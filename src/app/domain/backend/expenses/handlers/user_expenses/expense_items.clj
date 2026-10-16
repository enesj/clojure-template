(ns app.domain.backend.expenses.handlers.user-expenses.expense-items
  "User-facing (non-admin) expense items list.

  This endpoint is intended as a power-user tool for inspecting line items.
  It is role-gated to admin/owner and scoped to the current user's expenses."
  (:require
    [app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
    [app.domain.backend.expenses.services.article-aliases :as aliases]
    [app.shared.query-builders :as shared-qb]
    [app.template.backend.security.privacy-subject :as privacy-subject]
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant LocalDate OffsetDateTime]))

(def ^:private power-user-roles
  "Roles allowed to access the expense items power page."
  #{"admin" "owner"})

(defn- expense-owner-clause
  [user-id]
  (privacy-subject/user-match-clause :e.subject_ref user-id))

(def ^:private allowed-expense-items-order-by
  "Allowlisted sort keys for `list-expense-items-handler`.

  Keys are app keywords (kebab-case). Values are HoneySQL identifiers."
  {:created-at :ei.created_at
   :expense-id :ei.expense_id
   :alias-id :ei.alias_id
   :raw-label :aa.raw_label
   :article-canonical-name :a.canonical_name
   :expense-purchased-at :e.purchased_at
   :qty :ei.qty
   :unit :ei.unit
   :unit-price :ei.unit_price
   :line-total :ei.line_total})

(defn- blank->nil
  [v]
  (cond
    (nil? v) nil
    (string? v) (let [s (str/trim v)]
                  (when-not (str/blank? s) s))
    :else v))

(defn- parse-decimal!
  [field v]
  (let [v (blank->nil v)]
    (cond
      (nil? v) nil
      (instance? java.math.BigDecimal v) v
      (number? v) (bigdec v)
      (string? v)
      (try
        (bigdec v)
        (catch Exception _
          (throw (ex-info (str "Invalid " (name field))
                   {:status 400
                    :field field
                    :value v}))))
      :else
      (throw (ex-info (str "Invalid " (name field))
               {:status 400
                :field field
                :value v})))))

(defn- parse-unit
  [v]
  (some-> v blank->nil str str/trim str/lower-case not-empty))

(defn- clamp-limit
  [n]
  (-> (long (or n 200))
    (max 1)
    (min 500)))

(def ^:private expense-item-text-filter-columns
  {:raw-label :aa.raw_label
   :raw-label-normalized :aa.raw_label_normalized
   :article-canonical-name :a.canonical_name})

(defn- parse-instant-param
  "Lenient parse of a date/time string into java.time.Instant.
   Accepts ISO 8601 instants, offset date-times, or plain dates.
   Returns nil for nil, blank, or unparseable input."
  [raw]
  (when-not (str/blank? (str (or raw "")))
    (or (when (instance? Instant raw) raw)
      (try
        (Instant/parse (str raw))
        (catch Exception _ nil))
      (try
        (-> (OffsetDateTime/parse (str raw)) .toInstant)
        (catch Exception _ nil))
      (try
        (-> (LocalDate/parse (str raw))
          (.atStartOfDay java.time.ZoneOffset/UTC)
          .toInstant)
        (catch Exception _ nil)))))

(defn list-expense-items-handler
  "GET /api/v1/expenses/expense-items

  Returns expense_items joined with basic expense/supplier/payer/article context,
  scoped to the current user's expenses and tenant.

  Allowed roles: admin/owner.

  Supports order-by/order-dir for server-side sorting.
  Optional expense_id param to fetch items for a specific expense."
  [db]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request power-user-roles
                           "Admin or owner role required")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)
              elevated? (h/tenant-elevated? request)]
          (try
            (let [params (:query-params request)
                  limit (clamp-limit (some-> (h/get-param params :limit) parse-long))
                  offset (max 0 (long (or (some-> (h/get-param params :offset) parse-long) 0)))
                  search (some-> (h/get-param params :search) str)
                  expense-id (some-> (h/get-param params :expense_id) h/try-parse-uuid)
                  raw-label (some-> (h/get-param params :raw-label) str str/trim not-empty)
                  raw-label-normalized (some-> (h/get-param params :raw-label-normalized) str str/trim not-empty)
                  article-canonical-name (some-> (h/get-param params :article-canonical-name) str str/trim not-empty)
                  unit (parse-unit (h/get-param params :unit))
                  qty-min (parse-decimal! :qty-min (h/get-param params :qty-min))
                  qty-max (parse-decimal! :qty-max (h/get-param params :qty-max))
                  unit-price-min (parse-decimal! :unit-price-min (h/get-param params :unit-price-min))
                  unit-price-max (parse-decimal! :unit-price-max (h/get-param params :unit-price-max))
                  line-total-min (parse-decimal! :line-total-min (h/get-param params :line-total-min))
                  line-total-max (parse-decimal! :line-total-max (h/get-param params :line-total-max))
                  expense-purchased-at-from (parse-instant-param (h/get-param params :expense-purchased-at-from))
                  expense-purchased-at-to (parse-instant-param (h/get-param params :expense-purchased-at-to))
                  created-at-from (parse-instant-param (h/get-param params :created-at-from))
                  created-at-to (parse-instant-param (h/get-param params :created-at-to))
                  sort-opts (h/parse-sort-params params)
                  order-clauses (shared-qb/resolve-order-by-clauses
                                  {:sorts (:sorts sort-opts)
                                   :order-by (:order-by sort-opts)
                                   :order-dir (:order-dir sort-opts)
                                   :allowed-order-by allowed-expense-items-order-by
                                   :default-order-by :created-at
                                   :default-order-dir :desc
                                   :tie-breaker [:ei.id :asc]})
                  text-filters {:raw-label raw-label
                                :raw-label-normalized raw-label-normalized
                                :article-canonical-name article-canonical-name}
                  search* (when (and (string? search) (not (str/blank? search)))
                            (str "%" search "%"))
                  ;; Elevated users (admin/owner) see all tenant items; others see only their own.
                  effective-user-id (when-not elevated? user-id)
                  where (cond-> [:and]
                          (some? effective-user-id) (conj (expense-owner-clause effective-user-id))
                          tenant-id (conj [:= :e.tenant_id tenant-id])
                          expense-id (conj [:= :ei.expense_id expense-id])
                          unit (conj [:= :ei.unit unit])
                          (some? qty-min) (conj [:>= :ei.qty qty-min])
                          (some? qty-max) (conj [:<= :ei.qty qty-max])
                          (some? unit-price-min) (conj [:>= :ei.unit_price unit-price-min])
                          (some? unit-price-max) (conj [:<= :ei.unit_price unit-price-max])
                          (some? line-total-min) (conj [:>= :ei.line_total line-total-min])
                          (some? line-total-max) (conj [:<= :ei.line_total line-total-max])
                          expense-purchased-at-from (conj [:>= :e.purchased_at expense-purchased-at-from])
                          expense-purchased-at-to (conj [:<= :e.purchased_at expense-purchased-at-to])
                          created-at-from (conj [:>= :ei.created_at created-at-from])
                          created-at-to (conj [:<= :ei.created_at created-at-to])
                          search*
                          (conj [:or
                                 [:ilike :aa.raw_label search*]
                                 [:ilike :a.canonical_name search*]]))
                  base-query {:select [[:ei.*]
                                       [:aa.raw_label :raw_label]
                                       [:aa.raw_label_normalized :raw_label_normalized]
                                       [:e.purchased_at :expense_purchased_at]
                                       [:a.canonical_name :article_canonical_name]]
                              :from [[:expense_items :ei]]
                              :left-join [[:expenses :e] [:= :e.id :ei.expense_id]
                                          [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                          [:articles :a] [:= :a.id :aa.article_id]]
                              :where where}
                  query (-> base-query
                          (shared-qb/apply-text-filters expense-item-text-filter-columns text-filters)
                          (assoc :order-by order-clauses
                            :limit limit
                            :offset offset))
                  count-query (-> {:select [[[:count :ei.id] :total]]
                                   :from [[:expense_items :ei]]
                                   :left-join [[:expenses :e] [:= :e.id :ei.expense_id]
                                               [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                               [:articles :a] [:= :a.id :aa.article_id]]
                                   :where where}
                                (shared-qb/apply-text-filters expense-item-text-filter-columns text-filters))
                  items (jdbc/execute! db (sql/format query)
                          {:builder-fn rs/as-unqualified-lower-maps})
                  total (or (:total (jdbc/execute-one! db (sql/format count-query)
                                      {:builder-fn rs/as-unqualified-lower-maps}))
                          0)]
              (h/json-response {:data (vec items)
                                :total (long total)
                                :limit limit
                                :offset offset}))
            (catch Exception e
              (log/error e "Error listing expense items" {:user-id user-id})
              (h/json-response {:error "Failed to list expense items"} 500)))))
      (h/unauthorized-response))))

