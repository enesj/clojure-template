(ns app.domain.backend.expenses.services.user-expenses
  "User-facing expense services with privacy subject filtering.
   These services enforce that users can only access their own expenses."
  (:require
    [app.domain.backend.expenses.services.exchange-rates :as exchange-rates]
    [app.domain.backend.expenses.services.expenses :as admin-expenses]
    [app.template.backend.security.privacy-subject :as privacy-subject]
    [app.shared.model-naming :as model-naming]
    [app.shared.query-builders :as shared-qb]
    [app.shared.type-conversion :as type-conv]
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant LocalDate OffsetDateTime]
    [java.util UUID]))

;; ============================================================================
;; User Expense CRUD with subject-ref filtering
;; ============================================================================

(defn- ensure-uuid
  "Coerce a UUID or UUID string into a UUID instance. Returns nil if input is nil.
   Throws IllegalArgumentException for invalid UUID strings to surface earlier."
  [v]
  (cond
    (nil? v) nil
    (instance? UUID v) v
    :else (UUID/fromString (str v))))

(defn- user-ownership-clause
  "Return the subject-ref ownership predicate for user-scoped reads."
  [subject-column user-id]
  (privacy-subject/user-match-clause subject-column user-id))

(defn- parse-instant-param
  "Lenient parse of a date/time string into java.time.Instant.
   Accepts ISO 8601 instants, offset date-times, or plain dates.
   Returns nil for nil, blank, or unparseable input."
  [raw]
  (when-not (clojure.string/blank? (str (or raw "")))
    (or (when (instance? Instant raw) raw)
      (try (Instant/parse (str raw)) (catch Exception _ nil))
      (try (-> (OffsetDateTime/parse (str raw)) .toInstant) (catch Exception _ nil))
      (try (-> (LocalDate/parse (str raw)) (.atStartOfDay java.time.ZoneOffset/UTC) .toInstant) (catch Exception _ nil)))))

(defn create-user-expense!
  "Create an expense for a specific user. Returns expense with :items.

   `tenant-id` scopes the expense to a specific tenant.

   For non-BAM currencies, this primes the daily exchange-rate cache (when app
   config is available) and lets the shared expense service persist normalized
   monetary fields for every write path. New rows store a secret-derived
   subject ref instead of a direct users.id owner link."
  ([db tenant-id user-id expense-data items]
   (create-user-expense! db tenant-id user-id expense-data items nil))
  ([db tenant-id user-id expense-data items app-config]
   (let [user-id (ensure-uuid user-id)
         tenant-id (ensure-uuid tenant-id)
         subject-ref (privacy-subject/user-subject-ref user-id)
         currency (or (:currency expense-data) "BAM")
         total-amount (some-> (:total_amount expense-data) bigdec)]
     (when-not user-id
       (throw (ex-info "user-id is required" {:data expense-data})))
     (log/debug "Creating expense for user" {:user-id user-id :tenant-id tenant-id :currency currency})
     (when (and total-amount (not= currency "BAM") app-config)
       (try
         (exchange-rates/ensure-daily-rates!
           db
           (exchange-rates/build-config app-config))
         (catch Exception e
           (log/warn e "Failed to ensure daily rates" {:currency currency}))))
     (let [expense-data (cond-> (assoc expense-data
                                  :subject_ref subject-ref
                                  :created_by_subject_ref subject-ref
                                  :currency currency)
                          tenant-id (assoc :tenant_id tenant-id))]
       (admin-expenses/create-expense! db expense-data items)))))

