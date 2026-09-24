(ns app.domain.frontend.expenses.pages.user.expenses-dashboard
  "Workspace dashboard — tenant-wide expense overview with insights."
  (:require
    [app.domain.frontend.expenses.components.user-expense-form :refer [user-expense-add-form-modal]]
    [app.template.frontend.components.button :refer [button]]
    [app.template.frontend.components.modal-wrapper :refer [modal-wrapper]]
    [app.template.frontend.i18n :refer [use-t]]
    app.domain.frontend.expenses.subs.workspace-dashboard
    [re-frame.core :as rf]
    [uix.core :refer [$ defui use-state]]
    [uix.re-frame :refer [use-subscribe]]))

;; ========================================================================
;; Formatting helpers
;; ========================================================================

(defn- format-bam
  "Format a BAM amount with 2 decimal places."
  [amount]
  (if (some? amount)
    (str (.toFixed (js/Number amount) 2) " BAM")
    "--"))

(defn- format-number
  "Format a number with locale separators."
  [n]
  (if (some? n)
    (.toLocaleString (js/Number n) "bs-BA")
    "--"))

(defn- format-percent
  "Format a percentage with +/- sign."
  [pct]
  (when (some? pct)
    (let [n (js/Number pct)]
      (str (when (pos? n) "+") (.toFixed n 1) "%"))))

(defn- trend-direction
  "Return :up, :down, or :flat based on percentage."
  [pct]
  (let [n (or pct 0)]
    (cond
      (> n 1) :up
      (< n -1) :down
      :else :flat)))

(defn- trend-color [dir]
  (case dir
    :up "text-emerald-600"
    :down "text-red-600"
    "text-slate-400"))

(defn- trend-arrow [dir]
  (case dir
    :up "↑"
    :down "↓"
    "—"))

;; ========================================================================
;; Skeleton Components
;; ========================================================================

(defui skeleton-block [{:keys [class]}]
  ($ :div {:class (str "bg-slate-100 animate-pulse rounded " (or class "h-4 w-24"))}))

(defui card-skeleton []
  ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5 space-y-3"}
    ($ skeleton-block {:class "h-4 w-28"})
    ($ skeleton-block {:class "h-7 w-36"})
    ($ skeleton-block {:class "h-3 w-20"})))

;; ========================================================================
;; Summary Card
;; ========================================================================

(defui summary-card [{:keys [title expense-count total-spend-bam comparison-pct
                             inline-avg avg-label loading? t]}]
  (let [dir (trend-direction comparison-pct)]
    ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
      (if loading?
        ($ card-skeleton)
        ($ :<>
          ($ :p {:class "text-sm font-medium text-slate-500"} title)
          ($ :p {:class "text-2xl font-bold text-slate-900 mt-1"}
            (format-bam total-spend-bam))
          ($ :div {:class "flex items-center gap-3 mt-2"}
            ($ :span {:class "text-sm text-slate-600"}
              (t :dashboard/expenses-count (format-number expense-count)))
            (when comparison-pct
              ($ :span {:class (str "text-xs font-medium " (trend-color dir))}
                (str (trend-arrow dir) " " (format-percent comparison-pct)))))
          (when inline-avg
            ($ :p {:class "text-xs text-slate-400 mt-1"}
              (str (t :dashboard/avg-prefix) " " (format-bam inline-avg)
                (case avg-label
                  "daily" (str " " (t :dashboard/per-day))
                  "weekly" (str " " (t :dashboard/per-week))
                  "")))))))))

;; ========================================================================
;; Monthly Trend Bar Chart (CSS-only)
;; ========================================================================

