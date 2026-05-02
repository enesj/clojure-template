(ns app.domain.backend.expenses.services.user-expense-reports.filters
  (:require
    [app.domain.backend.expenses.services.user-expense-reports.shared :as shared]))

(defn options
  "Filter options with available DB data for the current report scope."
  [db user-id opts]
  (let [user-id (shared/ensure-uuid user-id)
        item-joins [[:expenses :e] [:= :e.id :ei.expense_id]]
        item-left-joins [[:article_aliases :aa] [:= :aa.id :ei.alias_id]
                         [:articles :a] [:= :a.id :aa.article_id]
                         [:subcategories :sc] [:= :sc.id :a.subcategory_id]
                         [:categories :c] [:= :c.id :sc.category_id]
                         [:manufacturers :m] [:= :m.id :a.manufacturer_id]]
        suppliers (shared/query-option-list
                    db
                    {:select [[:e.supplier_id :id]
                              [[:coalesce :s.display_name [:inline "Unknown supplier"]] :name]]
                     :from [[:expenses :e]]
                     :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]]
                     :where (conj (shared/base-where user-id (dissoc opts :supplier-id))
                              [:is-not :e.supplier_id nil])
                     :group-by [:e.supplier_id :s.display_name]
                     :order-by [[:s.display_name :asc]]})
        expense-categories (shared/query-option-list
                             db
                             {:select [[:e.expense_category_id :id]
                                       [[:coalesce :ec.name [:inline "Uncategorized"]] :name]]
                              :from [[:expenses :e]]
                              :left-join [[:expense_categories :ec] [:= :ec.id :e.expense_category_id]]
                              :where (conj (shared/base-where user-id (dissoc opts :expense-category-id))
                                       [:is-not :e.expense_category_id nil])
                              :group-by [:e.expense_category_id :ec.name]
                              :order-by [[:ec.name :asc]]})
        expense-contexts (shared/query-option-list
                           db
                           {:select [[:e.expense_context_id :id]
                                     [[:coalesce :ctx.name [:inline "No context"]] :name]]
                            :from [[:expenses :e]]
                            :left-join [[:expense_contexts :ctx] [:= :ctx.id :e.expense_context_id]]
                            :where (conj (shared/base-where user-id (dissoc opts :expense-context-id :expense-context-missing?))
                                     [:is-not :e.expense_context_id nil])
                            :group-by [:e.expense_context_id :ctx.name]
                            :order-by [[:ctx.name :asc]]})
        categories (shared/query-option-list
                     db
                     {:select [[:c.id :id]
                               [[:coalesce :c.name [:inline "Uncategorized"]] :name]]
                      :from [[:expense_items :ei]]
                      :join item-joins
                      :left-join item-left-joins
                      :where (conj (shared/item-base-where user-id (dissoc opts :category-id))
                               [:is-not :c.id nil])
                      :group-by [:c.id :c.name]
                      :order-by [[:c.name :asc]]})
        subcategories (shared/query-option-list
                        db
                        {:select [[:sc.id :id]
                                  [[:coalesce :sc.name [:inline "Uncategorized"]] :name]
                                  [:sc.category_id :category_id]]
                         :from [[:expense_items :ei]]
                         :join item-joins
                         :left-join item-left-joins
                         :where (conj (shared/item-base-where user-id (dissoc opts :subcategory-id))
                                  [:is-not :sc.id nil])
                         :group-by [:sc.id :sc.name :sc.category_id]
                         :order-by [[:sc.name :asc]]})
        manufacturers (shared/query-option-list
                        db
                        {:select [[:m.id :id]
                                  [[:coalesce :m.display_name [:inline "Unknown manufacturer"]] :name]]
                         :from [[:expense_items :ei]]
                         :join item-joins
                         :left-join item-left-joins
                         :where (conj (shared/item-base-where user-id (dissoc opts :manufacturer-id))
                                  [:is-not :m.id nil])
                         :group-by [:m.id :m.display_name]
                         :order-by [[:m.display_name :asc]]})]
    {:suppliers suppliers
     :categories categories
     :subcategories subcategories
     :expense-categories expense-categories
    :expense-contexts expense-contexts
     :manufacturers manufacturers}))