(defn update-user-expense!
  "Update expense fields for a user's own expense. Returns updated expense or nil if not found/unauthorized.
   `tenant-id` scopes the ownership check and update to a specific tenant."
  [db tenant-id user-id expense-id updates]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)]
    (when-not user-id
      (throw (ex-info "user-id is required" {:expense-id expense-id})))
    ;; First verify subject-ref ownership (scoped by tenant when present).
    (let [ownership-where (cond-> [:and
                                   [:= :id expense-id]
                                   (user-ownership-clause :subject_ref user-id)]
                            tenant-id (conj [:= :tenant_id tenant-id]))
          existing (jdbc/execute-one!
                     db
                     (sql/format {:select [:id :subject_ref]
                                  :from [:expenses]
                                  :where ownership-where})
                     {:builder-fn rs/as-unqualified-lower-maps})]
      (when existing
        (admin-expenses/update-expense! db expense-id updates tenant-id)))))

(defn delete-user-expenses!
  "Hard delete multiple expenses owned by a user.

  Returns a result map:
  - :deleted-count
  - :deleted-ids
  - :not-found-ids (includes ids not owned by the user or missing)

  `tenant-id` scopes the ownership check and delete to a specific tenant."
  [db tenant-id user-id expense-ids]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)
        ids (->> (or expense-ids [])
              (map ensure-uuid)
              (remove nil?)
              vec)]
    (when-not user-id
      (throw (ex-info "user-id is required" {:expense-ids expense-ids})))
    (if (empty? ids)
      {:deleted-count 0
       :deleted-ids []
       :not-found-ids []}
      (jdbc/with-transaction [tx db]
        ;; Only delete expenses that are owned by the user (scoped by tenant).
        (let [ownership-where (cond-> [:and
                                       (user-ownership-clause :subject_ref user-id)
                                       [:in :id ids]]
                                tenant-id (conj [:= :tenant_id tenant-id]))
              owned-rows (jdbc/execute!
                           tx
                           (sql/format {:select [:id]
                                        :from [:expenses]
                                        :where ownership-where})
                           {:builder-fn rs/as-unqualified-lower-maps})
              owned-ids (mapv :id owned-rows)
              deleted-ids (reduce
                            (fn [acc expense-id]
                              (if-let [deleted (admin-expenses/delete-expense! tx expense-id tenant-id)]
                                (conj acc (:id deleted))
                                acc))
                            []
                            owned-ids)
              deleted-set (set deleted-ids)
              not-found-ids (->> ids
                              (remove deleted-set)
                              vec)]
          {:deleted-count (count deleted-ids)
           :deleted-ids deleted-ids
           :not-found-ids not-found-ids})))))

(def ^:private batch-update-allowed-keys
  #{:supplier_id
    :payer_id
    :expense_category_id
    :expense_context_id
    :purchased_at
    :total_amount
    :currency
    :notes
    :receipt_id})

(defn- normalize-batch-update-updates
  "Normalize a batch-update item into DB-oriented expense update keys.

   Accepts either app/runtime kebab-case keys (for example `:payer-id`) or
   database snake_case keys (for example `:payer_id`), removes non-updatable
   bookkeeping fields, and returns only the supported update columns."
  [item]
  (-> item
    model-naming/app-map-keys->db
    (dissoc :id :created_at :updated_at)
    (select-keys batch-update-allowed-keys)))