(defui monthly-trend-chart [{:keys [data loading? t]}]
  (let [max-val (when (seq data) (apply max (map #(or (:total_bam %) 0) data)))
        bars (reverse (or data []))]
    ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
      ($ :p {:class "text-sm font-medium text-slate-500 mb-4"} (t :dashboard/monthly-trend))
      (if loading?
        ($ :div {:class "flex items-end gap-2 h-32"}
          (for [i (range 6)]
            ($ :div {:key i :class "flex-1 bg-slate-100 animate-pulse rounded-t"
                     :style {:height (str (* 20 (inc (mod i 3))) "%")}})))
        ($ :div {:class "flex items-end gap-2 h-32"}
          (for [{:keys [month_label total_bam expense_count]} bars]
            (let [pct (if (and max-val (pos? max-val))
                        (* 100 (/ (or total_bam 0) max-val))
                        0)]
              ($ :div {:key month_label :class "flex-1 flex flex-col items-center"}
                ($ :div {:class "text-xs text-slate-500 mb-1"}
                  (format-bam total_bam))
                ($ :div {:class "w-full bg-blue-500 rounded-t hover:bg-blue-600 transition-colors min-h-[2px]"
                         :style {:height (str (max 2 pct) "%")}
                         :title (str month_label ": " (format-bam total_bam)
                                  " (" (t :dashboard/expenses-count expense_count) ")")})
                ($ :div {:class "text-xs text-slate-500 mt-1 font-medium"}
                  month_label)))))))))

;; ========================================================================
;; Top 3 Ranked List Card
;; ========================================================================

(defui ranked-list-card [{:keys [title items loading? empty-text render-item]}]
  ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
    ($ :p {:class "text-sm font-medium text-slate-500 mb-3"} title)
    (cond
      loading?
      ($ :div {:class "space-y-3"}
        (for [i (range 3)]
          ($ :div {:key i :class "flex items-center gap-3"}
            ($ skeleton-block {:class "h-6 w-6 rounded-full"})
            ($ :div {:class "flex-1 space-y-1"}
              ($ skeleton-block {:class "h-4 w-32"})
              ($ skeleton-block {:class "h-3 w-20"})))))

      (seq items)
      ($ :div {:class "space-y-3"}
        (for [[idx item] (map-indexed vector items)]
          ($ :div {:key idx :class "flex items-center gap-3"}
            ($ :div {:class "w-6 h-6 rounded-full bg-blue-100 text-blue-700 text-xs font-bold flex items-center justify-center flex-shrink-0"}
              (str (inc idx)))
            (render-item item))))

      :else
      ($ :p {:class "text-sm text-slate-400"} empty-text))))

(defui supplier-store-item [{:keys [item t]}]
  ($ :div {:class "flex-1 min-w-0"}
    ($ :p {:class "text-sm font-medium text-slate-900 truncate"} (:display_name item))
    ($ :div {:class "flex gap-2 text-xs text-slate-500"}
      ($ :span (format-bam (:total_spend_bam item)))
      ($ :span "·")
      ($ :span (t :dashboard/expenses-count (:expense_count item))))))

(defui article-item [{:keys [item t]}]
  ($ :div {:class "flex-1 min-w-0"}
    ($ :p {:class "text-sm font-medium text-slate-900 truncate"} (:canonical_name item))
    ($ :div {:class "flex gap-2 text-xs text-slate-500"}
      ($ :span (t :dashboard/times-purchased (:purchase_count item)))
      (when (:avg_unit_price item)
        ($ :<>
          ($ :span "·")
          ($ :span (str (t :dashboard/avg-prefix) " " (.toFixed (js/Number (:avg_unit_price item)) 2)
                     " " (or (:currency item) "BAM"))))))))

;; ========================================================================
;; Price Changes Widget
;; ========================================================================

(defui price-changes-card [{:keys [items loading? t]}]
  ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
    ($ :p {:class "text-sm font-medium text-slate-500 mb-3"} (t :dashboard/price-changes))
    (cond
      loading?
      ($ :div {:class "space-y-3"}
        (for [i (range 3)]
          ($ :div {:key i :class "flex justify-between"}
            ($ skeleton-block {:class "h-4 w-32"})
            ($ skeleton-block {:class "h-4 w-16"}))))

      (seq items)
      ($ :div {:class "space-y-3"}
        (for [{:keys [canonical_name change_percent direction currency
                      baseline_avg recent_avg]} items]
          (let [dir (if (= direction "up") :up :down)]
            ($ :div {:key canonical_name :class "flex items-center justify-between"}
              ($ :div {:class "min-w-0 flex-1"}
                ($ :p {:class "text-sm font-medium text-slate-900 truncate"} canonical_name)
                ($ :p {:class "text-xs text-slate-400"}
                  (str (.toFixed (js/Number (or baseline_avg 0)) 2)
                    " → " (.toFixed (js/Number (or recent_avg 0)) 2)
                    " " (or currency "BAM"))))
              ($ :span {:class (str "text-sm font-semibold ml-2 " (trend-color dir))}
                (str (trend-arrow dir) " "
                  (when (pos? (js/Number (or change_percent 0))) "+")
                  (.toFixed (js/Number (or change_percent 0)) 1) "%"))))))

      :else
      ($ :p {:class "text-sm text-slate-400"} (t :dashboard/no-price-data)))))

