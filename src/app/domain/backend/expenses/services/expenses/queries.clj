(ns app.domain.backend.expenses.services.expenses.queries
  (:require
    [app.domain.backend.expenses.services.expenses.parsing :as parsing]
    [app.shared.model-naming :as model-naming]
    [app.shared.query-builders :as shared-qb]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(def ^:private allowed-admin-expenses-order-by
  "Allowlisted sort keys for the admin expenses list. Keys are app keywords."
  {:purchased-at :e.purchased_at
   :expense-date :e.purchased_at
   :created-at :e.created_at
   :updated-at :e.updated_at
   :total-amount :e.total_amount
   :currency :e.currency
   :supplier-display-name :s.display_name
   :payer-label :p.label
   :expense-category-name :ec.name
  :expense-context-name :ctx.name
   :item-count :item_count})

(defn- normalize-sort-entry
  [{:keys [field] :as sort-entry}]
  (cond-> sort-entry
    field (assoc :field (model-naming/ensure-app-keyword field))))

(defn- normalize-sorts
  [sorts]
  (->> (or sorts [])
    (map normalize-sort-entry)
    vec))

(defn source-clause
  [col source]
  (case (some-> source str)
    "manual"  [:is col nil]
    "receipt" [:is-not col nil]
    "none"    [:= 1 0]
    nil))

(defn get-expense-with-items
  ([db id] (get-expense-with-items db id nil))
  ([db id tenant-id]
   (let [where (if tenant-id
                 [:and [:= :e.id id] [:= :e.tenant_id tenant-id]]
                 [:= :e.id id])
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
                                :where [:= :ei.expense_id id]
                                :order-by [[:ei.created_at :asc]]})
                   {:builder-fn rs/as-unqualified-lower-maps}))]
     (when expense
       (assoc expense :items items)))))

(defn list-expenses
  [db {:keys [from to supplier-id payer-id expense-context-id tenant-id source limit offset sorts order-by order-dir]
       :or {limit 50 offset 0 order-dir :desc}}]
  (let [from (try (parsing/parse-instant! :from from) (catch Exception _ nil))
        to (try (parsing/parse-instant! :to to) (catch Exception _ nil))
        source-where (source-clause :e.receipt_id source)
        order-clauses (shared-qb/resolve-order-by-clauses
                        {:sorts (normalize-sorts sorts)
                         :order-by (some-> order-by model-naming/ensure-app-keyword)
                         :order-dir order-dir
                         :allowed-order-by allowed-admin-expenses-order-by
                         :default-order-by :created-at
                         :default-order-dir :desc
                         :tie-breaker [:e.id :asc]})
        base-where (cond-> [:and]
                     tenant-id (conj [:= :e.tenant_id tenant-id])
                     from (conj [:>= :e.purchased_at from])
                     to (conj [:<= :e.purchased_at to])
                     supplier-id (conj [:= :e.supplier_id supplier-id])
                     payer-id (conj [:= :e.payer_id payer-id])
                     expense-context-id (conj [:= :e.expense_context_id expense-context-id])
                     source-where (conj source-where))
        query {:select [[:e.*]
                        [:s.display_name :supplier_display_name]
                        [:s.normalized_key :supplier_normalized_key]
                        [:p.label :payer_label]
                        [:p.type :payer_type]
                        [:ctx.name :expense_context_name]
                        [{:select [[[:count :*] :n]]
                          :from [[:expense_items :ei]]
                          :where [:and
                                  [:= :ei.expense_id :e.id]
                                  [:= :ei.tenant_id :e.tenant_id]]}
                         :item_count]
                        [:ec.name :expense_category_name]]
               :from [[:expenses :e]]
               :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]
                           [:payers :p] [:= :p.id :e.payer_id]
                           [:expense_categories :ec] [:= :ec.id :e.expense_category_id]
                           [:expense_contexts :ctx] [:= :ctx.id :e.expense_context_id]]
               :where base-where
               :order-by order-clauses
               :limit limit
               :offset offset}]
    (jdbc/execute! db (sql/format query) {:builder-fn rs/as-unqualified-lower-maps})))

(defn count-expenses
  [db {:keys [from to supplier-id payer-id expense-context-id tenant-id source]}]
  (let [from (try (parsing/parse-instant! :from from) (catch Exception _ nil))
        to (try (parsing/parse-instant! :to to) (catch Exception _ nil))
        source-where (source-clause :receipt_id source)
        base-where (cond-> [:and]
                     tenant-id (conj [:= :tenant_id tenant-id])
                     from (conj [:>= :purchased_at from])
                     to (conj [:<= :purchased_at to])
                     supplier-id (conj [:= :supplier_id supplier-id])
                     payer-id (conj [:= :payer_id payer-id])
                     expense-context-id (conj [:= :expense_context_id expense-context-id])
                     source-where (conj source-where))
        row (jdbc/execute-one!
              db
              (sql/format {:select [[[:count :*] :total]]
                           :from [:expenses]
                           :where base-where})
              {:builder-fn rs/as-unqualified-lower-maps})]
    {:total (long (or (:total row) 0))}))