(defn batch-update-user-expenses!
  "Batch update multiple expenses owned by a user.

  `items` is a collection of maps, each containing:
  - :id (UUID or UUID string)
  - any updatable expense columns using snake_case or kebab-case keys

  Only the following fields are applied after normalization:
  :supplier_id :payer_id :expense_category_id :expense_context_id :purchased_at :total_amount :currency :notes :receipt_id

  `tenant-id` scopes the ownership checks and updates to a specific tenant.

  Returns:
  {:updated n
   :results [<updated-expense> ...]
   :errors  [{:id <uuid-or-raw> :error <msg>} ...]}"
  [db tenant-id user-id items]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)
        items (vec (or items []))
        try-uuid type-conv/try-parse-uuid]
    (when-not user-id
      (throw (ex-info "user-id is required" {:items-count (count items)})))
    (if (empty? items)
      {:updated 0
       :results []
       :errors []}
      (jdbc/with-transaction [tx db]
        (let [{:keys [results errors]}
              (reduce
                (fn [acc item]
                  (let [expense-id (try-uuid (:id item))
                        updates (normalize-batch-update-updates item)]
                    (cond
                      (nil? expense-id)
                      (update acc :errors conj {:id (:id item) :error "Invalid expense id"})

                      (empty? updates)
                      (update acc :errors conj {:id expense-id :error "No updatable fields provided"})

                      :else
                      (try
                        (if-let [expense (update-user-expense! tx tenant-id user-id expense-id updates)]
                          (update acc :results conj expense)
                          (update acc :errors conj {:id expense-id :error "Expense not found or access denied"}))
                        (catch Exception e
                          (log/error e "Batch update failed for expense" {:expense-id expense-id :user-id user-id})
                          (update acc :errors conj {:id expense-id :error "Failed to update expense"}))))))
                {:results [] :errors []}
                items)]
          {:updated (count results)
           :results results
           :errors errors})))))

(defn get-user-expense-with-items
  "Get an expense with items. When user-id is non-nil, restricts to that user's expenses.
   `tenant-id` scopes the query to a specific tenant."
  [db tenant-id user-id expense-id]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)
        where (cond-> [:and
                       [:= :e.id expense-id]]
                user-id (conj (user-ownership-clause :e.subject_ref user-id))
                tenant-id (conj [:= :e.tenant_id tenant-id]))
        expense (jdbc/execute-one!
                  db
                  (sql/format {:select [[:e.*]
                                        [:s.display_name :supplier_display_name]
                                        [:s.normalized_key :supplier_normalized_key]
                                        [:p.label :payer_label]
                                        [:p.type :payer_type]
                                         [:ec.name :expense_category_name]
                                         [:ctx.name :expense_context_name]]
                               :from [[:expenses :e]]
                               :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]
                                           [:payers :p] [:= :p.id :e.payer_id]
                                           [:expense_categories :ec] [:= :ec.id :e.expense_category_id]
                                           [:expense_contexts :ctx] [:= :ctx.id :e.expense_context_id]]
                               :where where})
                  {:builder-fn rs/as-unqualified-lower-maps})
        items (when expense
                (jdbc/execute!
                  db
                  (sql/format {:select [[:ei.*]
                                        [:aa.raw_label :raw_label]
                                        [:aa.raw_label_normalized :raw_label_normalized]
                                        [:a.canonical_name :article_canonical_name]]
                               :from [[:expense_items :ei]]
                               :left-join [[:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                           [:articles :a] [:= :a.id :aa.article_id]]
                               :where [:= :ei.expense_id expense-id]
                               :order-by [[:ei.created_at :asc]]})
                  {:builder-fn rs/as-unqualified-lower-maps}))]
    (when expense
      (assoc expense :items items))))

(def ^:private expense-text-filter-columns
  "Mapping from text filter keys to SQL column identifiers for expenses."
  {:supplier-display-name  :s.display_name
   :store-display-name     :st.display_name
   :expense-category-name  :ec.name
    :expense-context-name   :ctx.name
   :payer-label            :p.label
   :notes                  :e.notes})

(def ^:private allowed-user-expenses-order-by
  "Allowlisted sort keys for `list-user-expenses`.

  Keys are app keywords (kebab-case). Values are HoneySQL identifiers."
  {:purchased-at :e.purchased_at
   :expense-date :e.purchased_at
   :created-at :e.created_at
   :updated-at :e.updated_at
   :total-amount :e.total_amount
   :currency :e.currency
   :supplier-display-name :s.display_name
   :store-display-name :st.display_name
   :payer-label :p.label
   :payer-type :pt.label
  :expense-category-name :ec.name
  :expense-context-name :ctx.name
  :item-count :item_count})