;; ========================================================================
;; Category Breakdown Widget
;; ========================================================================

(defui category-breakdown-card [{:keys [items loading? t]}]
  ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
    ($ :p {:class "text-sm font-medium text-slate-500 mb-3"} (t :dashboard/category-breakdown))
    (cond
      loading?
      ($ :div {:class "space-y-3"}
        (for [i (range 3)]
          ($ :div {:key i :class "space-y-1"}
            ($ skeleton-block {:class "h-4 w-32"})
            ($ skeleton-block {:class "h-3 w-full"}))))

      (seq items)
      ($ :div {:class "space-y-3"}
        (for [{:keys [category_name total_spend_bam percentage expense_count]} items]
          ($ :div {:key category_name}
            ($ :div {:class "flex justify-between text-sm mb-1"}
              ($ :span {:class "font-medium text-slate-700 truncate"} category_name)
              ($ :span {:class "text-slate-500 ml-2 flex-shrink-0"}
                (str (format-bam total_spend_bam) " (" expense_count ")")))
            ($ :div {:class "w-full bg-slate-100 rounded-full h-2"}
              ($ :div {:class "bg-blue-500 rounded-full h-2 transition-all"
                       :style {:width (str (or percentage 0) "%")}})))))

      :else
      ($ :p {:class "text-sm text-slate-400"} (t :dashboard/no-category-data)))))

;; ========================================================================
;; Biggest Expense Highlight
;; ========================================================================

(defui biggest-expense-card [{:keys [data loading? t]}]
  ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
    ($ :p {:class "text-sm font-medium text-slate-500 mb-3"}
      (t :dashboard/biggest-expense))
    (cond
      loading?
      ($ :div {:class "space-y-2"}
        ($ skeleton-block {:class "h-6 w-28"})
        ($ skeleton-block {:class "h-4 w-40"}))

      data
      ($ :div
        ($ :p {:class "text-2xl font-bold text-slate-900"}
          (format-bam (:bam_amount data)))
        (when (not= "BAM" (:currency data))
          ($ :p {:class "text-xs text-slate-400"}
            (str (.toFixed (js/Number (:total_amount data)) 2) " " (:currency data))))
        ($ :p {:class "text-sm text-slate-600 mt-1"}
          (str (or (:supplier_name data) "")
            (when (and (:supplier_name data) (:store_name data)) " — ")
            (or (:store_name data) "")))
        (when (:purchased_at data)
          ($ :p {:class "text-xs text-slate-400 mt-1"}
            (some-> (:purchased_at data) (.substring 0 10)))))

      :else
      ($ :p {:class "text-sm text-slate-400"}
        (t :dashboard/no-expenses-this-month)))))

;; ========================================================================
;; Spending Averages Card
;; ========================================================================

(defui averages-card [{:keys [data loading? t]}]
  ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
    ($ :p {:class "text-sm font-medium text-slate-500 mb-3"} (t :dashboard/spending-averages))
    (if loading?
      ($ :div {:class "space-y-3"}
        (for [i (range 3)]
          ($ :div {:key i :class "flex justify-between"}
            ($ skeleton-block {:class "h-4 w-20"})
            ($ skeleton-block {:class "h-4 w-24"}))))
      ($ :div {:class "space-y-3"}
        ($ :div {:class "flex justify-between"}
          ($ :span {:class "text-sm text-slate-600"} (t :dashboard/daily-30d))
          ($ :span {:class "text-sm font-semibold text-slate-900"}
            (format-bam (:daily_avg_30d data))))
        ($ :div {:class "flex justify-between"}
          ($ :span {:class "text-sm text-slate-600"} (t :dashboard/weekly-30d))
          ($ :span {:class "text-sm font-semibold text-slate-900"}
            (format-bam (:weekly_avg_30d data))))
        ($ :div {:class "flex justify-between"}
          ($ :span {:class "text-sm text-slate-600"} (t :dashboard/monthly-6m))
          ($ :span {:class "text-sm font-semibold text-slate-900"}
            (format-bam (:monthly_avg_6m data))))))))

