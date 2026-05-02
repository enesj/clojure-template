(ns app.domain.frontend.expenses.subs.user-expenses
  "Subscriptions for user-facing expense dashboard data."
  (:require
    [app.template.frontend.db.paths :as paths]
    [re-frame.core :as rf]))

(rf/reg-sub
  :user-expenses/by-supplier
  (fn [db _]
    (get-in db [:user-expenses :by-supplier :data])))

;; Current expense detail
(rf/reg-sub
  :user-expenses/current-expense
  (fn [db _]
    (get-in db [:user-expenses :current-expense :data])))

(rf/reg-sub
  :user-expenses/current-expense-loading?
  (fn [db _]
    (get-in db [:user-expenses :current-expense :loading?])))

(rf/reg-sub
  :user-expenses/current-expense-error
  (fn [db _]
    (get-in db [:user-expenses :current-expense :error])))

;; Form state
(rf/reg-sub
  :user-expenses/form-loading?
  (fn [db _]
    (get-in db [:user-expenses :form :loading?])))

(rf/reg-sub
  :user-expenses/form-error
  (fn [db _]
    (get-in db [:user-expenses :form :error])))

;; Suppliers and payers for forms
(rf/reg-sub
  :user-expenses/suppliers
  (fn [db _]
    (get-in db [:user-expenses :suppliers :items])))

(rf/reg-sub
  :user-expenses/expense-categories
  (fn [db _]
    (get-in db [:user-expenses :expense-categories :items])))

(rf/reg-sub
  :user-expenses/expense-categories-loading?
  (fn [db _]
    (get-in db [:user-expenses :expense-categories :loading?])))

(rf/reg-sub
  :user-expenses/expense-contexts
  (fn [db _]
    (get-in db [:user-expenses :expense-contexts :items])))

(rf/reg-sub
  :user-expenses/expense-contexts-loading?
  (fn [db _]
    (get-in db [:user-expenses :expense-contexts :loading?])))

;; Supplier detail (used by the user suppliers modal)
(rf/reg-sub
  :user-expenses/supplier-detail
  (fn [db [_ supplier-id]]
    (get-in db [:user-expenses :suppliers :detail :by-id (some-> supplier-id str)])))

(rf/reg-sub
  :user-expenses/supplier-detail-loading?
  (fn [db _]
    (true? (get-in db [:user-expenses :suppliers :detail :loading?]))))

(rf/reg-sub
  :user-expenses/supplier-detail-error
  (fn [db _]
    (get-in db [:user-expenses :suppliers :detail :error])))

(rf/reg-sub
  :user-expenses/supplier-detail-expenses
  (fn [db _]
    (get-in db [:user-expenses :suppliers :detail :expenses])))

(rf/reg-sub
  :user-expenses/supplier-detail-article-aliases
  (fn [db _]
    (get-in db [:user-expenses :suppliers :detail :aliases])))

(rf/reg-sub
  :user-expenses/payers
  (fn [db _]
    (get-in db [:user-expenses :payers :items])))

(rf/reg-sub
  :user-expenses/payers-loading?
  (fn [db _]
    (get-in db [:user-expenses :payers :loading?])))

(rf/reg-sub
  :user-expenses/user-payer-id
  (fn [db _]
    (get-in db [:user-expenses :payers :user-payer-id])))

;; Upload state
(rf/reg-sub
  :user-expenses/upload-loading?
  (fn [db _]
    (get-in db [:user-expenses :upload :loading?])))

(rf/reg-sub
  :user-expenses/upload-error
  (fn [db _]
    (get-in db [:user-expenses :upload :error])))

(rf/reg-sub
  :user-expenses/upload-notice
  (fn [db _]
    (get-in db [:user-expenses :upload :notice])))

(rf/reg-sub
  :user-expenses/upload-batch
  (fn [db _]
    (get-in db [:user-expenses :upload :batch])))