(defn- parse-decimal-param
  [raw]
  (cond
    (nil? raw) nil
    (instance? java.math.BigDecimal raw) raw
    (number? raw) (bigdec raw)
    :else
    (let [value (some-> raw str str/trim not-empty)]
      (when value
        (try
          (bigdec value)
          (catch Exception _
            nil))))))

(defn- normalize-currency-filter
  [currency]
  (let [normalize-one (fn [value]
                        (cond
                          (nil? value) nil
                          (keyword? value) (some-> value name str/trim not-empty str/upper-case)
                          :else (some-> value str str/trim not-empty str/upper-case)))
        values (cond
                 (nil? currency) []
                 (and (sequential? currency) (not (string? currency))) currency
                 :else [currency])]
    (->> values
      (map normalize-one)
      (remove nil?)
      distinct
      vec)))

(defn- text-cast-column
  [column]
  (if (keyword? column)
    (let [ns-part (namespace column)
          n (name column)]
      [:raw (str (when ns-part (str ns-part ".")) n "::text")])
    column))

(defn- apply-currency-filter
  [where column currency-values]
  (let [column (text-cast-column column)]
    (cond
      (empty? currency-values) where
      (= 1 (count currency-values)) (conj where [:= column (first currency-values)])
      :else (conj where [:in column currency-values]))))

(defn- apply-expense-context-filter
  [where column expense-context-id expense-context-missing?]
  (cond
    (true? expense-context-missing?) (conj where [:is column nil])
    expense-context-id (conj where [:= column expense-context-id])
    :else where))

(defn list-user-expenses
  "List expenses for a specific user with common filters.

  opts:
  - :from, :to
  - :supplier-id, :payer-id
  - :supplier-display-name, :store-display-name, :expense-category-name,
    :payer-label, :notes  (text ILIKE filters)
  - :currency (exact match, accepts one or many values)
  - :total-amount-min, :total-amount-max
  - :limit, :offset
  - :order-by (app keyword)
  - :order-dir (:asc/:desc)

  `tenant-id` scopes the query to a specific tenant."
  [db tenant-id user-id {:keys [from to created-at-from created-at-to
                                supplier-id payer-id expense-context-id
                                expense-context-missing?
                                supplier-display-name store-display-name
                                expense-category-name expense-context-name
                                payer-label currency notes
                                total-amount-min total-amount-max source
                                limit offset sorts order-by order-dir]
                         :or {limit 50 offset 0 order-dir :desc}}]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)
        from (parse-instant-param from)
        to (parse-instant-param to)
        created-at-from (parse-instant-param created-at-from)
        created-at-to (parse-instant-param created-at-to)
        total-amount-min (parse-decimal-param total-amount-min)
        total-amount-max (parse-decimal-param total-amount-max)
        currency-values (normalize-currency-filter currency)
        source-where (case (some-> source str)
                       "manual" [:is :e.receipt_id nil]
                       "receipt" [:is-not :e.receipt_id nil]
                       "none" [:= 1 0]
                       nil)
        order-clauses (shared-qb/resolve-order-by-clauses
                        {:sorts sorts
                         :order-by (some-> order-by model-naming/ensure-app-keyword)
                         :order-dir order-dir
                         :allowed-order-by allowed-user-expenses-order-by
                         :default-order-by :expense-date
                         :default-order-dir :desc
                         :tie-breaker [:e.id :asc]})
        base-where (cond-> [:and]
                     user-id (conj (user-ownership-clause :e.subject_ref user-id))
                     tenant-id (conj [:= :e.tenant_id tenant-id])
                     from (conj [:>= :e.purchased_at from])
                     to (conj [:<= :e.purchased_at to])
                     created-at-from (conj [:>= :e.created_at created-at-from])
                     created-at-to (conj [:<= :e.created_at created-at-to])
                     supplier-id (conj [:= :e.supplier_id supplier-id])
                     payer-id (conj [:= :e.payer_id payer-id])
                     (some? total-amount-min) (conj [:>= :e.total_amount total-amount-min])
                     (some? total-amount-max) (conj [:<= :e.total_amount total-amount-max])
                     source-where (conj source-where))
                  base-where (apply-expense-context-filter
                         base-where
                         :e.expense_context_id
                         expense-context-id
                         expense-context-missing?)
        filtered-base-where (apply-currency-filter base-where :e.currency currency-values)
        query (-> {:select [[:e.*]
                            [:s.display_name :supplier_display_name]
                            [:st.display_name :store_display_name]
                            [:s.normalized_key :supplier_normalized_key]
                            [:p.label :payer_label]
                            [:p.type :payer_type]
                            [:ec.name :expense_category_name]
                            [:ctx.name :expense_context_name]
                            [{:select [[[:count :ei.id]]]
                              :from [[:expense_items :ei]]
                              :where [:= :ei.expense_id :e.id]} :item_count]]
                   :from [[:expenses :e]]
                   :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]
                               [:stores :st] [:= :st.id :e.store_id]
                               [:payers :p] [:= :p.id :e.payer_id]
                               [:expense_categories :ec] [:= :ec.id :e.expense_category_id]
                               [:expense_contexts :ctx] [:= :ctx.id :e.expense_context_id]]
                   :where filtered-base-where
                   :order-by order-clauses
                   :limit limit
                   :offset offset}
                (shared-qb/apply-text-filters expense-text-filter-columns
                  {:supplier-display-name supplier-display-name
                   :store-display-name store-display-name
                   :expense-category-name expense-category-name
                   :expense-context-name expense-context-name
                   :payer-label payer-label
                   :notes notes}))]
    (jdbc/execute! db (sql/format query) {:builder-fn rs/as-unqualified-lower-maps})))