(defn- expense-item-id
  [request]
  (or (h/try-parse-uuid (get-in request [:path-params :id]))
    (h/try-parse-uuid (get-in request [:parameters :path :id]))))

(defn- update-expense-item!
  [db user-id item-id updates & {:keys [tenant-id]}]
  (jdbc/execute-one! db
    (sql/format
      {:update [:expense_items :ei]
       :set updates
       :from [[:expenses :e]]
       :where (cond-> [:and
                       [:= :ei.id item-id]
                       [:= :e.id :ei.expense_id]
                       (expense-owner-clause user-id)]
                tenant-id (conj [:= :e.tenant_id tenant-id]))
       :returning [:ei.*]})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn- fetch-expense-item
  "Fetch a single expense item with alias + article joins."
  [db user-id item-id & {:keys [tenant-id]}]
  (jdbc/execute-one!
    db
    (sql/format
      {:select [[:ei.*]
                [:aa.raw_label :raw_label]
                [:aa.raw_label_normalized :raw_label_normalized]
                [:e.purchased_at :expense_purchased_at]
                [:a.canonical_name :article_canonical_name]]
       :from [[:expense_items :ei]]
       :left-join [[:expenses :e] [:= :e.id :ei.expense_id]
                   [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                   [:articles :a] [:= :a.id :aa.article_id]]
       :where (cond-> [:and
                       [:= :ei.id item-id]
                       (expense-owner-clause user-id)]
                tenant-id (conj [:= :e.tenant_id tenant-id]))
       :limit 1})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn update-expense-item-handler
  "PUT /api/v1/expenses/expense-items/:id

  Updates an expense item (line item) scoped to the current user's expenses.

  Allowed roles: admin/owner."
  [db]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request power-user-roles
                           "Only admins and owners can modify expense items")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)]
          (if-let [item-id (expense-item-id request)]
            (try
              (let [body (h/read-body-params request)
                    raw-label (some-> (h/get-param body :raw_label) str str/trim)
                    raw-label* (when-not (str/blank? raw-label) raw-label)
                    expense-row (jdbc/execute-one! db
                                  (sql/format {:select [:e.supplier_id :ei.unit]
                                               :from [[:expense_items :ei]]
                                               :join [[:expenses :e] [:= :e.id :ei.expense_id]]
                                               :where (cond-> [:and [:= :ei.id item-id]]
                                                        tenant-id (conj [:= :e.tenant_id tenant-id]))})
                                  {:builder-fn rs/as-unqualified-lower-maps})
                    supplier-id (:supplier_id expense-row)
                    unit (or (parse-unit (h/get-param body :unit))
                           (:unit expense-row))
                    alias-id (when raw-label*
                               (:id (aliases/find-or-create-alias! db supplier-id raw-label* unit {:allow-noise? true})))
                    updates {:alias_id (or alias-id
                                         (throw (ex-info "raw_label is required"
                                                  {:status 400
                                                   :field :raw_label})))
                             :qty (parse-decimal! :qty (h/get-param body :qty))
                             :unit unit
                             :unit_price (parse-decimal! :unit_price (h/get-param body :unit_price))
                             :line_total (or (parse-decimal! :line_total (h/get-param body :line_total))
                                           (throw (ex-info "line_total is required"
                                                    {:status 400
                                                     :field :line_total})))}]
                (if-let [updated (update-expense-item! db user-id item-id updates :tenant-id tenant-id)]
                  (h/json-response {:data (or (fetch-expense-item db user-id item-id :tenant-id tenant-id) updated)})
                  (h/not-found-response "Expense item not found or access denied")))
              (catch clojure.lang.ExceptionInfo e
                (let [{:keys [status]} (ex-data e)]
                  (if (number? status)
                    (h/json-response {:error (.getMessage e)} status)
                    (do
                      (log/error e "Error updating expense item (ex-info)" {:user-id user-id :item-id item-id})
                      (h/json-response {:error "Failed to update expense item"} 500)))))
              (catch Exception e
                (log/error e "Error updating expense item" {:user-id user-id :item-id item-id})
                (h/json-response {:error "Failed to update expense item"} 500)))
            (h/json-response {:error "Invalid expense item ID"} 400))))
      (h/unauthorized-response))))