(rf/reg-sub
  :user-expenses/upload-payer-id
  (fn [db _]
    (get-in db [:user-expenses :upload :payer-id])))

(rf/reg-sub
  :user-expenses/upload-expense-category-id
  (fn [db _]
    (get-in db [:user-expenses :upload :expense-category-id])))

(rf/reg-sub
  :user-expenses/upload-notes
  (fn [db _]
    (get-in db [:user-expenses :upload :notes])))

(rf/reg-sub
  :user-expenses/recent-receipts
  (fn [db _]
    (get-in db [:user-expenses :receipts :items])))

;; Receipts inbox
(rf/reg-sub
  :user-expenses/receipts
  (fn [db _]
    (get-in db [:user-expenses :receipts :items])))

(rf/reg-sub
  :user-expenses/show-purged-receipts?
  (fn [db _]
    (get-in db [:user-expenses :receipts :show-purged?] false)))

(rf/reg-sub
  :user-expenses/purged-receipts-total
  (fn [db _]
    (long (or (get-in db [:user-expenses :receipts :purged-total]) 0))))

(rf/reg-sub
  :user-expenses/receipts-error
  (fn [db _]
    (get-in db [:user-expenses :receipts :error])))

(rf/reg-sub
  :user-expenses/receipt
  (fn [db [_ receipt-id]]
    (get-in db [:user-expenses :receipts :by-id receipt-id])))

(rf/reg-sub
  :user-expenses/receipt-detail-loading?
  (fn [db _]
    (get-in db [:user-expenses :receipts :detail-loading?])))

(rf/reg-sub
  :user-expenses/receipt-action-loading?
  (fn [db _]
    (get-in db [:user-expenses :receipts :action-loading?])))

(rf/reg-sub
  :user-expenses/receipt-detail-modal-open?
  (fn [db _]
    (true? (get-in db [:user-expenses :receipts :detail-modal :open?]))))

(rf/reg-sub
  :user-expenses/receipt-detail-modal-id
  (fn [db _]
    (get-in db [:user-expenses :receipts :detail-modal :entity-id])))

(defn- reports-data
  [db report-key field]
  (get-in db [:user-expenses :reports report-key field]))

;; Reports filters
(rf/reg-sub
  :user-expenses/reports-filters
  (fn [db _]
    (get-in db [:user-expenses :reports :filters])))

(rf/reg-sub
  :user-expenses/report-summary
  (fn [db _]
    (or (reports-data db :summary :data) {})))

(rf/reg-sub
  :user-expenses/report-summary-loading?
  (fn [db _]
    (boolean (reports-data db :summary :loading?))))

(rf/reg-sub
  :user-expenses/report-filter-options
  (fn [db _]
    (reports-data db :filter-options :data)))

(rf/reg-sub
  :user-expenses/report-filter-options-loading?
  (fn [db _]
    (boolean (reports-data db :filter-options :loading?))))

;; Day-of-week report
(rf/reg-sub
  :user-expenses/report-day-of-week
  (fn [db _]
    (or (reports-data db :day-of-week :data) [])))

(rf/reg-sub
  :user-expenses/report-day-of-week-loading?
  (fn [db _]
    (boolean (reports-data db :day-of-week :loading?))))

(rf/reg-sub
  :user-expenses/report-day-of-week-error
  (fn [db _]
    (reports-data db :day-of-week :error)))

;; Size distribution report
(rf/reg-sub
  :user-expenses/report-size-distribution
  (fn [db _]
    (or (reports-data db :size-distribution :data) [])))

(rf/reg-sub
  :user-expenses/report-size-distribution-loading?
  (fn [db _]
    (boolean (reports-data db :size-distribution :loading?))))

(rf/reg-sub
  :user-expenses/report-size-distribution-error
  (fn [db _]
    (reports-data db :size-distribution :error)))

;; Daily heatmap report
(rf/reg-sub
  :user-expenses/report-daily-heatmap
  (fn [db _]
    (or (reports-data db :daily-heatmap :data) [])))