(defn count-user-expenses
  "Count total expenses for a specific user with optional filters.
   When user-id is nil, counts all tenant expenses.
   `tenant-id` scopes the count to a specific tenant.
   Text filters (supplier-display-name, etc.) require LEFT JOINs."
  [db tenant-id user-id {:keys [from to created-at-from created-at-to
                                supplier-id payer-id expense-context-id
                                expense-context-missing?
                                supplier-display-name store-display-name
                                expense-category-name expense-context-name
                                payer-label currency notes
                                total-amount-min total-amount-max source]}]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)
        from (parse-instant-param from)
        to (parse-instant-param to)
        created-at-from (parse-instant-param created-at-from)
        created-at-to (parse-instant-param created-at-to)
        total-amount-min (parse-decimal-param total-amount-min)
        total-amount-max (parse-decimal-param total-amount-max)
        currency-values (normalize-currency-filter currency)
        text-filters {:supplier-display-name supplier-display-name
                      :store-display-name store-display-name
                      :expense-category-name expense-category-name
                      :expense-context-name expense-context-name
                      :payer-label payer-label
                      :notes notes}
        has-text-filters? (shared-qb/has-text-filters?
                            (keys expense-text-filter-columns) text-filters)
        source-col (if has-text-filters? :e.receipt_id :receipt_id)
        source-where (case (some-> source str)
                       "manual" [:is source-col nil]
                       "receipt" [:is-not source-col nil]
                       "none" [:= 1 0]
                       nil)
        user-subject-column (if has-text-filters? :e.subject_ref :subject_ref)
        base-where (cond-> [:and]
                     user-id (conj (user-ownership-clause user-subject-column user-id))
                     tenant-id (conj [:= (if has-text-filters? :e.tenant_id :tenant_id) tenant-id])
                     from (conj [:>= (if has-text-filters? :e.purchased_at :purchased_at) from])
                     to (conj [:<= (if has-text-filters? :e.purchased_at :purchased_at) to])
                     created-at-from (conj [:>= (if has-text-filters? :e.created_at :created_at) created-at-from])
                     created-at-to (conj [:<= (if has-text-filters? :e.created_at :created_at) created-at-to])
                     supplier-id (conj [:= (if has-text-filters? :e.supplier_id :supplier_id) supplier-id])
                     payer-id (conj [:= (if has-text-filters? :e.payer_id :payer_id) payer-id])
                     (some? total-amount-min) (conj [:>= (if has-text-filters? :e.total_amount :total_amount) total-amount-min])
                     (some? total-amount-max) (conj [:<= (if has-text-filters? :e.total_amount :total_amount) total-amount-max])
                     source-where (conj source-where))
                  base-where (apply-expense-context-filter
                         base-where
                         (if has-text-filters? :e.expense_context_id :expense_context_id)
                         expense-context-id
                         expense-context-missing?)
        filtered-base-where (apply-currency-filter base-where (if has-text-filters? :e.currency :currency) currency-values)
        query (if has-text-filters?
                (-> {:select [[[:count :*] :total]]
                     :from [[:expenses :e]]
                     :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]
                                 [:stores :st] [:= :st.id :e.store_id]
                                 [:payers :p] [:= :p.id :e.payer_id]
                                 [:expense_categories :ec] [:= :ec.id :e.expense_category_id]
                                 [:expense_contexts :ctx] [:= :ctx.id :e.expense_context_id]]
                     :where filtered-base-where}
                  (shared-qb/apply-text-filters expense-text-filter-columns text-filters))
                {:select [[[:count :*] :total]]
                 :from [:expenses]
                 :where filtered-base-where})]
    (:total (jdbc/execute-one! db (sql/format query)
              {:builder-fn rs/as-unqualified-lower-maps}))))

