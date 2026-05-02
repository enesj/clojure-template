(ns app.domain.frontend.expenses.events.user-expenses.endpoints
  "API endpoints for user-facing expense operations."
  (:require
    [app.template.frontend.api :as api]))

(def dashboard-endpoint (api/versioned-endpoint "/expenses/dashboard"))
(def list-endpoint (api/versioned-endpoint "/expenses"))
(def expense-detail-endpoint (api/versioned-endpoint "/expenses"))

(def reports-endpoint (api/versioned-endpoint "/expenses/reports"))
(def summary-endpoint (str reports-endpoint "/summary"))
(def by-month-endpoint (str reports-endpoint "/by-month"))
(def by-supplier-endpoint (str reports-endpoint "/by-supplier"))
(def reports-day-of-week-endpoint (str reports-endpoint "/day-of-week"))
(def reports-size-distribution-endpoint (str reports-endpoint "/size-distribution"))
(def reports-daily-heatmap-endpoint (str reports-endpoint "/daily-heatmap"))
(def reports-by-category-endpoint (str reports-endpoint "/by-category"))

(def reports-filter-options-endpoint (str reports-endpoint "/filter-options"))

(def expense-contexts-endpoint (api/versioned-endpoint "/expenses/expense-contexts"))

(def suppliers-endpoint (api/versioned-endpoint "/expenses/suppliers"))

(def stores-endpoint (api/versioned-endpoint "/expenses/stores"))

(def stores-batch-endpoint (str stores-endpoint "/batch"))
(def store-aliases-endpoint (api/versioned-endpoint "/expenses/store-aliases"))
(def payers-endpoint (api/versioned-endpoint "/expenses/payers"))
(def expense-items-endpoint (api/versioned-endpoint "/expenses/expense-items"))
(def article-aliases-endpoint (api/versioned-endpoint "/expenses/article-aliases"))
(def supplier-aliases-endpoint (api/versioned-endpoint "/expenses/supplier-aliases"))

(def profile-endpoint (api/versioned-endpoint "/profile"))
(def profile-defaults-endpoint (api/versioned-endpoint "/profile/defaults"))
(def profile-export-endpoint (api/versioned-endpoint "/profile/export"))
(def profile-delete-all-endpoint (api/versioned-endpoint "/profile/all"))
(def tenant-settings-endpoint (api/versioned-endpoint "/tenant/settings"))
(def tenant-name-endpoint (api/versioned-endpoint "/tenant/name"))
(def upload-endpoint (api/versioned-endpoint "/expenses/upload"))

(def receipts-endpoint (api/versioned-endpoint "/expenses/receipts"))

;; Articles (role-gated in backend)
(def articles-endpoint (api/versioned-endpoint "/expenses/articles"))

;; Manufacturers (role-gated in backend)
(def manufacturers-endpoint (api/versioned-endpoint "/expenses/manufacturers"))

(def categories-endpoint (api/versioned-endpoint "/expenses/categories"))
(def expense-categories-endpoint (api/versioned-endpoint "/expenses/expense-categories"))
(def cities-endpoint (api/versioned-endpoint "/expenses/cities"))
(def subcategories-endpoint (api/versioned-endpoint "/expenses/subcategories"))
(def articles-unmapped-aliases-endpoint (api/versioned-endpoint "/expenses/articles/unmapped-aliases"))

;; Quick add search (context-first /expenses/new workflow)
(def quick-add-search-endpoint (api/versioned-endpoint "/expenses/quick-add-search"))
(def quick-add-history-endpoint (api/versioned-endpoint "/expenses/quick-add-history"))

(def related-search-endpoint (str list-endpoint "/search/related"))

(def cooccurring-articles-endpoint (api/versioned-endpoint "/expenses/quick-add-cooccurring"))

(def context-suggestions-endpoint (api/versioned-endpoint "/expenses/quick-add-context-suggestions"))

