(ns app.domain.frontend.expenses.routes.user
  "User-facing expense tracking routes.
   These routes are role-gated via tenant membership roles."
  (:require
    [app.domain.shared.routes.expenses-user :as expenses-user-routes]
    [app.template.frontend.routes.controllers :as controllers]))

(def ^:private route-options-by-id
  {:expenses-dashboard
   {:name :expenses-dashboard
    :view :expenses-dashboard
    :controllers (controllers/user-guarded-start :page/init-expenses-dashboard)}

   :user-dashboard
   {:name :user-dashboard
    :view :expenses-dashboard
    :controllers (controllers/user-guarded-start :page/init-expenses-dashboard)}

   :unmapped-items
   {:name :unmapped-items
    :view :unmapped-items
    :controllers (controllers/user-guarded-start :page/init-unmapped-items)}

   :expenses-dashboard-alias
   {:name :expenses-dashboard-alias
    :view :expenses-dashboard
    :controllers (controllers/user-guarded-start :page/init-expenses-dashboard)}

   :expenses-list
   {:name :expenses-list
    :view :expenses-list
    :controllers (controllers/user-guarded-start :page/init-expenses-list)}

  :expense-utility-new
  {:name :expense-utility-new
   :view :expense-utility-new
   :controllers (controllers/user-guarded-start :page/init-expense-utility-new)}

   :expense-upload
   {:name :expense-upload
    :view :expense-upload
    :controllers (controllers/user-guarded-start :page/init-expense-upload)}

   :receipts
   {:name :receipts
    :view :receipts-list
    :controllers (controllers/power-user-guarded-start :page/init-receipts-list)}

   :receipt-detail
   {:name :receipt-detail
    :view :receipt-detail
    :parameters {:path {:receipt-id string?}}
    :controllers (controllers/power-user-guarded-start :page/init-receipt-detail)}

   :expense-reports
   {:name :expense-reports
    :view :expense-reports
    :controllers (controllers/user-guarded-start :page/init-expense-reports)}

   :user-profile
   {:name :user-profile
    :view :user-profile
    :controllers (controllers/user-guarded-start :page/init-user-profile)}

   :expense-suppliers
   {:name :expense-suppliers
    :view :expense-suppliers
    :controllers (controllers/user-guarded-start :page/init-expense-suppliers)}

   :expense-payers
   {:name :expense-payers
    :view :expense-payers
    :controllers (controllers/user-guarded-start :page/init-expense-payers)}

  :expense-contexts
  {:name :expense-contexts
   :view :expense-contexts
   :controllers (controllers/user-guarded-start :page/init-expense-contexts)}

   :expense-stores
   {:name :expense-stores
    :view :expense-stores
    :controllers (controllers/user-guarded-start :page/init-expense-stores)}

   :expense-store-aliases
   {:name :expense-store-aliases
    :view :expense-store-aliases
    :controllers (controllers/user-guarded-start :page/init-expense-store-aliases)}

   :expense-items
   {:name :expense-items
    :view :expense-items
    :controllers (controllers/user-guarded-start :page/init-expense-items)}

   :expense-articles
   {:name :expense-articles
    :view :expense-articles
    :controllers (controllers/user-guarded-start :page/init-expense-articles)}

   :expense-manufacturers
   {:name :expense-manufacturers
    :view :expense-manufacturers
    :controllers (controllers/user-guarded-start :page/init-expense-manufacturers)}

   :expense-categories
   {:name :expense-categories
    :view :expense-categories
    :controllers (controllers/user-guarded-start :page/init-expense-categories)}

   :expense-categories-catalog
   {:name :expense-categories-catalog
    :view :expense-categories-catalog
    :controllers (controllers/user-guarded-start :page/init-expense-categories-catalog)}

   :expense-cities
   {:name :expense-cities
    :view :expense-cities
    :controllers (controllers/user-guarded-start :page/init-expense-cities)}

   :expense-subcategories
   {:name :expense-subcategories
    :view :expense-subcategories
    :controllers (controllers/user-guarded-start :page/init-expense-subcategories)}

   :expense-article-aliases
   {:name :expense-article-aliases
    :view :expense-article-aliases
    :controllers (controllers/user-guarded-start :page/init-expense-article-aliases)}

   :expense-supplier-aliases
   {:name :expense-supplier-aliases
    :view :expense-supplier-aliases
    :controllers (controllers/user-guarded-start :page/init-expense-supplier-aliases)}

   :expense-search
   {:name :expense-search
    :view :expense-search
    :controllers (controllers/user-guarded-start :page/init-expense-search)}

   :expense-detail
   {:name :expense-detail
    :view :expense-detail
    :parameters {:path {:expense-id string?}}
    :controllers (controllers/user-guarded-start :page/init-expense-detail)}})

(defn- descriptor->route
  [{:keys [id path]}]
  (let [route-options (get route-options-by-id id)]
    (when-not route-options
      (throw (ex-info "Missing frontend route options for canonical descriptor"
               {:route-id id
                :path path})))
    [path route-options]))

(defn routes
  "Generate user-facing expense tracking routes from canonical descriptors."
  []
  (mapv descriptor->route expenses-user-routes/route-descriptors))