(def highlight-date-columns
  "Date columns available for highlight queries.
   Keys are app keywords from the frontend; values are SQL column refs."
  {:purchased-at :e.purchased_at
   :created-at :e.created_at
   :updated-at :e.updated_at})

(defn- safe-timezone-id
  "Validate and return a canonical IANA timezone ID, or nil."
  [timezone]
  (when-not (clojure.string/blank? (str (or timezone "")))
    (try
      (.getId (java.time.ZoneId/of (str timezone)))
      (catch Exception _ nil))))

(defn highlight-user-expense-days
  "Return distinct local-date strings for a given timestamp column while
   respecting the same non-highlight filters as list/count."
  [db tenant-id user-id {:keys [from to created-at-from created-at-to
                                supplier-id payer-id expense-context-id
                                expense-context-missing?
                                supplier-display-name store-display-name
                                expense-category-name expense-context-name
                                payer-label currency notes
                                total-amount-min total-amount-max
                                highlight-column highlight-timezone
                                highlight-field]}]
  (let [tz-id (safe-timezone-id highlight-timezone)
        sql-col (when (keyword? highlight-column)
                  (let [ns-part (namespace highlight-column)
                        n (name highlight-column)]
                    (if ns-part (str ns-part "." n) n)))]
    (when (and tz-id (seq sql-col))
      (let [user-id (ensure-uuid user-id)
            tenant-id (ensure-uuid tenant-id)
            exclude-purchased? (= highlight-field :purchased-at)
            exclude-created? (= highlight-field :created-at)
            from (when-not exclude-purchased? (parse-instant-param from))
            to (when-not exclude-purchased? (parse-instant-param to))
            created-at-from (when-not exclude-created? (parse-instant-param created-at-from))
            created-at-to (when-not exclude-created? (parse-instant-param created-at-to))
            total-amount-min (parse-decimal-param total-amount-min)
            total-amount-max (parse-decimal-param total-amount-max)
            expense-context-id (some-> expense-context-id ensure-uuid)
            currency-values (normalize-currency-filter currency)
            day-expr (str "to_char(timezone('"
                       (clojure.string/replace tz-id "'" "''")
                       "', " sql-col ")::date, 'YYYY-MM-DD')")
            base-where (cond-> [:and]
                         user-id (conj (user-ownership-clause :e.subject_ref user-id))
                         tenant-id (conj [:= :e.tenant_id tenant-id])
                         from (conj [:>= :e.purchased_at from])
                         to (conj [:<= :e.purchased_at to])
                         created-at-from (conj [:>= :e.created_at created-at-from])
                         created-at-to (conj [:<= :e.created_at created-at-to])
                         supplier-id (conj [:= :e.supplier_id (ensure-uuid supplier-id)])
                         payer-id (conj [:= :e.payer_id (ensure-uuid payer-id)])
                         (some? total-amount-min) (conj [:>= :e.total_amount total-amount-min])
                         (some? total-amount-max) (conj [:<= :e.total_amount total-amount-max]))
            base-where (apply-expense-context-filter
                         base-where
                         :e.expense_context_id
                         expense-context-id
                         expense-context-missing?)
            base-where (apply-currency-filter base-where :e.currency currency-values)
            query (-> {:select [[[:raw day-expr] :day]]
                       :from [[:expenses :e]]
                       :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]
                                   [:stores :st] [:= :st.id :e.store_id]
                                   [:payers :p] [:= :p.id :e.payer_id]
                                   [:expense_categories :ec] [:= :ec.id :e.expense_category_id]
                                   [:expense_contexts :ctx] [:= :ctx.id :e.expense_context_id]]
                       :where base-where
                       :group-by [[:raw day-expr]]
                       :order-by [[[:raw day-expr] :asc]]}
                    (shared-qb/apply-text-filters expense-text-filter-columns
                      {:supplier-display-name supplier-display-name
                       :store-display-name store-display-name
                       :expense-category-name expense-category-name
                       :expense-context-name expense-context-name
                       :payer-label payer-label
                       :notes notes}))]
        (->> (jdbc/execute! db (sql/format query)
               {:builder-fn rs/as-unqualified-lower-maps})
          (keep :day)
          vec)))))

