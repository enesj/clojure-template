(ns app.domain.backend.expenses.routes.user-api
  "User-facing (non-admin) expenses API routes.

   Mounted by the template API under /api/v1.
   Path prefix for this router is /expenses."
  (:require
    [app.domain.backend.expenses.handlers.receipt-upload :as receipt-upload]
    [app.domain.backend.expenses.handlers.search :as search]
    [app.domain.backend.expenses.handlers.user-articles :as user-articles]
    [app.domain.backend.expenses.handlers.user-categories :as user-categories]
    [app.domain.backend.expenses.handlers.user-cities :as user-cities]
     [app.domain.backend.expenses.handlers.user-expense-contexts :as user-expense-contexts]
    [app.domain.backend.expenses.handlers.user-expense-categories :as user-expense-categories]
    [app.domain.backend.expenses.handlers.user-expenses.article-aliases :as user-expenses-article-aliases]
    [app.domain.backend.expenses.handlers.user-expenses.batch :as user-expenses-batch]
    [app.domain.backend.expenses.handlers.user-expenses.crud :as user-expenses-crud]
    [app.domain.backend.expenses.handlers.user-expenses.expense-items :as user-expenses-expense-items]
    [app.domain.backend.expenses.handlers.user-expenses.reference-data :as user-expenses-reference-data]
    [app.domain.backend.expenses.handlers.user-expenses.reports :as user-expenses-reports]
    [app.domain.backend.expenses.handlers.user-expenses.supplier-aliases :as user-expenses-supplier-aliases]
    [app.domain.backend.expenses.handlers.user-expenses.supplier-detail :as supplier-detail]
    [app.domain.backend.expenses.handlers.user-expenses.summary :as user-expenses-summary]
    [app.domain.backend.expenses.handlers.user-dashboard :as user-dashboard]
    [app.domain.backend.expenses.handlers.user-manufacturers :as user-manufacturers]
	[app.domain.backend.expenses.handlers.user-recurring-expenses :as user-recurring-expenses]

    [app.domain.backend.expenses.handlers.user-receipts :as user-receipts]
    [app.domain.backend.expenses.handlers.user-store-aliases :as user-store-aliases]
    [app.domain.backend.expenses.handlers.user-stores :as user-stores]
    [app.domain.backend.expenses.handlers.user-subcategories :as user-subcategories]))