(defn- delete-expense-item!
  [db user-id item-id & {:keys [tenant-id]}]
  (jdbc/execute-one! db
    (sql/format
      {:delete-from :expense_items
       :where [:and
               [:= :id item-id]
               [:in :expense_id {:select [:id]
                                 :from [:expenses]
                                 :where (cond-> [:and
                                                 (privacy-subject/user-match-clause :subject_ref user-id)]
                                          tenant-id (conj [:= :tenant_id tenant-id]))}]]
       :returning [:*]})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn batch-delete-expense-items-handler
  "Batch delete expense items scoped to the current user's expenses and tenant.

  Allowed roles: admin/owner.

  Expects JSON body like:
  {:ids [<uuid> ...]}

  Returns:
  {:data {:deleted-count n :deleted-ids [...] :errors [...]}}"
  [db]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request power-user-roles
                           "Only admins and owners can modify expense items")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)]
          (try
            (let [body (h/read-body-params request)
                  raw-ids (or (:ids body)
                            (:expense_item_ids body)
                            (:expense-item-ids body)
                            (:expenseItemIds body)
                            [])
                  ids (->> raw-ids (map h/try-parse-uuid) (filter some?) vec)]
              (cond
                (empty? raw-ids)
                (h/json-response {:error "No expense item ids provided"} 400)

                (empty? ids)
                (h/json-response {:error "One or more expense item ids are invalid"} 400)

                :else
                (let [deleted-ids (atom [])
                      errors (atom [])]
                  (doseq [item-id ids]
                    (try
                      (if (some? (delete-expense-item! db user-id item-id :tenant-id tenant-id))
                        (swap! deleted-ids conj (str item-id))
                        (swap! errors conj {:id (str item-id)
                                            :error "not found"}))
                      (catch Exception e
                        (swap! errors conj {:id (str item-id)
                                            :error (.getMessage e)}))))
                  (h/json-response {:data {:deleted-count (count @deleted-ids)
                                           :deleted-ids (vec @deleted-ids)
                                           :errors (vec @errors)}}))))
            (catch Exception e
              (log/error e "Error batch deleting expense items" {:user-id user-id})
              (h/json-response {:error "Failed to delete expense items"} 500)))))
      (h/unauthorized-response))))
