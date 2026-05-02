(ns app.domain.backend.expenses.handlers.search.related-records
  "Rich detail data for selected search results — history, aggregates, cross-references."
  (:require
    [app.domain.backend.expenses.handlers.search.helpers :as sh]
    [app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log]))

;; ---------------------------------------------------------------------------
;; Shared query helpers
;; ---------------------------------------------------------------------------

(defn- tenant-scope
  "Optionally extend a HoneySQL WHERE condition with tenant_id scoping."
  [cond tenant-id]
  (if tenant-id [:and cond [:= :e.tenant_id tenant-id]] cond))

(defn- fetch-article-aggs
  "Article aggregates: id, canonical_name, total_qty, total_bam.
  `joins` must start from [:expense_items :ei] and include at least
  [:expenses :e], [:article_aliases :aa], [:articles :a].
  Optional `opts` map supports `:limit`."
  ([db joins where] (fetch-article-aggs db joins where nil))
  ([db joins where opts]
   (jdbc/execute!
     db
     (sql/format (cond-> {:select [[:a.id :id]
                                   [:a.canonical_name :canonical_name]
                                   [[:sum :ei.qty] :total_qty]
                                   [[:sum :ei.line_total] :total_bam]]
                          :from [[:expense_items :ei]]
                          :join joins
                          :where where
                          :group-by [:a.id :a.canonical_name]
                          :order-by [[[:sum :ei.line_total] :desc]]}
                   (:limit opts) (assoc :limit (:limit opts))))
     {:builder-fn rs/as-unqualified-lower-maps})))