(defn routes
  "User expense routes (requires authenticated user).

  `wrap-user-authentication` is injected by the template to avoid domain -> template coupling.
  `app-config` is optional and used for OCR routes."
  [db wrap-user-authentication & [app-config]]
  ["/expenses"
   {:middleware [wrap-user-authentication]}

   ;; Cross-entity search + related records
   ["/search" {:get {:handler (search/user-search-handler db)}}]
   ["/search/related" {:get {:handler (search/user-related-handler db)}}]

   ;; Dedicated search for Quick Add expense context inputs
   ["/quick-add-search" {:get {:handler (search/quick-add-search-handler db)}}]
   ["/quick-add-history" {:get {:handler (search/quick-add-history-handler db)}}]
   ["/quick-add-cooccurring" {:get {:handler (search/cooccurring-articles-handler db)}}]
   ["/quick-add-context-suggestions" {:get {:handler (search/context-suggestions-handler db)}}]

   ;; Lightweight scored search for smart expense form (global catalog)
   ["/quick-search" {:get {:handler (search/quick-search-handler db)}}]

   ;; New workspace dashboard (single endpoint, all widgets)
   ["/dashboard" {:get {:handler (user-dashboard/dashboard-handler db)}}]

   ;; Tenant-scoped report endpoints (reports grouping by global entities are in admin API)
   ["/reports"
     ["/summary" {:get {:handler (user-expenses-summary/expense-summary-handler db)}}]
     ["/by-month" {:get {:handler (user-expenses-summary/spending-by-month-handler db)}}]
     ["/by-supplier" {:get {:handler (user-expenses-summary/spending-by-supplier-handler db)}}]
    ["/day-of-week" {:get {:handler (user-expenses-reports/day-of-week-spending-handler db)}}]
    ["/size-distribution" {:get {:handler (user-expenses-reports/expense-size-distribution-handler db)}}]
    ["/daily-heatmap" {:get {:handler (user-expenses-reports/daily-heatmap-handler db)}}]
    ["/by-category" {:get {:handler (user-expenses-reports/by-category-handler db)}}]
    ["/filter-options" {:get {:handler (user-expenses-reports/filter-options-handler db)}}]]

     ;; Expense contexts (admin/owner manage, reference-readable for members/viewers)
     ["/expense-contexts"
      ["" {:get {:handler (user-expense-contexts/list-expense-contexts-handler db)}
               :post {:handler (user-expense-contexts/create-expense-context-handler db)}}]

      ["/batch" {:delete {:handler (user-expense-contexts/batch-delete-expense-contexts-handler db)}}]

      ["/:id" {:put {:handler (user-expense-contexts/update-expense-context-handler db)}}]]

     ;; Reference data endpoints (suppliers, payers)

   ["/suppliers"
    {:get {:handler (user-expenses-reference-data/list-suppliers-handler db)}
     :post {:handler (user-expenses-reference-data/create-supplier-handler db)}}]

   ["/suppliers/batch"
    {:delete {:handler (user-expenses-reference-data/batch-delete-suppliers-handler db)}}]

   ["/suppliers/:id"
    {:get {:handler (user-expenses-reference-data/get-supplier-handler db)}
     :put {:handler (user-expenses-reference-data/update-supplier-handler db)}}]

   ;; Supplier detail related lists (used by user Suppliers "View Details" modal)
   ["/article-aliases" {:get {:handler (supplier-detail/list-article-aliases-handler db)}}]

   ;; Article aliases (power-user only)
   ["/article-aliases/batch"
    {:delete {:handler (user-expenses-article-aliases/batch-delete-article-aliases-handler db)}}]

   ["/article-aliases/:id"
    {:put {:handler (user-expenses-article-aliases/update-article-alias-handler db)}}]

   ;; Supplier aliases (admin/owner only)
   ["/supplier-aliases"
    {:get {:handler (user-expenses-supplier-aliases/list-supplier-aliases-handler db)}}]

   ["/supplier-aliases/batch"
    {:delete {:handler (user-expenses-supplier-aliases/batch-delete-supplier-aliases-handler db)}}]

   ["/supplier-aliases/:id"
    {:put {:handler (user-expenses-supplier-aliases/update-supplier-alias-handler db)}}]

   ;; Stores (admin/owner only)
   ["/stores" {:get {:handler (user-stores/list-stores-handler db)}
               :post {:handler (user-stores/create-store-handler db)}}]

   ["/stores/batch" {:delete {:handler (user-stores/batch-delete-stores-handler db)}}]

   ["/stores/:id" {:put {:handler (user-stores/update-store-handler db)}}]

   ;; Cities (admin/owner only)
   ["/cities"
    ["" {:get {:handler (user-cities/list-cities-handler db)}
         :post {:handler (user-cities/create-city-handler db)}}]

    ["/batch" {:delete {:handler (user-cities/batch-delete-cities-handler db)}}]

    ["/:id" {:put {:handler (user-cities/update-city-handler db)}}]]

   ;; Store aliases (admin/owner only)
   ["/store-aliases" {:get {:handler (user-store-aliases/list-store-aliases-handler db)}}]

   ["/store-aliases/batch"
    {:delete {:handler (user-store-aliases/batch-delete-store-aliases-handler db)}}]

   ["/store-aliases/:id" {:put {:handler (user-store-aliases/update-store-alias-handler db)}}]

   ["/payers"
    {:get {:handler (user-expenses-reference-data/list-payers-handler db)}
     :post {:handler (user-expenses-reference-data/create-payer-handler db)}}]

   ["/payers/batch"
    {:delete {:handler (user-expenses-reference-data/batch-delete-payers-handler db)}}]

   ["/payers/:id"
    {:put {:handler (user-expenses-reference-data/update-payer-handler db)}}]

   ;; Expense items (power-user only)
   ["/expense-items" {:get {:handler (user-expenses-expense-items/list-expense-items-handler db)}}]

   ["/expense-items/batch"
    {:delete {:handler (user-expenses-expense-items/batch-delete-expense-items-handler db)}}]

   ["/expense-items/:id"
    {:put {:handler (user-expenses-expense-items/update-expense-item-handler db)}}]

   ;; Receipt upload (creates a receipts row)
   ["/upload" {:post {:handler (receipt-upload/user-upload-handler db)}}]

   ;; Receipts inbox (review + approve + OCR)
   ["/receipts" {:get {:handler (user-receipts/list-receipts-handler db)}}]
   ["/receipts/:id/download" {:get {:handler (user-receipts/download-receipt-handler db)}}]
   ["/receipts/batch" {:delete {:handler (user-receipts/batch-delete-receipts-handler db)}}]

   ["/receipts/ocr" {:post {:handler (user-receipts/ocr-batch-receipts-handler db app-config)}}]
   ["/receipts/:id"
    {:get {:handler (user-receipts/get-receipt-handler db)}}]
   ["/receipts/:id/approve" {:post {:handler (user-receipts/approve-receipt-handler db)}}]
   ["/receipts/:id/review" {:post {:handler (user-receipts/save-receipt-review-handler db)}}]
   ["/receipts/:id/update-posted" {:post {:handler (user-receipts/update-posted-receipt-handler db)}}]
   ["/receipts/:id/ocr" {:post {:handler (user-receipts/ocr-single-receipt-handler db app-config)}}]

   ;; Manufacturers (admin/owner only)
   ["/manufacturers"
    ["" {:get {:handler (user-manufacturers/list-manufacturers-handler db)}
         :post {:handler (user-manufacturers/create-manufacturer-handler db)}}]

    ["/batch" {:delete {:handler (user-manufacturers/batch-delete-manufacturers-handler db)}}]

    ["/:id" {:put {:handler (user-manufacturers/update-manufacturer-handler db)}}]]

   ;; Categories (admin/owner only)
   ["/categories"
    ["" {:get {:handler (user-categories/list-categories-handler db)}
         :post {:handler (user-categories/create-category-handler db)}}]

    ["/batch" {:delete {:handler (user-categories/batch-delete-categories-handler db)}}]

    ["/:id" {:put {:handler (user-categories/update-category-handler db)}}]]

   ;; Expense Categories (admin/owner only)
   ["/expense-categories"
    ["" {:get {:handler (user-expense-categories/list-expense-categories-handler db)}
         :post {:handler (user-expense-categories/create-expense-category-handler db)}}]

    ["/batch" {:delete {:handler (user-expense-categories/batch-delete-expense-categories-handler db)}}]

    ["/:id" {:put {:handler (user-expense-categories/update-expense-category-handler db)}}]]

   ;; Subcategories (admin/owner only)
   ["/subcategories"
    ["" {:get {:handler (user-subcategories/list-subcategories-handler db)}
         :post {:handler (user-subcategories/create-subcategory-handler db)}}]

    ["/batch" {:delete {:handler (user-subcategories/batch-delete-subcategories-handler db)}}]

    ["/:id" {:put {:handler (user-subcategories/update-subcategory-handler db)}}]]

   ;; Articles + unmapped aliases (role-gated to admin/owner)
   ;; IMPORTANT: Must come before the "/:id" expense route.
   ["/articles"
    ["" {:get {:handler (user-articles/list-articles-handler db)}
         :post {:handler (user-articles/create-article-handler db)}}]

    ["/unmapped-aliases" {:get {:handler (user-articles/list-unmapped-aliases-handler db)}}]

    ["/aliases/:alias-id/map" {:post {:handler (user-articles/map-alias-to-article-handler db)}}]

    ["/:id/aliases" {:post {:handler (user-articles/batch-create-aliases-handler db)}}]

    ["/batch" {:delete {:handler (user-articles/batch-delete-articles-handler db)}}]

    ["/:id" {:put {:handler (user-articles/update-article-handler db)}}]]

   ;; Recurring expense templates and reminders (must come before expense "/:id")
     ["/expense-templates"
    ["" {:get {:handler (user-recurring-expenses/list-templates-handler db)}
         :post {:handler (user-recurring-expenses/create-template-handler db)}}]

    ["/:id" {:put {:handler (user-recurring-expenses/update-template-handler db)}}]
    ["/:id/archive" {:post {:handler (user-recurring-expenses/archive-template-handler db)}}]]

   ["/recurring-reminders"
    ["" {:get {:handler (user-recurring-expenses/list-reminders-handler db)}}]
    ["/generate" {:post {:handler (user-recurring-expenses/generate-reminders-handler db)}}]
    ["/:id/prefill" {:get {:handler (user-recurring-expenses/reminder-prefill-handler db)}}]
    ["/:id/skip" {:post {:handler (user-recurring-expenses/skip-reminder-handler db)}}]
    ["/:id/snooze" {:post {:handler (user-recurring-expenses/snooze-reminder-handler db)}}]
    ["/:id/record" {:post {:handler (user-recurring-expenses/record-reminder-handler db)}}]]

   ;; Expenses CRUD
   ["" {:get {:handler (user-expenses-crud/list-expenses-handler db)}
        :post {:handler (user-expenses-crud/create-expense-handler db app-config)}}]

   ;; Batch operations
   ["/batch" {:put {:handler (user-expenses-batch/batch-update-expenses-handler db)}
              :delete {:handler (user-expenses-batch/batch-delete-expenses-handler db)}}]

   ["/batch-delete" {:post {:handler (user-expenses-batch/batch-delete-expenses-handler db)}}]

   ["/:id" {:get {:handler (user-expenses-crud/get-expense-handler db)}
            :put {:handler (user-expenses-crud/update-expense-handler db)}}]])