(rf/reg-sub
  :user-expenses/report-daily-heatmap-loading?
  (fn [db _]
    (boolean (reports-data db :daily-heatmap :loading?))))

(rf/reg-sub
  :user-expenses/report-daily-heatmap-error
  (fn [db _]
    (reports-data db :daily-heatmap :error)))

;; By-category report
(rf/reg-sub
  :user-expenses/report-by-category
  (fn [db _]
    (or (reports-data db :by-category :data) [])))

(rf/reg-sub
  :user-expenses/report-by-category-loading?
  (fn [db _]
    (boolean (reports-data db :by-category :loading?))))

(rf/reg-sub
  :user-expenses/report-by-category-error
  (fn [db _]
    (reports-data db :by-category :error)))

(defn- entity-items
  [db entity-type]
  (let [ids (get-in db (paths/entity-ids entity-type) [])
        data (get-in db (paths/entity-data entity-type) {})]
    (->> ids
      (keep #(get data %))
      vec)))

;; Stores and articles (synced to admin entity store by fetch events)
(rf/reg-sub
  :user-expenses/stores
  (fn [db _]
    (entity-items db :stores)))

(rf/reg-sub
  :user-expenses/articles
  (fn [db _]
    (entity-items db :articles)))

;; Search
(rf/reg-sub
  :user-expenses/search-query
  (fn [db _]
    (get-in db [:user-expenses :search :query])))

(rf/reg-sub
  :user-expenses/search-loading?
  (fn [db _]
    (get-in db [:user-expenses :search :loading?])))

(rf/reg-sub
  :user-expenses/search-results
  (fn [db _]
    (get-in db [:user-expenses :search :results])))

(rf/reg-sub
  :user-expenses/search-selected
  (fn [db _]
    (get-in db [:user-expenses :search :selected])))

(rf/reg-sub
  :user-expenses/search-related
  (fn [db _]
    (get-in db [:user-expenses :search :related])))

(rf/reg-sub
  :user-expenses/search-related-loading?
  (fn [db _]
    (get-in db [:user-expenses :search :related-loading?])))

;; Quick Add search (context-first /expenses/new workflow)
(rf/reg-sub
  :user-expenses/quick-add-search-results
  (fn [db [_ entity-type]]
    (get-in db [:user-expenses :quick-add-search entity-type :results] [])))

(rf/reg-sub
  :user-expenses/quick-add-search-loading?
  (fn [db [_ entity-type]]
    (boolean (get-in db [:user-expenses :quick-add-search entity-type :loading?]))))

(rf/reg-sub
  :user-expenses/quick-add-related
  (fn [db _]
    (get-in db [:user-expenses :quick-add-related]
      {:entity-type nil
       :entity-id nil
       :loading? false
       :related {}})))

(rf/reg-sub
  :user-expenses/quick-add-history
  (fn [db _]
    (get-in db [:user-expenses :quick-add-history]
      {:loading? false
       :loaded? false
       :stores []
       :articles []})))

(rf/reg-sub
  :user-expenses/cooccurring-articles
  (fn [db _]
    (get-in db [:user-expenses :cooccurring-articles :results] [])))

(rf/reg-sub
  :user-expenses/context-suggestions
  (fn [db _]
    (get-in db [:user-expenses :context-suggestions]
      {:loading? false :suppliers [] :stores [] :categories []})))

;; Per-supplier store pool — populated by
;; `app.domain.frontend.expenses.events.supplier-stores/fetch-stores-for-supplier`.
;; Used in the manual expense form to surface every store of the selected
;; supplier (not just stores the tenant has previously bought from).
(rf/reg-sub
  :user-expenses/supplier-stores-pool
  (fn [db [_ supplier-id]]
    (when supplier-id
      (get-in db [:supplier-stores :by-supplier supplier-id :stores]))))