;; ========================================================================
;; Team Overview Card
;; ========================================================================

(defui team-card [{:keys [data loading? t]}]
  ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
    ($ :div {:class "flex items-center justify-between mb-3"}
      ($ :p {:class "text-sm font-medium text-slate-500"} (t :dashboard/team))
      ($ :a {:href "/tenant/members"
             :class "text-xs text-blue-600 hover:text-blue-700 font-medium"}
        (t :dashboard/manage-team)))
    (if loading?
      ($ :div {:class "space-y-2"}
        ($ skeleton-block {:class "h-6 w-20"})
        ($ skeleton-block {:class "h-4 w-36"}))
      ($ :div
        ($ :p {:class "text-2xl font-bold text-slate-900"}
          (str (:total-members data) " " (t :dashboard/members)))
        (when (:tenant-name data)
          ($ :p {:class "text-sm text-slate-600 mt-1"} (:tenant-name data)))
        (when (seq (:role-breakdown data))
          ($ :div {:class "flex gap-2 mt-2 flex-wrap"}
            (for [{:keys [role count]} (:role-breakdown data)]
              ($ :span {:key role
                        :class "ds-badge ds-badge-sm ds-badge-ghost"}
                (str count " " role)))))))))

;; ========================================================================
;; Admin Alerts Card
;; ========================================================================

