(ns app.domain.backend.expenses.services.article-aliases
  "Article alias management (unified raw_label + article mapping)."
  (:require
    [app.domain.backend.expenses.services.article-resolution.noise :as noise]
    [app.domain.backend.expenses.services.articles :as articles]
    [app.domain.backend.expenses.services.related-records :as rr]
    [app.domain.backend.expenses.services.service-configs :as configs]
    [app.domain.backend.expenses.services.services-factory :as factory]
    [app.shared.model-naming :as model-naming]
    [app.shared.query-builders :as shared-qb]
    [app.shared.type-conversion :as type-conv]
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs])
  (:import
    [java.util UUID]))

;; ============================================================================
;; Constants
;; ============================================================================

(def ^:private unknown-supplier-normalized-key "unknown-supplier")

(def ^:private min-alias-normalized-length 2)

(defn- valid-alias-normalized-key?
  [normalized]
  (and (not (str/blank? normalized))
    (>= (count normalized) min-alias-normalized-length)))

;; ============================================================================
;; Service Registration
;; ============================================================================

(def config (configs/get-entity-config :article-alias))

;; ============================================================================
;; Generated CRUD Operations
;; ============================================================================

(def service (factory/build-entity-service config))

(def ^:private try-uuid type-conv/try-parse-uuid)

(def ^:private occurrence-count-expr [:count :ei.id])

(def ^:private allowed-unmapped-aliases-order-by
  {:supplier-display-name :s/display_name
   :raw-label :aa/raw_label
   :raw-label-normalized :aa/raw_label_normalized
   :unit :aa/unit
   :occurrence-count occurrence-count-expr})

(defn- append-and-clause
  [existing clause]
  (cond
    (nil? clause) existing
    (nil? existing) clause
    (and (vector? existing) (= :and (first existing))) (conj existing clause)
    :else [:and existing clause]))

(defn- add-having-clause
  [query clause]
  (if clause
    (update query :having append-and-clause clause)
    query))

(defn normalize-unit
  "Normalize an article/expense unit to the canonical lowercase form used in alias keys."
  [unit]
  (some-> unit str str/trim str/lower-case not-empty))

(defn- apply-unmapped-alias-filters
  [query {:keys [supplier-name raw-label raw-label-normalized unit]}]
  (cond-> query
    (seq supplier-name)
    (update :where shared-qb/merge-where-and
      [:ilike :s.display_name (str "%" supplier-name "%")])

    (seq raw-label)
    (update :where shared-qb/merge-where-and
      [:ilike :aa.raw_label (str "%" raw-label "%")])

    (seq raw-label-normalized)
    (update :where shared-qb/merge-where-and
      [:ilike :aa.raw_label_normalized (str "%" raw-label-normalized "%")])

    (seq unit)
    (update :where shared-qb/merge-where-and
      [:= :aa.unit unit])))

(defn- build-unmapped-aliases-query
  [{:keys [supplier-id supplier_id tenant-id tenant_id supplier-name raw-label raw-label-normalized
           unit occurrence-count-min occurrence-count-max]
    :as _opts}]
  (let [supplier-uuid (try-uuid (or supplier-id supplier_id))
        tenant-uuid (try-uuid (or tenant-id tenant_id))
        having-clause (cond-> nil
                        tenant-uuid (append-and-clause [:> occurrence-count-expr 0])
                        (some? occurrence-count-min) (append-and-clause [:>= occurrence-count-expr occurrence-count-min])
                        (some? occurrence-count-max) (append-and-clause [:<= occurrence-count-expr occurrence-count-max]))]
    (cond-> {:from [[:article_aliases :aa]]
             :left-join [[:suppliers :s] [:= :aa.supplier_id :s.id]
                         [:expense_items :ei]
                         (if tenant-uuid
                           [:and
                            [:= :ei.alias_id :aa.id]
                            [:= :ei.tenant_id tenant-uuid]]
                           [:= :ei.alias_id :aa.id])]
             :where [:and
                     [:is :aa.article_id nil]
                     ;; Settled as not-a-product by the resolver or a reviewer (noise / rejected)
                     [:not [:exists {:select [1] :from [[:article_alias_resolutions :r]]
                                     :where [:and [:= :r.alias_id :aa.id]
                                             [:in [:cast :r.status :text] ["noise" "rejected"]]]}]]]
             :group-by [:aa.id :aa.unit :s.display_name]}
      supplier-uuid (update :where conj [:= :aa.supplier_id supplier-uuid])
      true (apply-unmapped-alias-filters {:supplier-name supplier-name
                                          :raw-label raw-label
                                          :raw-label-normalized raw-label-normalized
                                          :unit (normalize-unit unit)})
      having-clause (add-having-clause having-clause))))

