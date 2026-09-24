(ns app.domain.shared.routes.expenses-user
  "Canonical user-route descriptors for the expenses domain.

   These descriptors are the single source of truth for user-facing route paths
   across frontend route building and backend SPA fallbacks."
  (:require
    [app.domain.shared.routes.contract :as route-contract]))

(def route-descriptors
  [{:id :expenses-dashboard :path "/expenses" :spa-fallback? true}
   {:id :user-dashboard :path "/dashboard" :spa-fallback? true}
   {:id :unmapped-items :path "/unmapped-items" :spa-fallback? true}
   {:id :expenses-dashboard-alias :path "/expenses/dashboard" :spa-fallback? true}
   {:id :expenses-list :path "/expenses/list" :spa-fallback? true}
  {:id :expense-utility-new :path "/expenses/add-utility" :spa-fallback? true}
   {:id :expense-upload :path "/expenses/upload" :spa-fallback? true}
   {:id :receipts :path "/receipts" :spa-fallback? true}
   {:id :receipt-detail :path "/receipts/:receipt-id" :spa-fallback? true}
   {:id :expense-reports :path "/expenses/reports" :spa-fallback? true}
   {:id :user-profile :path "/profile" :spa-fallback? true}
   {:id :expense-suppliers :path "/suppliers" :spa-fallback? true}
   {:id :expense-payers :path "/payers" :spa-fallback? true}
  {:id :expense-contexts :path "/expense-contexts" :spa-fallback? true}
   {:id :expense-stores :path "/stores" :spa-fallback? true}
   {:id :expense-store-aliases :path "/store-aliases" :spa-fallback? true}
   {:id :expense-items :path "/expense-items" :spa-fallback? true}
   {:id :expense-articles :path "/articles" :spa-fallback? true}
   {:id :expense-manufacturers :path "/manufacturers" :spa-fallback? true}
   {:id :expense-categories :path "/categories" :spa-fallback? true}
   {:id :expense-categories-catalog :path "/expense-categories" :spa-fallback? true}
   {:id :expense-cities :path "/cities" :spa-fallback? true}
   {:id :expense-subcategories :path "/subcategories" :spa-fallback? true}
   {:id :expense-article-aliases :path "/article-aliases" :spa-fallback? true}
   {:id :expense-supplier-aliases :path "/supplier-aliases" :spa-fallback? true}
   {:id :expense-search :path "/search" :spa-fallback? true}

   ;; Keep parameterized route after literals to avoid accidental catches.
   {:id :expense-detail :path "/expenses/:expense-id" :spa-fallback? true}])

(def all-paths
  "All canonical descriptor paths in declaration order."
  (route-contract/descriptor-paths route-descriptors))

(def spa-fallback-paths
  "Canonical expenses SPA fallback paths for backend route composition."
  (route-contract/spa-fallback-paths route-descriptors))
