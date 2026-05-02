(ns app.domain.frontend.expenses.pages
  "Domain pages aggregator - provides page component mapping for routes.

  This namespace centralizes the mapping of route :view keywords to
  their corresponding page components, allowing the template to
  dynamically render domain pages without hardcoded imports."
  (:require
    [app.domain.frontend.expenses.pages.user.article-aliases :refer [article-aliases-page]]
    [app.domain.frontend.expenses.pages.user.articles :refer [articles-page]]
    [app.domain.frontend.expenses.pages.user.categories :refer [categories-page]]
    [app.domain.frontend.expenses.pages.user.cities :refer [cities-page]]
    [app.domain.frontend.expenses.pages.user.expense-detail :refer [expense-detail-page]]
    [app.domain.frontend.expenses.pages.user.expense-contexts :refer [expense-contexts-page]]
    [app.domain.frontend.expenses.pages.user.expense-categories :refer [expense-categories-page]]
    [app.domain.frontend.expenses.pages.user.expense-items :refer [expense-items-page]]
    [app.domain.frontend.expenses.pages.user.expense-reports :refer [expense-reports-page]]
    [app.domain.frontend.expenses.pages.user.expense-upload :refer [expense-upload-page]]
    [app.domain.frontend.expenses.pages.user.profile :refer [profile-page]]
    [app.domain.frontend.expenses.pages.user.expenses-dashboard :refer [expenses-dashboard-page]]
    [app.domain.frontend.expenses.pages.user.expenses-list :refer [expenses-list-page]]
    [app.domain.frontend.expenses.pages.user.manufacturers :refer [manufacturers-page]]
    [app.domain.frontend.expenses.pages.user.payers :refer [payers-page]]

    [app.domain.frontend.expenses.pages.user.receipt-detail :refer [receipt-detail-page]]
    [app.domain.frontend.expenses.pages.user.receipts-list :refer [receipts-list-page]]
    [app.domain.frontend.expenses.pages.user.search :refer [search-page]]
    [app.domain.frontend.expenses.pages.user.store-aliases :refer [store-aliases-page]]
    [app.domain.frontend.expenses.pages.user.stores :refer [stores-page]]
    [app.domain.frontend.expenses.pages.user.subcategories :refer [subcategories-page]]
    [app.domain.frontend.expenses.pages.user.supplier-aliases :refer [supplier-aliases-page]]
    [app.domain.frontend.expenses.pages.user.suppliers :refer [suppliers-page]]
    [app.domain.frontend.expenses.pages.user.unmapped-items :refer [unmapped-items-page]]))

(def pages
  "Map of route :view keywords to page components.

  The keys must match the :view values in the domain routes (user.cljs).
  The template's current-page component uses this map to render domain pages."
  {:expenses-dashboard expenses-dashboard-page
   :expenses-list expenses-list-page
   :expense-upload expense-upload-page
   :receipts-list receipts-list-page
   :expense-detail expense-detail-page
   :receipt-detail receipt-detail-page
   :expense-reports expense-reports-page
   :user-profile profile-page
   :expense-suppliers suppliers-page
   :expense-payers payers-page
  :expense-contexts expense-contexts-page
   :expense-stores stores-page
   :expense-store-aliases store-aliases-page
   :expense-items expense-items-page
   :expense-articles articles-page
   :expense-manufacturers manufacturers-page
   :expense-categories categories-page
   :expense-categories-catalog expense-categories-page
   :expense-cities cities-page
   :expense-subcategories subcategories-page
   :expense-article-aliases article-aliases-page
   :expense-supplier-aliases supplier-aliases-page
   :expense-search search-page

   :unmapped-items unmapped-items-page})