(defn- fetch-last-prices
  "Last price per article using ROW_NUMBER window function.
  `joins` and `where` scope the expense_items rows."
  [db joins where]
  (jdbc/execute!
    db
    (sql/format {:select [:article_id :unit_price]
                 :from [[{:select [[:aa.article_id :article_id]
                                   [:ei.unit_price :unit_price]
                                   [[:over [[:row_number]
                                            {:partition-by :aa.article_id
                                             :order-by [[:e.purchased_at :desc]]}]]
                                    :rn]]
                          :from [[:expense_items :ei]]
                          :join joins
                          :where where}
                         :sub]]
                 :where [:= :rn 1]})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn- merge-last-prices
  "Merge last-price data into article-aggs rows.
  Returns article-aggs with :last_price assoc'd."
  [article-aggs last-prices]
  (let [price-map (reduce (fn [acc {:keys [article_id unit_price]}]
                            (assoc acc (str article_id) unit_price))
                    {} last-prices)]
    (mapv (fn [art]
            (assoc art :last_price (get price-map (str (:id art)))))
      article-aggs)))

(defn- articles-with-last-prices
  "Fetch article aggregates and merge last-price data.
  Convenience wrapper combining fetch-article-aggs + fetch-last-prices + merge-last-prices.
  Optional `agg-opts` passed through to fetch-article-aggs (e.g. {:limit 10})."
  ([db agg-joins agg-where price-joins price-where]
   (articles-with-last-prices db agg-joins agg-where price-joins price-where nil))
  ([db agg-joins agg-where price-joins price-where agg-opts]
   (let [article-aggs (fetch-article-aggs db agg-joins agg-where agg-opts)]
     (if (seq article-aggs)
       (let [last-prices (fetch-last-prices db price-joins price-where)]
         (merge-last-prices article-aggs last-prices))
       article-aggs))))

;; ---------------------------------------------------------------------------
;; Entity-specific related data
;; ---------------------------------------------------------------------------

(defn- related-for-article
  "Rich article detail: metadata, price history, stores, aggregates."
  [db article-id _limit tenant-id]
  (let [;; 1. Article detail with manufacturer, category, subcategory
        detail (first
                 (jdbc/execute!
                   db
                   (sql/format {:select [[:a.canonical_name :canonical_name]
                                         [:a.link :link]
                                         [:m.display_name :manufacturer_display_name]
                                         [:sub.name :subcategory_name]
                                         [:c.name :category_name]]
                                :from [[:articles :a]]
                                :left-join [[:manufacturers :m] [:= :m.id :a.manufacturer_id]
                                            [:subcategories :sub] [:= :sub.id :a.subcategory_id]
                                            [:categories :c] [:= :c.id :sub.category_id]]
                                :where [:= :a.id article-id]})
                   {:builder-fn rs/as-unqualified-lower-maps}))

        ;; Common WHERE: expense_items links to articles via article_aliases
        ei-where (tenant-scope [:= :aa.article_id article-id] tenant-id)

        ;; 2. Aggregate stats: total turnover, item count
        stats (first
                (jdbc/execute!
                  db
                  (sql/format {:select [[[:coalesce [:sum :ei.line_total] [:inline 0]] :total_turnover]
                                        [[:count :ei.id] :total_items]]
                               :from [[:expense_items :ei]]
                               :join [[:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                      [:expenses :e] [:= :e.id :ei.expense_id]]
                               :where ei-where})
                  {:builder-fn rs/as-unqualified-lower-maps}))

        ;; 3. Price history (most recent 50, one row per expense)
        price-history (jdbc/execute!
                        db
                        (sql/format {:select [[[:min :ei.unit_price] :unit_price]
                                              [[:sum :ei.qty] :qty]
                                              [[:sum :ei.line_total] :line_total]
                                              [:e.id :expense_id]
                                              [:e.purchased_at :purchased_at]
                                              [:st.id :store_id]
                                              [:st.display_name :store_display_name]
                                              [:st.address :store_address]
                                              [:sup.display_name :supplier_display_name]]
                                     :from [[:expense_items :ei]]
                                     :join [[:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                            [:expenses :e] [:= :e.id :ei.expense_id]]
                                     :left-join [[:stores :st] [:= :st.id :e.store_id]
                                                 [:suppliers :sup] [:= :sup.id :e.supplier_id]]
                                     :where ei-where
                                     :group-by [:e.id :e.purchased_at
                                                :st.id :st.display_name :st.address
                                                :sup.display_name]
                                     :order-by [[:e.purchased_at :desc]]
                                     :limit 50})
                        {:builder-fn rs/as-unqualified-lower-maps})

        ;; 4. Distinct stores (all, not limited by price history)
        stores (jdbc/execute!
                 db
                 (sql/format {:select-distinct [[:st.id :id]
                                                [:st.display_name :display_name]
                                                [:st.address :address]]
                              :from [[:expense_items :ei]]
                              :join [[:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                     [:expenses :e] [:= :e.id :ei.expense_id]
                                     [:stores :st] [:= :st.id :e.store_id]]
                              :where ei-where
                              :order-by [[:st.display_name :asc]]})
                 {:builder-fn rs/as-unqualified-lower-maps})

        ;; Last price = unit_price from the most recent entry
        last-price (:unit_price (first price-history))]
    {:detail (assoc detail :last_price last-price)
     :stats stats
     :price_history price-history
     :stores stores}))

(defn- related-for-supplier
  "Supplier detail: stores for this supplier + articles purchased from this supplier."
  [db supplier-id limit tenant-id]
  (let [expense-join-cond (if tenant-id
                            [:and [:= :e.store_id :st.id]
                             [:= :e.tenant_id tenant-id]]
                            [:= :e.store_id :st.id])
        where (tenant-scope [:= :e.supplier_id supplier-id] tenant-id)
        agg-joins [[:expenses :e] [:= :e.id :ei.expense_id]
                   [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                   [:articles :a] [:= :a.id :aa.article_id]]
        price-joins [[:expenses :e] [:= :e.id :ei.expense_id]
                     [:article_aliases :aa] [:= :aa.id :ei.alias_id]]

        ;; 1. All stores belonging to this supplier, with tenant-scoped total spendings.
        stores (jdbc/execute!
                 db
                 (sql/format {:select [[:st.id :id]
                                       [:st.display_name :display_name]
                                       [:st.address :address]
                                       [:st.supplier_id :supplier_id]
                                       [[:coalesce [:sum :e.total_amount] [:inline 0]] :total_spendings]]
                              :from [[:stores :st]]
                              :left-join [[:expenses :e] expense-join-cond]
                              :where [:= :st.supplier_id supplier-id]
                              :group-by [:st.id :st.display_name :st.address :st.supplier_id]
                              :order-by [[[:sum :e.total_amount] :desc]
                                         [:st.display_name :asc]]
                              :limit limit})
                 {:builder-fn rs/as-unqualified-lower-maps})

        ;; 2+3. Articles + last prices
        articles (articles-with-last-prices db agg-joins where price-joins where {:limit limit})]
    {:stores stores
     :articles articles}))

(defn- related-for-store
  "Store detail (supplier, city, address) + articles purchased there."
  [db store-id _limit tenant-id]
  (let [;; 1. Store detail with supplier and city names
        detail (first
                 (jdbc/execute!
                   db
                   (sql/format {:select [[:s.display_name :display_name]
                                         [:s.address :address]
                                         [:sup.display_name :supplier_display_name]
                                         [:c.name :city_name]]
                                :from [[:stores :s]]
                                :left-join [[:suppliers :sup] [:= :sup.id :s.supplier_id]
                                            [:cities :c] [:= :c.id :s.city_id]]
                                :where [:= :s.id store-id]})
                   {:builder-fn rs/as-unqualified-lower-maps}))

        art-where (tenant-scope [:= :e.store_id store-id] tenant-id)
        agg-joins [[:expenses :e] [:= :e.id :ei.expense_id]
                   [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                   [:articles :a] [:= :a.id :aa.article_id]]
        price-joins [[:expenses :e] [:= :e.id :ei.expense_id]
                     [:article_aliases :aa] [:= :aa.id :ei.alias_id]]

        ;; 2+3. Articles + last prices
        articles (articles-with-last-prices db agg-joins art-where price-joins art-where)]
    {:detail detail
     :articles articles}))

(defn- related-for-payer
  "Recent expenses for this payer."
  [db payer-id limit tenant-id]
  (let [where (tenant-scope [:= :e.payer_id payer-id] tenant-id)]
    {:expenses (jdbc/execute!
                 db
                 (sql/format {:select [[:e.id :id]
                                       [:e.total_amount :total_amount]
                                       [:e.currency :currency]
                                       [:e.purchased_at :purchased_at]
                                       [:s.display_name :supplier_display_name]]
                              :from [[:expenses :e]]
                              :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]]
                              :where where
                              :order-by [[:e.purchased_at :desc]]
                              :limit limit})
                 {:builder-fn rs/as-unqualified-lower-maps})}))

(defn- related-for-expense-cat
  "Recent expenses in this category."
  [db cat-id limit tenant-id]
  (let [where (tenant-scope [:= :e.expense_category_id cat-id] tenant-id)]
    {:expenses (jdbc/execute!
                 db
                 (sql/format {:select [[:e.id :id]
                                       [:e.total_amount :total_amount]
                                       [:e.currency :currency]
                                       [:e.purchased_at :purchased_at]
                                       [:s.display_name :supplier_display_name]]
                              :from [[:expenses :e]]
                              :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]]
                              :where where
                              :order-by [[:e.purchased_at :desc]]
                              :limit limit})
                 {:builder-fn rs/as-unqualified-lower-maps})}))

(defn- related-for-manufacturer
  "Manufacturer detail: suppliers + articles purchased (aggregates + last prices).
   Articles include supplier_id so the frontend can filter by supplier."
  [db manufacturer-id _limit tenant-id]
  (let [where (tenant-scope [:= :a.manufacturer_id manufacturer-id] tenant-id)
        base-joins [[:expenses :e] [:= :e.id :ei.expense_id]
                    [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                    [:articles :a] [:= :a.id :aa.article_id]]

        ;; 1. Suppliers that carry this manufacturer's articles (via expenses)
        suppliers (jdbc/execute!
                    db
                    (sql/format {:select-distinct [[:sup.id :id]
                                                   [:sup.display_name :display_name]
                                                   [:sup.address :address]]
                                 :from [[:expense_items :ei]]
                                 :join (into base-joins
                                         [[:suppliers :sup] [:= :sup.id :e.supplier_id]])
                                 :where where
                                 :order-by [[:sup.display_name :asc]]})
                    {:builder-fn rs/as-unqualified-lower-maps})

        ;; 2+4. Articles + last prices
        articles (articles-with-last-prices db base-joins where base-joins where)

        ;; 3. Per-supplier article aggregates (for filtering)
        supplier-articles (jdbc/execute!
                            db
                            (sql/format {:select [[:a.id :article_id]
                                                  [:e.supplier_id :supplier_id]
                                                  [[:sum :ei.qty] :total_qty]
                                                  [[:sum :ei.line_total] :total_bam]]
                                         :from [[:expense_items :ei]]
                                         :join base-joins
                                         :where where
                                         :group-by [:a.id :e.supplier_id]})
                            {:builder-fn rs/as-unqualified-lower-maps})]
    {:suppliers suppliers
     :articles articles
     :supplier-articles supplier-articles}))

(defn- related-for-category
  "Category detail: subcategories, stores, + articles (with subcategory/store filtering)."
  [db category-id _limit tenant-id]
  (let [;; 1. Subcategories
        subcategories (jdbc/execute!
                        db
                        (sql/format {:select [:id :name]
                                     :from [:subcategories]
                                     :where (if tenant-id
                                              [:and
                                               [:= :category_id category-id]
                                               [:or
                                                [:is :tenant_id nil]
                                                [:= :tenant_id tenant-id]]]
                                              [:and
                                               [:= :category_id category-id]
                                               [:is :tenant_id nil]])
                                     :order-by [[:name :asc]]})
                        {:builder-fn rs/as-unqualified-lower-maps})

        where (tenant-scope [:= :sub.category_id category-id] tenant-id)
        subcat-joins [[:expenses :e] [:= :e.id :ei.expense_id]
                      [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                      [:articles :a] [:= :a.id :aa.article_id]
                      [:subcategories :sub] [:= :sub.id :a.subcategory_id]]

        ;; 2. Stores where articles from this category were purchased
        stores (jdbc/execute!
                 db
                 (sql/format {:select-distinct [[:st.id :id]
                                                [:st.display_name :display_name]
                                                [:st.address :address]]
                              :from [[:expense_items :ei]]
                              :join (into subcat-joins
                                      [[:stores :st] [:= :st.id :e.store_id]])
                              :where where
                              :order-by [[:st.display_name :asc]]})
                 {:builder-fn rs/as-unqualified-lower-maps})

        ;; 3+6. Articles + last prices
        articles (articles-with-last-prices db subcat-joins where subcat-joins where)

        ;; 4. Per-subcategory article aggregates (for filtering)
        subcat-articles (jdbc/execute!
                          db
                          (sql/format {:select [[:a.id :article_id]
                                                [:a.subcategory_id :subcategory_id]
                                                [[:sum :ei.qty] :total_qty]
                                                [[:sum :ei.line_total] :total_bam]]
                                       :from [[:expense_items :ei]]
                                       :join subcat-joins
                                       :where where
                                       :group-by [:a.id :a.subcategory_id]})
                          {:builder-fn rs/as-unqualified-lower-maps})

        ;; 5. Per-store article aggregates (for filtering)
        store-articles (jdbc/execute!
                         db
                         (sql/format {:select [[:a.id :article_id]
                                               [:e.store_id :store_id]
                                               [[:sum :ei.qty] :total_qty]
                                               [[:sum :ei.line_total] :total_bam]]
                                      :from [[:expense_items :ei]]
                                      :join subcat-joins
                                      :where where
                                      :group-by [:a.id :e.store_id]})
                         {:builder-fn rs/as-unqualified-lower-maps})]
    {:subcategories subcategories
     :stores stores
     :articles articles
     :subcat-articles subcat-articles
     :store-articles store-articles}))

(defn- related-for-subcategory
  "Subcategory detail: stores + articles in this subcategory."
  [db subcategory-id _limit tenant-id]
  (let [where (tenant-scope [:= :a.subcategory_id subcategory-id] tenant-id)
        base-joins [[:expenses :e] [:= :e.id :ei.expense_id]
                    [:article_aliases :aa] [:= :aa.id :ei.alias_id]
                    [:articles :a] [:= :a.id :aa.article_id]]

        ;; 1. Stores
        stores (jdbc/execute!
                 db
                 (sql/format {:select-distinct [[:st.id :id]
                                                [:st.display_name :display_name]
                                                [:st.address :address]]
                              :from [[:expense_items :ei]]
                              :join (into base-joins
                                      [[:stores :st] [:= :st.id :e.store_id]])
                              :where where
                              :order-by [[:st.display_name :asc]]})
                 {:builder-fn rs/as-unqualified-lower-maps})

        ;; 2+4. Articles + last prices
        articles (articles-with-last-prices db base-joins where base-joins where)

        ;; 3. Per-store article aggregates
        store-articles (jdbc/execute!
                         db
                         (sql/format {:select [[:a.id :article_id]
                                               [:e.store_id :store_id]
                                               [[:sum :ei.qty] :total_qty]
                                               [[:sum :ei.line_total] :total_bam]]
                                      :from [[:expense_items :ei]]
                                      :join base-joins
                                      :where where
                                      :group-by [:a.id :e.store_id]})
                         {:builder-fn rs/as-unqualified-lower-maps})]
    {:stores stores
     :articles articles
     :store-articles store-articles}))

(defn- related-results
  [db entity-type entity-id limit tenant-id]
  (case entity-type
    "articles" (related-for-article db entity-id limit tenant-id)
    "suppliers" (related-for-supplier db entity-id limit tenant-id)
    "stores" (related-for-store db entity-id limit tenant-id)
    "payers" (related-for-payer db entity-id limit tenant-id)
    "expense-cats" (related-for-expense-cat db entity-id limit tenant-id)
    "manufacturers" (related-for-manufacturer db entity-id limit tenant-id)
    "categories" (related-for-category db entity-id limit tenant-id)
    "subcategories" (related-for-subcategory db entity-id limit tenant-id)
    {}))

(defn- related-response
  [db request tenant-id]
  (let [params (:query-params request)
        entity-type (get params "type")
        entity-id (h/try-parse-uuid (get params "id"))
        limit (sh/parse-search-limit request 8)]
    (if-not entity-id
      (h/json-response {:error "Missing or invalid id"} 400)
      (try
        (let [related (related-results db entity-type entity-id limit tenant-id)]
          (h/json-response {:related related
                            :type entity-type
                            :id (str entity-id)}))
        (catch Exception e
          (log/error e "Error fetching related records"
            {:type entity-type
             :id entity-id
             :tenant-id tenant-id})
          (h/json-response {:error "Failed to fetch related records"} 500))))))

(defn user-related-handler
  "Fetch related records for a selected search result.
   Query params: type (string), id (uuid string)."
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/expenses-read-roles "Role required")]
        forbidden
        (related-response db request (h/get-tenant-id request)))
      (h/unauthorized-response))))

(defn admin-related-handler
  "Fetch related records for the admin search page using global scope."
  [db]
  (fn [request]
    (related-response db request nil)))