;; ============================================================================
;; User Dashboard Aggregations
;; ============================================================================

(defn get-user-expense-summary
  "Get summary statistics for expenses.
   When user-id is nil, aggregates all tenant expenses.
   Returns: {:total-expenses N :total-amount M :currency-totals {...} :recent-count N}
   `tenant-id` scopes the summary to a specific tenant."
  [db tenant-id user-id {:keys [days-back from to supplier-id expense-category-id
                                expense-context-id expense-context-missing?]
                         :or {days-back 30}}]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)
        from* (parse-instant-param from)
        to* (parse-instant-param to)
        supplier-id* (some-> supplier-id ensure-uuid)
        expense-category-id* (some-> expense-category-id ensure-uuid)
        expense-context-id* (some-> expense-context-id ensure-uuid)
        report-summary? (or from* to* supplier-id* expense-category-id*
                           expense-context-id* expense-context-missing?)]
    (if report-summary?
      (let [summary-where (cond-> [:and]
                            user-id (conj (user-ownership-clause :subject_ref user-id))
                            tenant-id (conj [:= :tenant_id tenant-id])
                            from* (conj [:>= :purchased_at from*])
                            to* (conj [:<= :purchased_at to*])
                            supplier-id* (conj [:= :supplier_id supplier-id*])
                            expense-category-id* (conj [:= :expense_category_id expense-category-id*]))
            summary-where (apply-expense-context-filter
                            summary-where
                            :expense_context_id
                            expense-context-id*
                            expense-context-missing?)
            total-expenses (:total (jdbc/execute-one!
                                     db
                                     (sql/format {:select [[[:count :*] :total]]
                                                  :from [:expenses]
                                                  :where summary-where})
                                     {:builder-fn rs/as-unqualified-lower-maps}))
            currency-totals (jdbc/execute!
                              db
                              (sql/format {:select [:currency
                                                    [[:sum :total_amount] :total]]
                                           :from [:expenses]
                                           :where summary-where
                                           :group-by [:currency]})
                              {:builder-fn rs/as-unqualified-lower-maps})]
        {:total-expenses total-expenses
         :currency-totals (into {} (map (juxt :currency :total) currency-totals))
         :recent-count total-expenses
         :from from*
         :to to*})
      (let [total-expenses (count-user-expenses db tenant-id user-id {})
            currency-where (cond-> [:and]
                             user-id (conj (user-ownership-clause :subject_ref user-id))
                             tenant-id (conj [:= :tenant_id tenant-id]))
            currency-where (apply-expense-context-filter
                             currency-where
                             :expense_context_id
                             nil
                             false)
            currency-totals (jdbc/execute!
                              db
                              (sql/format {:select [:currency
                                                    [[:sum :total_amount] :total]]
                                           :from [:expenses]
                                           :where currency-where
                                           :group-by [:currency]})
                              {:builder-fn rs/as-unqualified-lower-maps})
            recent-date (java.time.Instant/now)
            recent-count (count-user-expenses db tenant-id user-id
                           {:from (.minus recent-date (java.time.Duration/ofDays days-back))})]
        {:total-expenses total-expenses
         :currency-totals (into {} (map (juxt :currency :total) currency-totals))
         :recent-count recent-count
         :days-back days-back}))))