;; ============================================================================
;; Unknown Supplier Helper
;; ============================================================================

(defn get-unknown-supplier-id
  "Returns the ID of the 'Unknown Supplier' record.
   Creates it if it doesn't exist."
  [db]
  (let [existing (jdbc/execute-one!
                   db
                   (sql/format {:select [:id]
                                :from [:suppliers]
                                :where [:= :normalized_key unknown-supplier-normalized-key]
                                :limit 1})
                   {:builder-fn rs/as-unqualified-lower-maps})]
    (if existing
      (:id existing)
      (let [new-id (UUID/randomUUID)]
        (jdbc/execute-one!
          db
          (sql/format {:insert-into :suppliers
                       :values [{:id new-id
                                 :display_name "Unknown Supplier"
                                 :normalized_key unknown-supplier-normalized-key}]
                       :on-conflict [:normalized_key]
                       :do-update-set {:display_name :excluded/display_name}
                       :returning [:id]})
          {:builder-fn rs/as-unqualified-lower-maps})
        new-id))))

(defn find-unknown-supplier-id
  "Returns the ID of the 'Unknown Supplier' record, or nil if it doesn't exist.
   Read-only — does NOT create the record. Use get-unknown-supplier-id when creation is needed."
  [db]
  (some-> (jdbc/execute-one!
            db
            (sql/format {:select [:id]
                         :from [:suppliers]
                         :where [:= :normalized_key unknown-supplier-normalized-key]
                         :limit 1})
            {:builder-fn rs/as-unqualified-lower-maps})
    :id))

;; ============================================================================
;; Core Operations
;; ============================================================================