(defui admin-alerts-card [{:keys [unmapped-count loading? t]}]
  ($ :div {:class "bg-white rounded-xl shadow-sm border border-slate-100 p-5"}
    ($ :p {:class "text-sm font-medium text-slate-500 mb-3"} (t :dashboard/admin-alerts))
    (if loading?
      ($ :div {:class "space-y-3"}
        ($ skeleton-block {:class "h-4 w-32"})
        ($ skeleton-block {:class "h-4 w-28"}))
      ($ :div {:class "space-y-3"}
        (when (some? unmapped-count)
          ($ :button {:class "flex items-center justify-between w-full hover:bg-slate-50 rounded p-1 -m-1 transition-colors"
                      :on-click #(rf/dispatch [:navigate-to "/unmapped-items"])}
            ($ :span {:class "text-sm text-slate-600"}
              (t :dashboard/unmapped-aliases))
            ($ :span {:class (str "text-sm font-semibold "
                               (if (pos? unmapped-count) "text-amber-600" "text-emerald-600"))}
              unmapped-count)))))))

;; ========================================================================
;; Shortcuts Row (formerly "Brze radnje")
;; ========================================================================

(defui shortcut-btn [{:keys [id label icon on-click badge]}]
  ($ :button {:id id
              :class "flex items-center gap-2 px-4 py-3 bg-white rounded-xl shadow-sm border border-slate-100 hover:shadow-md hover:border-slate-200 transition-all text-sm font-medium text-slate-700"
              :on-click on-click}
    ($ :span icon)
    ($ :span label)
    (when badge
      ($ :span {:class "ds-badge ds-badge-xs ds-badge-warning ml-1"} badge))))

;; ========================================================================
;; Main Dashboard Page
;; ========================================================================

(defui expenses-dashboard-page []
  (let [t (use-t)
        user (use-subscribe [:current-user])
        power-user? (boolean (use-subscribe [:expenses/power-user?]))
        can-upload? (boolean (use-subscribe [:expenses/can? :expenses/upload]))
        can-add-expense? (boolean (use-subscribe [:expenses/can? :expenses/expense.write]))
      can-manage-expense-categories? (boolean (use-subscribe [:expenses/can? :expenses/expense-categories.manage]))
      can-manage-expense-contexts? (boolean (use-subscribe [:expenses/can? :expenses/expense-contexts.manage]))
        [show-quick-add? set-show-quick-add!] (use-state false)
        user-name (or (:full-name user) "there")
        loading? (boolean (use-subscribe [:workspace-dashboard/loading?]))
        error (use-subscribe [:workspace-dashboard/error])
        ;; Widget data
        last-30d (use-subscribe [:workspace-dashboard/last-30-days])
        last-6m (use-subscribe [:workspace-dashboard/last-6-months])
        trend (use-subscribe [:workspace-dashboard/monthly-trend])
        top-suppliers (use-subscribe [:workspace-dashboard/top-suppliers])
        top-stores (use-subscribe [:workspace-dashboard/top-stores])
        top-articles (use-subscribe [:workspace-dashboard/top-articles])
        price-changes (use-subscribe [:workspace-dashboard/price-changes])
        categories (use-subscribe [:workspace-dashboard/category-breakdown])
        biggest (use-subscribe [:workspace-dashboard/biggest-expense])
        averages (use-subscribe [:workspace-dashboard/averages])
        team (use-subscribe [:workspace-dashboard/team])
        unmapped-count (use-subscribe [:workspace-dashboard/unmapped-alias-count])
        ;; Compute comparison %
        compute-pct (fn [summary]
                      (let [curr (:total_spend_bam summary)
                            prev (:prev_spend_bam summary)]
                        (when (and curr prev (pos? prev))
                          (* 100 (/ (- curr prev) prev)))))]

    ($ :div {:class "min-h-screen bg-slate-50"}
      ;; Header
      ($ :header {:class "bg-white border-b border-slate-200"}
        ($ :div {:class "max-w-6xl mx-auto px-4 py-4 sm:py-6"}
          ($ :div {:class "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4"}
            ($ :div
              ($ :h1 {:class "text-xl sm:text-2xl font-bold text-slate-900"}
                (t :dashboard/greeting user-name))
              ($ :p {:class "text-sm text-slate-600"} (t :dashboard/subtitle)))
            ($ :div {:class "flex gap-2"}
              (when can-upload?
                ($ button {:id "btn-expenses-dashboard-upload-receipt"
                           :btn-type :primary
                           :on-click #(rf/dispatch [:navigate-to "/expenses/upload"])}
                  (t :dashboard/upload-receipt)))
              ($ button {:id "btn-expenses-dashboard-view-all"
                         :btn-type :ghost
                         :on-click #(rf/dispatch [:navigate-to "/expenses/list"])}
                (t :dashboard/view-all))))))

      ;; Error banner
      (when error
        ($ :div {:class "max-w-6xl mx-auto px-4 mt-4"}
          ($ :div {:class "bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg"}
            ($ :p {:class "text-sm font-medium"} error))))

      ;; Main content
      ($ :main {:class "max-w-6xl mx-auto px-4 py-6 space-y-6"}

        ;; Row 1: Summary Cards
        ($ :div {:class "grid grid-cols-1 sm:grid-cols-2 gap-4"}
          ($ summary-card {:title (t :dashboard/last-30-days)
                           :expense-count (:expense_count last-30d)
                           :total-spend-bam (:total_spend_bam last-30d)
                           :comparison-pct (compute-pct last-30d)
                           :inline-avg (when (and (:total_spend_bam last-30d)
                                               (pos? (or (:expense_count last-30d) 0)))
                                         (/ (:total_spend_bam last-30d) 30))
                           :avg-label "daily"
                           :loading? loading?
                           :t t})
          ($ summary-card {:title (t :dashboard/last-6-months)
                           :expense-count (:expense_count last-6m)
                           :total-spend-bam (:total_spend_bam last-6m)
                           :comparison-pct (compute-pct last-6m)
                           :inline-avg (when (and (:total_spend_bam last-6m)
                                               (pos? (or (:expense_count last-6m) 0)))
                                         (/ (:total_spend_bam last-6m) (/ 180 7)))
                           :avg-label "weekly"
                           :loading? loading?
                           :t t}))

        ;; Row 2: Monthly Trend
        ($ monthly-trend-chart {:data trend :loading? loading? :t t})

        ;; Row 3: Top 3 Rankings
        ($ :div {:class "grid grid-cols-1 md:grid-cols-3 gap-4"}
          ($ ranked-list-card
            {:title (t :dashboard/top-suppliers)
             :items top-suppliers
             :loading? loading?
             :empty-text (t :dashboard/no-suppliers)
             :render-item (fn [item] ($ supplier-store-item {:item item :t t}))})
          ($ ranked-list-card
            {:title (t :dashboard/top-stores)
             :items top-stores
             :loading? loading?
             :empty-text (t :dashboard/no-stores)
             :render-item (fn [item] ($ supplier-store-item {:item item :t t}))})
          ($ ranked-list-card
            {:title (t :dashboard/top-articles)
             :items top-articles
             :loading? loading?
             :empty-text (t :dashboard/no-articles)
             :render-item (fn [item] ($ article-item {:item item :t t}))}))

        ;; Row 4: Price Changes + Category Breakdown
        ($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
          ($ price-changes-card {:items price-changes :loading? loading? :t t})
          ($ category-breakdown-card {:items categories :loading? loading? :t t}))

        ;; Row 5: Averages + Biggest Expense
        ($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
          ($ averages-card {:data averages :loading? loading? :t t})
          ($ biggest-expense-card {:data biggest :loading? loading? :t t}))

        ;; Row 6: Team + Admin Alerts
        ($ :div {:class (str "grid grid-cols-1 gap-4 "
                          (if power-user? "md:grid-cols-2" ""))}
          ($ team-card {:data team :loading? loading? :t t})
          (when power-user?
            ($ admin-alerts-card {:unmapped-count unmapped-count
                                  :loading? loading?
                                  :t t})))

        ;; Row 7: Shortcuts
        ($ :div
          ($ :p {:class "text-sm font-medium text-slate-500 mb-3"} (t :dashboard/shortcuts))
          ($ :div {:class "flex flex-wrap gap-3"}
            (when can-upload?
              ($ shortcut-btn {:id "btn-shortcut-upload"
                               :label (t :dashboard/upload-receipt-short)
                               :icon "📷"
                               :on-click #(rf/dispatch [:navigate-to "/expenses/upload"])}))
            (when can-add-expense?
              ($ shortcut-btn {:id "btn-shortcut-add-expense"
                               :label (t :dashboard/add-expense)
                               :icon "✏️"
                               :on-click #(set-show-quick-add! true)}))
            (when can-add-expense?
              ($ shortcut-btn {:id "btn-shortcut-add-utility"
                               :label (t :dashboard/add-utility)
                               :icon "💡"
                               :on-click #(rf/dispatch [:navigate-to "/expenses/add-utility"])}))
            (when can-manage-expense-categories?
              ($ shortcut-btn {:id "btn-shortcut-expense-categories"
                               :label (t :expense-categories/title)
                               :icon "🗂️"
                               :on-click #(rf/dispatch [:navigate-to "/expense-categories"])}))
            (when can-manage-expense-contexts?
              ($ shortcut-btn {:id "btn-shortcut-expense-contexts"
                               :label (t :expense-contexts/title)
                               :icon "🏷️"
                               :on-click #(rf/dispatch [:navigate-to "/expense-contexts"])}))
            ($ shortcut-btn {:id "btn-shortcut-reports"
                             :label (t :dashboard/view-reports)
                             :icon "📊"
                             :on-click #(rf/dispatch [:navigate-to "/expenses/reports"])})
            (when power-user?
              ($ shortcut-btn {:id "btn-shortcut-unmapped"
                               :label (t :dashboard/unmapped-aliases)
                               :icon "🧩"
                               :on-click #(rf/dispatch [:navigate-to "/unmapped-items"])
                               :badge (when (and unmapped-count (pos? unmapped-count))
                                        (str unmapped-count))})))))

      ;; Quick-add expense modal
      (when show-quick-add?
        ($ modal-wrapper
          {:id "quick-add-expense-modal"
           :visible? true
           :title (t :dashboard/add-expense)
           :size :large
           :on-close #(set-show-quick-add! false)
           :close-button-id "btn-close-quick-add-expense"}
          ($ user-expense-add-form-modal
            {:on-success (fn []
                           (set-show-quick-add! false)
                           (rf/dispatch [:workspace-dashboard/init]))
             :on-cancel #(set-show-quick-add! false)}))))))