(defn get-user-spending-by-month
  "Get monthly spending aggregation for a user.
   Returns list of {:month \"YYYY-MM\" :currency C :total N}
   `tenant-id` scopes the query to a specific tenant."
  [db tenant-id user-id {:keys [months-back expense-context-id expense-context-missing?]
                         :or {months-back 6}}]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)
        expense-context-id (some-> expense-context-id ensure-uuid)
        where (cond-> [:and
                       [:>= :purchased_at
                        [:raw (format "NOW() - INTERVAL '%d months'" months-back)]]]
                user-id (conj (user-ownership-clause :subject_ref user-id))
          tenant-id (conj [:= :tenant_id tenant-id]))
        where (apply-expense-context-filter where :expense_context_id expense-context-id expense-context-missing?)]
    (jdbc/execute!
      db
      (sql/format {:select [[[:to_char :purchased_at [:inline "YYYY-MM"]] :month]
                            :currency
                            [[:sum :total_amount] :total]]
                   :from [:expenses]
                   :where where
                   :group-by [[:to_char :purchased_at [:inline "YYYY-MM"]] :currency]
                   :order-by [[[:to_char :purchased_at [:inline "YYYY-MM"]] :desc]]})
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn get-user-spending-by-supplier
  "Get spending by supplier for a user.
   Returns list of {:supplier_id UUID :supplier_name S :total N :currency C}
   `tenant-id` scopes the query to a specific tenant."
  [db tenant-id user-id {:keys [from to limit expense-context-id expense-context-missing?]
                         :or {limit 10}}]
  (let [user-id (ensure-uuid user-id)
        tenant-id (ensure-uuid tenant-id)
        expense-context-id (some-> expense-context-id ensure-uuid)
        from (parse-instant-param from)
        to (parse-instant-param to)
        base-where (cond-> [:and
                            [:is-not :e.supplier_id nil]]
                     user-id (conj (user-ownership-clause :e.subject_ref user-id))
                     tenant-id (conj [:= :e.tenant_id tenant-id])
                     from (conj [:>= :e.purchased_at from])
                     to (conj [:<= :e.purchased_at to]))
        base-where (apply-expense-context-filter base-where :e.expense_context_id expense-context-id expense-context-missing?)]
    (jdbc/execute!
      db
      (sql/format {:select [:e.supplier_id
                            [:s.display_name :supplier_name]
                            :e.currency
                            [[:sum :e.total_amount] :total]
                            [[:count :*] :expense_count]]
                   :from [[:expenses :e]]
                   :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]]
                   :where base-where
                   :group-by [:e.supplier_id :s.display_name :e.currency]
                   :order-by [[[:sum :e.total_amount] :desc]]
                   :limit limit})
      {:builder-fn rs/as-unqualified-lower-maps})))