(defn- find-alias-by-key
  [db supplier-id raw-label-normalized unit]
  (jdbc/execute-one!
    db
    (sql/format {:select [:*]
                 :from [:article_aliases]
                 :where [:and
                         [:= :supplier_id supplier-id]
                         [:= :raw_label_normalized raw-label-normalized]
                         [:= :unit unit]]
                 :limit 1})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn find-or-create-alias!
  "Find or create an article_alias by (supplier_id, raw_label, unit).

   Returns the alias row (with :id, :article_id, etc.) and `:created?` to
   indicate whether this call inserted a new alias row.

   Parameters:
   - db: database connection
   - supplier-id: UUID or nil (uses Unknown Supplier if nil)
   - raw-label: the raw label text (required)
   - unit: canonical item unit; defaults to `kom` when blank/nil.

   The normalized key is computed from raw-label using articles/normalize-alias-label.

   OCR noise (\"69 60936564905\", \"KO\", \"MG 1 A 3a26\"; see article-resolution.noise) throws
   {:status 400 :reason :ocr-noise} so receipt extraction keeps the line without an alias. A label typed
   by a user is trusted: pass {:allow-noise? true}."
  ([db supplier-id raw-label]
   (find-or-create-alias! db supplier-id raw-label nil))
  ([db supplier-id raw-label unit]
   (find-or-create-alias! db supplier-id raw-label unit nil))
  ([db supplier-id raw-label unit {:keys [allow-noise?]}]
   (when (str/blank? raw-label)
     (throw (ex-info "raw_label is required" {:status 400 :field :raw_label})))
   (let [raw-label* (str/trim raw-label)
         normalized (articles/normalize-alias-label raw-label*)
         _ (when-let [reason (and (not allow-noise?)
                               (noise/ocr-noise-reason {:raw_label raw-label* :raw_label_normalized normalized}))]
             (throw (ex-info "raw_label looks like OCR noise"
                      {:status 400 :field :raw_label :reason :ocr-noise :noise reason :raw_label raw-label*})))
         _ (when-not (valid-alias-normalized-key? normalized)
             (throw (ex-info "raw_label normalizes to an invalid key"
                      {:status 400
                       :field :raw_label
                       :raw_label raw-label*
                       :raw_label_normalized normalized})))
         normalized-unit (or (normalize-unit unit) "kom")
         effective-supplier-id (or supplier-id (get-unknown-supplier-id db))
         row {:id (UUID/randomUUID)
              :supplier_id effective-supplier-id
              :raw_label raw-label*
              :raw_label_normalized normalized
              :unit normalized-unit
              :article_id nil}]
     (if-let [inserted (jdbc/execute-one!
                         db
                         (sql/format {:insert-into :article_aliases
                                      :values [row]
                                      :on-conflict [:supplier_id :raw_label_normalized :unit]
                                      :do-nothing true
                                      :returning [:*]})
                         {:builder-fn rs/as-unqualified-lower-maps})]
       (assoc inserted :created? true)
       (assoc (or (jdbc/execute-one!
                    db
                    (sql/format {:update :article_aliases
                                 :set {:raw_label raw-label*
                                       :unit normalized-unit}
                                 :where [:and
                                         [:= :supplier_id effective-supplier-id]
                                         [:= :raw_label_normalized normalized]
                                         [:= :unit normalized-unit]]
                                 :returning [:*]})
                    {:builder-fn rs/as-unqualified-lower-maps})
                (find-alias-by-key db effective-supplier-id normalized normalized-unit))
         :created? false)))))

(defn list-article-aliases
  "List article aliases with optional filters.

   Supports:
   - :supplier-display-name / :article-canonical-name / :raw-label / :raw-label-normalized (ILIKE)
   - :supplier-id / :supplier_id
   - :article-id / :article_id
   - :unmapped-only (boolean, filters to article_id IS NULL)"
  [db {:keys [supplier-id supplier_id article-id article_id unmapped-only] :as opts}]
  (let [supplier-uuid (try-uuid (or supplier-id supplier_id))
        article-uuid (try-uuid (or article-id article_id))
        base-filters (cond-> (vec (or (:base-filters config) []))
                       supplier-uuid (conj [:= :aa/supplier_id supplier-uuid])
                       article-uuid (conj [:= :aa/article_id article-uuid])
                       unmapped-only (conj [:is :aa/article_id nil]))
        config* (assoc config :base-filters base-filters)]
    ((factory/build-list-function config*) db opts)))

(defn count-article-aliases
  "Count article aliases with optional filters.

   Supports:
   - :supplier-display-name / :article-canonical-name / :raw-label / :raw-label-normalized (ILIKE)
   - :supplier-id / :supplier_id
   - :article-id / :article_id
   - :unmapped-only (boolean, filters to article_id IS NULL)"
  [db {:keys [supplier-id supplier_id article-id article_id unmapped-only] :as opts}]
  (let [supplier-uuid (try-uuid (or supplier-id supplier_id))
        article-uuid (try-uuid (or article-id article_id))
        base-filters (cond-> (vec (or (:base-filters config) []))
                       supplier-uuid (conj [:= :aa/supplier_id supplier-uuid])
                       article-uuid (conj [:= :aa/article_id article-uuid])
                       unmapped-only (conj [:is :aa/article_id nil]))
        config* (assoc config :base-filters base-filters)]
    ((factory/build-count-function config*) db opts)))

(defn list-unmapped-aliases
  "List unmapped article aliases (article_id IS NULL) with occurrence counts.
   When :tenant-id is provided, only includes aliases seen in that tenant's
   expense items and counts occurrences within that tenant."
  [db {:keys [limit offset sorts order-by order-dir]
       :or {limit 100 offset 0 order-dir :desc}
       :as opts}]
  (let [order-by* (some-> order-by model-naming/ensure-app-keyword)
        order-clauses (shared-qb/resolve-order-by-clauses
                        {:sorts sorts
                         :order-by order-by*
                         :order-dir order-dir
                         :allowed-order-by allowed-unmapped-aliases-order-by
                         :default-order-by occurrence-count-expr
                         :default-order-dir :desc
                         :tie-breaker [:aa.id :asc]})
        query (-> (build-unmapped-aliases-query opts)
                (assoc :select [[:aa.id]
                                [:aa.raw_label]
                                [:aa.raw_label_normalized]
                                [:aa.unit :unit]
                                [:aa.supplier_id]
                                [:s.display_name :supplier_display_name]
                                [occurrence-count-expr :occurrence_count]])
                (assoc :order-by order-clauses)
                (assoc :limit limit
                  :offset offset))]
    (jdbc/execute! db (sql/format query) {:builder-fn rs/as-unqualified-lower-maps})))

(defn count-unmapped-aliases
  "Count unmapped article aliases (article_id IS NULL), optionally by supplier and tenant.
   When :tenant-id is provided, only counts aliases with at least one expense item
   in that tenant."
  [db opts]
  (let [grouped-query (-> (build-unmapped-aliases-query opts)
                        (assoc :select [[:aa.id]]))
        query {:select [[[:count :*] :total]]
               :from [[grouped-query :unmapped_aliases]]}]
    (:total (jdbc/execute-one! db (sql/format query) {:builder-fn rs/as-unqualified-lower-maps}))))

(defn map-alias-to-article!
  "Map an alias to an article.

   Updates article_aliases.article_id for the given alias."
  [db alias-id article-id]
  (jdbc/execute-one!
    db
    (sql/format {:update :article_aliases
                 :set {:article_id article-id}
                 :where [:= :id alias-id]
                 :returning [:*]})
    {:builder-fn rs/as-unqualified-lower-maps}))

;; ============================================================================
;; Batch Operations
;; ============================================================================

;; ============================================================================
;; Related Records
;; ============================================================================

(defn- list-related-expenses
  [db alias-id limit]
  (jdbc/execute!
    db
    (sql/format {:select [[:e.id :id]
                          [:e.purchased_at :purchased_at]
                          [:e.total_amount :total_amount]
                          [:e.currency :currency]
                          [:e.notes :notes]
                          [:e.created_at :created_at]
                          [:s.display_name :supplier_display_name]
                          [:st.display_name :store_display_name]
                          [[:raw "json_agg(json_build_object('raw_label', aa.raw_label, 'canonical_name', a.canonical_name, 'qty', ei.qty, 'unit_price', ei.unit_price, 'line_total', ei.line_total) ORDER BY ei.created_at) FILTER (WHERE ei.id IS NOT NULL)"]
                           :items]]
                 :from [[:expenses :e]]
                 :left-join [[:expense_items :ei] [:= :ei.expense_id :e.id]
                             [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                             [:articles :a] [:= :a.id :ei.article_id]
                             [:suppliers :s] [:= :s.id :e.supplier_id]
                             [:stores :st] [:= :st.id :e.store_id]]
                 :where [:exists {:select [1]
                                  :from [:expense_items]
                                  :where [:and
                                          [:= :expense_id :e.id]
                                          [:= :alias_id alias-id]]}]
                 :group-by [:e.id :s.display_name :st.display_name]
                 :order-by [[:e.purchased_at :desc]]
                 :limit limit})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn- list-related-receipts
  [db alias-id limit]
  (jdbc/execute!
    db
    (sql/format {:select-distinct [[:r.id :id]
                                   [:r.original_filename :original_filename]
                                   [:r.status :status]
                                   [:r.supplier_guess :supplier_guess]
                                   [:r.total_amount_guess :total_amount_guess]
                                   [:r.currency_guess :currency_guess]
                                   [:r.created_at :created_at]
                                   [:r.updated_at :updated_at]
                                   [:e.id :expense_id]
                                   [:s.display_name :supplier_display_name]]
                 :from [[:expense_items :ei]]
                 :join [[:expenses :e] [:= :e.id :ei.expense_id]
                        [:receipts :r] [:= :r.id :e.receipt_id]]
                 :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]]
                 :where [:= :ei.alias_id alias-id]
                 :order-by [[:r.created_at :desc]]
                 :limit limit})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn list-related-records
  "List records related to an article alias by type.

  Supported types: expenses, receipts."
  [db alias-id {:keys [type limit]}]
  (when-not alias-id
    (throw (ex-info "alias-id is required" {:status 400})))
  (let [related-type (rr/normalize-related-type type)
        related-limit (rr/clamp-related-limit limit)]
    (case related-type
      :expenses (list-related-expenses db alias-id related-limit)
      :receipts (list-related-receipts db alias-id related-limit)
      (throw (ex-info
               "Invalid related type. Expected one of: expenses, receipts."
               {:status 400 :type type})))))

(defn- count-related-expenses [db alias-id]
  (:cnt (jdbc/execute-one! db
          (sql/format {:select [[[:raw "COUNT(DISTINCT e.id)"] :cnt]]
                       :from [[:expense_items :ei]]
                       :join [[:expenses :e] [:= :e.id :ei.expense_id]]
                       :where [:= :ei.alias_id alias-id]})
          {:builder-fn rs/as-unqualified-lower-maps})))

(defn- count-related-receipts [db alias-id]
  (:cnt (jdbc/execute-one! db
          (sql/format {:select [[[:raw "COUNT(DISTINCT r.id)"] :cnt]]
                       :from [[:expense_items :ei]]
                       :join [[:expenses :e] [:= :e.id :ei.expense_id]
                              [:receipts :r] [:= :r.id :e.receipt_id]]
                       :where [:= :ei.alias_id alias-id]})
          {:builder-fn rs/as-unqualified-lower-maps})))

(defn count-all-related
  "Count related records for all types for an article alias."
  [db alias-id]
  (when-not alias-id
    (throw (ex-info "alias-id is required" {:status 400})))
  {"expenses" (or (count-related-expenses db alias-id) 0)
   "receipts" (or (count-related-receipts db alias-id) 0)})
