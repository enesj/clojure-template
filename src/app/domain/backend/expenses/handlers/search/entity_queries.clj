(ns app.domain.backend.expenses.handlers.search.entity-queries
  "Per-entity search queries — all tenant-scoped when tenant-id is provided."
  (:require
    [app.domain.backend.expenses.handlers.search.helpers :as sh]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(defn search-payers
  [db term limit tenant-id]
  (let [p (sh/pattern term)
        text-where (sh/fuzzy-text-where [:label] term p)
        where (if tenant-id
                [:and text-where [:= :tenant_id tenant-id]]
                text-where)]
    (jdbc/execute!
      db
      (sql/format {:select [:id :label :type]
                   :from [:payers]
                   :where where
                   :order-by [[:label :asc]]
                   :limit limit})
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn search-expense-cats
  [db term limit tenant-id]
  (let [p (sh/pattern term)
        text-where (sh/fuzzy-text-where [:name] term p)
        where (if tenant-id
                [:and text-where [:= :tenant_id tenant-id]]
                text-where)]
    (jdbc/execute!
      db
      (sql/format {:select [:id :name]
                   :from [:expense_categories]
                   :where where
                   :order-by [[:name :asc]]
                   :limit limit})
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn search-suppliers
  [db term limit tenant-id]
  (let [p (sh/pattern term)]
    (jdbc/execute!
      db
      (sql/format
        (if tenant-id
          {:select [[:s.id :id]
                    [:s.display_name :display_name]
                    [:s.normalized_key :normalized_key]
                    [:s.address :address]]
           :from [[:suppliers :s]]
           :where [:and
                   (sh/fuzzy-text-where [:s.display_name :s.address] term p)
                   [:or
                    [:exists {:select [1]
                              :from [[:expenses :e]]
                              :where [:and
                                      [:= :e.tenant_id tenant-id]
                                      [:= :e.supplier_id :s.id]]}]
                    [:exists {:select [1]
                              :from [[:receipts :r]]
                              :join [[:supplier_aliases :sa] [:= :sa.id :r.supplier_alias_id]]
                              :where [:and
                                      [:= :r.tenant_id tenant-id]
                                      [:= :sa.supplier_id :s.id]]}]
                    [:exists {:select [1]
                              :from [[:receipts :r]]
                              :join [[:store_aliases :sta] [:= :sta.id :r.store_alias_id]
                                     [:stores :st_receipt] [:= :st_receipt.id :sta.store_id]]
                              :where [:and
                                      [:= :r.tenant_id tenant-id]
                                      [:= :st_receipt.supplier_id :s.id]]}]]]
           :order-by [[:s.display_name :asc]]
           :limit limit}
          {:select [:id :display_name :normalized_key :address]
           :from [:suppliers]
           :where (sh/fuzzy-text-where [:display_name :address] term p)
           :order-by [[:display_name :asc]]
           :limit limit}))
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn search-stores
  [db term limit tenant-id]
  (let [p (sh/pattern term)]
    (jdbc/execute!
      db
      (sql/format
        (if tenant-id
          {:select [[:st.id :id]
                    [:st.display_name :display_name]
                    [:st.normalized_key :normalized_key]
                    [:st.address :address]]
           :from [[:stores :st]]
           :where [:and
                   (sh/fuzzy-text-where [:st.display_name :st.address] term p)
                   [:or
                    [:exists {:select [1]
                              :from [[:expenses :e]]
                              :where [:and
                                      [:= :e.tenant_id tenant-id]
                                      [:= :e.store_id :st.id]]}]
                    [:exists {:select [1]
                              :from [[:receipts :r]]
                              :join [[:store_aliases :sta] [:= :sta.id :r.store_alias_id]]
                              :where [:and
                                      [:= :r.tenant_id tenant-id]
                                      [:= :sta.store_id :st.id]]}]]]
           :order-by [[:st.display_name :asc]]
           :limit limit}
          {:select [:id :display_name :normalized_key :address]
           :from [:stores]
           :where (sh/fuzzy-text-where [:display_name :address] term p)
           :order-by [[:display_name :asc]]
           :limit limit}))
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn search-articles
  [db term limit tenant-id]
  (let [p (sh/pattern term)]
    (jdbc/execute!
      db
      (sql/format
        (if tenant-id
          {:select [[:a.id :id]
                    [:a.canonical_name :canonical_name]]
           :from [[:articles :a]]
           :where [:and
                   (sh/fuzzy-text-where [:a.canonical_name] term p)
                   [:or
                    [:exists {:select [1]
                              :from [[:expense_items :ei]]
                              :where [:and
                                      [:= :ei.tenant_id tenant-id]
                                      [:= :ei.article_id :a.id]]}]
                    [:exists {:select [1]
                              :from [[:expense_items :ei]]
                              :join [[:article_aliases :aa] [:= :aa.id :ei.alias_id]]
                              :where [:and
                                      [:= :ei.tenant_id tenant-id]
                                      [:= :aa.article_id :a.id]]}]]]
           :order-by [[:a.canonical_name :asc]]
           :limit limit}
          {:select [:id :canonical_name]
           :from [:articles]
           :where (sh/fuzzy-text-where [:canonical_name] term p)
           :order-by [[:canonical_name :asc]]
           :limit limit}))
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn search-categories
  [db term limit tenant-id]
  (let [p (sh/pattern term)]
    (jdbc/execute!
      db
      (sql/format
        (if tenant-id
          {:select [[:c.id :id]
                    [:c.name :name]
                    [:c.description :description]]
           :from [[:categories :c]]
           :where [:and
                   (sh/fuzzy-text-where [:c.name] term p)
                   [:or
                    [:exists {:select [1]
                              :from [[:expense_items :ei]]
                              :join [[:articles :a_item] [:= :a_item.id :ei.article_id]
                                     [:subcategories :sub_item] [:= :sub_item.id :a_item.subcategory_id]]
                              :where [:and
                                      [:= :ei.tenant_id tenant-id]
                                      [:= :sub_item.category_id :c.id]]}]
                    [:exists {:select [1]
                              :from [[:expense_items :ei]]
                              :join [[:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                     [:articles :a_alias] [:= :a_alias.id :aa.article_id]
                                     [:subcategories :sub_alias] [:= :sub_alias.id :a_alias.subcategory_id]]
                              :where [:and
                                      [:= :ei.tenant_id tenant-id]
                                      [:= :sub_alias.category_id :c.id]]}]]]
           :order-by [[:c.name :asc]]
           :limit limit}
          {:select [:id :name :description]
           :from [:categories]
           :where (sh/fuzzy-text-where [:name] term p)
           :order-by [[:name :asc]]
           :limit limit}))
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn search-subcategories
  [db term limit tenant-id]
  (let [p (sh/pattern term)]
    (jdbc/execute!
      db
      (sql/format
        (if tenant-id
          {:select [[:sub.id :id]
                    [:sub.name :name]
                    [:c.name :category_name]]
           :from [[:subcategories :sub]]
           :join [[:categories :c] [:= :c.id :sub.category_id]]
           :where [:and
                   (sh/fuzzy-text-where [:sub.name] term p)
               [:or
                [:is :sub.tenant_id nil]
                [:= :sub.tenant_id tenant-id]]
                   [:or
                    [:exists {:select [1]
                              :from [[:expense_items :ei]]
                              :join [[:articles :a_item] [:= :a_item.id :ei.article_id]]
                              :where [:and
                                      [:= :ei.tenant_id tenant-id]
                                      [:= :a_item.subcategory_id :sub.id]]}]
                    [:exists {:select [1]
                              :from [[:expense_items :ei]]
                              :join [[:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                     [:articles :a_alias] [:= :a_alias.id :aa.article_id]]
                              :where [:and
                                      [:= :ei.tenant_id tenant-id]
                                      [:= :a_alias.subcategory_id :sub.id]]}]]]
           :order-by [[:c.name :asc] [:sub.name :asc]]
           :limit limit}
          {:select [[:s.id :id] [:s.name :name] [:c.name :category_name]]
           :from [[:subcategories :s]]
           :join [[:categories :c] [:= :c.id :s.category_id]]
             :where [:and
               (sh/fuzzy-text-where [:s.name] term p)
               [:is :s.tenant_id nil]]
           :order-by [[:c.name :asc] [:s.name :asc]]
           :limit limit}))
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn search-manufacturers
  [db term limit tenant-id]
  (let [p (sh/pattern term)]
    (jdbc/execute!
      db
      (sql/format
        (if tenant-id
          {:select [[:m.id :id]
                    [:m.display_name :display_name]
                    [:m.normalized_key :normalized_key]]
           :from [[:manufacturers :m]]
           :where [:and
                   (sh/fuzzy-text-where [:m.display_name :m.normalized_key] term p)
                   [:or
                    [:exists {:select [1]
                              :from [[:expense_items :ei]]
                              :join [[:articles :a_item] [:= :a_item.id :ei.article_id]]
                              :where [:and
                                      [:= :ei.tenant_id tenant-id]
                                      [:= :a_item.manufacturer_id :m.id]]}]
                    [:exists {:select [1]
                              :from [[:expense_items :ei]]
                              :join [[:article_aliases :aa] [:= :aa.id :ei.alias_id]
                                     [:articles :a_alias] [:= :a_alias.id :aa.article_id]]
                              :where [:and
                                      [:= :ei.tenant_id tenant-id]
                                      [:= :a_alias.manufacturer_id :m.id]]}]]]
           :order-by [[:m.display_name :asc]]
           :limit limit}
          {:select [:id :display_name :normalized_key]
           :from [:manufacturers]
           :where (sh/fuzzy-text-where [:display_name :normalized_key] term p)
           :order-by [[:display_name :asc]]
           :limit limit}))
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn search-cities
  [db term limit tenant-id]
  (let [p (sh/pattern term)]
    (jdbc/execute!
      db
      (sql/format
        (if tenant-id
          {:select [[:city.id :id]
                    [:city.name :name]
                    [:city.zip :zip]
                    [:city.country :country]]
           :from [[:cities :city]]
           :where [:and
                   (sh/fuzzy-text-where [:city.name :city.zip] term p)
                   [:or
                    [:exists {:select [1]
                              :from [[:expenses :e]]
                              :join [[:stores :st_exp] [:= :st_exp.id :e.store_id]]
                              :where [:and
                                      [:= :e.tenant_id tenant-id]
                                      [:= :st_exp.city_id :city.id]]}]
                    [:exists {:select [1]
                              :from [[:receipts :r]]
                              :join [[:store_aliases :sta] [:= :sta.id :r.store_alias_id]
                                     [:stores :st_receipt] [:= :st_receipt.id :sta.store_id]]
                              :where [:and
                                      [:= :r.tenant_id tenant-id]
                                      [:= :st_receipt.city_id :city.id]]}]]]
           :order-by [[:city.name :asc]]
           :limit limit}
          {:select-distinct [[:c.id :id] [:c.name :name] [:c.zip :zip] [:c.country :country]]
           :from [[:cities :c]]
           :join [[:stores :st] [:= :st.city_id :c.id]
                  [:expenses :e] [:= :e.store_id :st.id]]
           :where (sh/fuzzy-text-where [:c.name :c.zip] term p)
           :order-by [[:c.name :asc]]
           :limit limit}))
      {:builder-fn rs/as-unqualified-lower-maps})))
