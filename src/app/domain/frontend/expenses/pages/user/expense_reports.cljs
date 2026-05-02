(ns app.domain.frontend.expenses.pages.user.expense-reports
  "User-facing expense reports — tenant-scoped analytics.

  Reports grouping by global entities (suppliers, articles, categories)
  have been moved to admin. This page shows:
  - Summary stat cards
  - Day-of-week spending pattern
  - Expense size distribution
  - Daily calendar heatmap
  - Category/subcategory breakdown"
  (:require
    [app.domain.frontend.expenses.pages.user.expense-reports.components :refer [report-multi-select
                                                                                stat-card]]
    [app.domain.frontend.expenses.pages.user.expense-reports.sections.categories :refer [categories-tab]]
    [app.domain.frontend.expenses.pages.user.expense-reports.sections.expenses :refer [expenses-tab]]
    [app.domain.frontend.expenses.pages.user.expense-reports.utils :as report-utils]
    [app.template.frontend.components.button :refer [button]]
    [app.template.frontend.i18n :refer [use-t]]
    [clojure.string :as str]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui]]
    [uix.re-frame :refer [use-subscribe]]))

(defn- normalize-selected-ids
  [value]
  (let [values (cond
                 (nil? value) []
                 (sequential? value) value
                 :else [value])]
    (->> values
      (keep (fn [v]
              (let [s (some-> v str str/trim)]
                (when (seq s) s))))
      distinct
      vec)))

(defn- normalize-report-options
  [rows]
  (->> (or rows [])
    (keep (fn [row]
            (let [id (some-> (:id row) str str/trim)
                  name* (some-> (:name row) str str/trim)]
              (when (and (seq id) (seq name*))
                {:id id :name name*}))))
    (sort-by (comp str/lower-case :name))
    vec))

(defn- merge-options-with-selected
  [available-options fallback-options selected-ids]
  (let [fallback-by-id (into {} (map (juxt :id identity) (or fallback-options [])))
        selected-options (->> (or selected-ids [])
                           (keep fallback-by-id))]
    (->> (concat (or available-options []) selected-options)
      (reduce (fn [acc {:keys [id] :as option}]
                (if (contains? acc id)
                  acc
                  (assoc acc id option)))
        {})
      vals
      (sort-by (comp str/lower-case :name))
      vec)))

(defui expense-reports-page []
  (let [t (use-t)
        summary (or (use-subscribe [:user-expenses/report-summary]) {})
        summary-loading? (boolean (use-subscribe [:user-expenses/report-summary-loading?]))
        by-supplier (or (use-subscribe [:user-expenses/by-supplier]) [])
        expense-categories-data (or (use-subscribe [:user-expenses/expense-categories]) [])
        expense-contexts-data (or (use-subscribe [:user-expenses/expense-contexts]) [])
        template-expense-categories (or (use-subscribe [:app.template.frontend.subs.entity/entities :expense-categories]) [])
        template-expense-contexts (or (use-subscribe [:app.template.frontend.subs.entity/entities :expense-contexts]) [])

        reports-filters (or (use-subscribe [:user-expenses/reports-filters]) {})
        months-back (or (:months-back reports-filters) 6)
        supplier-filter (:supplier-id reports-filters)
        expense-category-filter (:expense-category-id reports-filters)
        expense-context-filter (:expense-context-id reports-filters)
        selected-supplier-ids (normalize-selected-ids supplier-filter)
        selected-expense-category-ids (normalize-selected-ids expense-category-filter)
        selected-expense-context-ids (normalize-selected-ids expense-context-filter)
        selected-supplier-id (first selected-supplier-ids)
        selected-day-of-week (:day-of-week reports-filters)
        selected-bucket-key (:amount-bucket reports-filters)
        selected-day (:selected-day reports-filters)
        has-global-filters? (or (seq selected-supplier-ids)
                              (seq selected-expense-category-ids)
                              (seq selected-expense-context-ids))

        day-of-week-data (or (use-subscribe [:user-expenses/report-day-of-week]) [])
        day-of-week-loading? (boolean (use-subscribe [:user-expenses/report-day-of-week-loading?]))
        day-of-week-error (use-subscribe [:user-expenses/report-day-of-week-error])

        size-distribution-data (or (use-subscribe [:user-expenses/report-size-distribution]) [])
        size-distribution-loading? (boolean (use-subscribe [:user-expenses/report-size-distribution-loading?]))
        size-distribution-error (use-subscribe [:user-expenses/report-size-distribution-error])

        daily-heatmap-data (or (use-subscribe [:user-expenses/report-daily-heatmap]) [])
        daily-heatmap-loading? (boolean (use-subscribe [:user-expenses/report-daily-heatmap-loading?]))
        daily-heatmap-error (use-subscribe [:user-expenses/report-daily-heatmap-error])

        by-category-data (or (use-subscribe [:user-expenses/report-by-category]) [])
        by-category-loading? (boolean (use-subscribe [:user-expenses/report-by-category-loading?]))
        by-category-error (use-subscribe [:user-expenses/report-by-category-error])

        report-filter-options (use-subscribe [:user-expenses/report-filter-options])
        report-filter-options-loading? (boolean (use-subscribe [:user-expenses/report-filter-options-loading?]))
        filter-options-ready? (and (not report-filter-options-loading?) (some? report-filter-options))

        suppliers-fallback* (report-utils/supplier-options by-supplier)
        expense-categories-fallback* (->> (if (seq expense-categories-data) expense-categories-data template-expense-categories)
                                       (keep (fn [row]
                                               (let [id (some-> (:id row) str)
                                                     name* (some-> (:name row) str str/trim)]
                                                 (when (and (seq id) (seq name*))
                                                   {:id id :name name*}))))
                                       (sort-by (comp str/lower-case :name))
                                       vec)
        expense-contexts-fallback* (->> (if (seq expense-contexts-data) expense-contexts-data template-expense-contexts)
                   (keep (fn [row]
                     (let [id (some-> (:id row) str)
                     name* (some-> (:name row) str str/trim)]
                       (when (and (seq id) (seq name*))
                   {:id id :name name*}))))
                   (sort-by (comp str/lower-case :name))
                   vec)

        available-suppliers* (normalize-report-options (:suppliers report-filter-options))
        available-expense-categories* (normalize-report-options (or (:expense-categories report-filter-options)
                                                                  (:expense_categories report-filter-options)))
        available-expense-contexts* (normalize-report-options (or (:expense-contexts report-filter-options)
                      (:expense_contexts report-filter-options)))

        suppliers* (if filter-options-ready?
                     (merge-options-with-selected available-suppliers* suppliers-fallback* selected-supplier-ids)
                     suppliers-fallback*)
        expense-categories* (if filter-options-ready?
                              (merge-options-with-selected available-expense-categories* expense-categories-fallback* selected-expense-category-ids)
                              expense-categories-fallback*)
        expense-contexts* (if filter-options-ready?
                (merge-options-with-selected available-expense-contexts* expense-contexts-fallback* selected-expense-context-ids)
                expense-contexts-fallback*)
        supplier-name-by-id (into {} (map (juxt :id :name) suppliers*))
        selected-supplier-name (cond
                                 (> (count selected-supplier-ids) 1) (t :expense-reports/n-suppliers (count selected-supplier-ids))
                                 selected-supplier-id (or (get supplier-name-by-id (some-> selected-supplier-id str))
                                                        (t :expense-reports/selected-supplier))
                                 :else (t :expense-reports/all-suppliers-scope))

        day-pattern (report-utils/aggregate-day-pattern day-of-week-data)
        day-pattern-max (apply max (cons 0 (map #(or (report-utils/->number (:total_amount %)) 0) day-pattern)))

        size-buckets (report-utils/aggregate-size-buckets size-distribution-data)
        size-buckets-max (apply max (cons 0 (map #(or (report-utils/->number (:total_amount %)) 0) size-buckets)))

        heatmap-rows-raw (report-utils/aggregate-heatmap daily-heatmap-data)
        heatmap-rows (if selected-day-of-week
                       (->> heatmap-rows-raw
                         (filter #(= selected-day-of-week (:iso_day_of_week %)))
                         vec)
                       heatmap-rows-raw)
        heatmap-visible (->> heatmap-rows (take-last 84) vec)
        heatmap-max (apply max (cons 0 (map #(or (report-utils/->number (:total_amount %)) 0) heatmap-visible)))
        selected-heatmap-day (first (filter #(= selected-day (:day %)) heatmap-visible))

        category-data (report-utils/aggregate-by-category by-category-data)

        currency-totals (or (:currency-totals summary) {})
        primary-currency-key (or
                               (some #(when (contains? currency-totals %) %) [:BAM :USD :EUR "BAM" "USD" "EUR"])
                               (first (keys currency-totals)))
        primary-currency (if (keyword? primary-currency-key)
                           (name primary-currency-key)
                           (some-> primary-currency-key str))
        total-sum (reduce + 0 (map #(or (report-utils/->number %) 0) (vals currency-totals)))
        total-expenses (or (report-utils/->number (:total-expenses summary)) 0)
        average-spend (when (pos? total-expenses) (/ total-sum total-expenses))]

    ($ :div {:class "min-h-screen bg-gradient-to-b from-base-100 via-primary/5 to-secondary/10 pb-12"}
      ($ :header {:class "bg-white/80 backdrop-blur-xl border-b border-base-200 sticky top-0 z-30 shadow-sm transition-all"}
        ($ :div {:class "max-w-7xl mx-auto px-4 sm:px-6 py-4"}
          ($ :div {:class "flex flex-col xl:flex-row xl:items-start xl:justify-between gap-6"}
            ($ :div {:class "flex-shrink-0 space-y-1"}
              ($ :div {:class "flex items-center gap-2 mb-1"}
                ($ :span {:class "w-2 h-2 rounded-full bg-primary animate-pulse"})
                ($ :span {:class "text-xs font-bold text-primary uppercase tracking-widest"} (t :expense-reports/analytics-badge)))
              ($ :h1 {:class "text-2xl sm:text-3xl font-black text-base-content tracking-tight"} (t :expense-reports/title))
              ($ :p {:class "text-sm text-base-content/60 max-w-md font-medium"}
                (t :expense-reports/subtitle)))

            ($ :div {:class "w-full rounded-2xl border border-primary/20 bg-gradient-to-br from-white via-primary/5 to-secondary/10 p-4 sm:p-6 space-y-4 shadow-sm backdrop-blur-sm"}
              ($ :div {:class "flex items-center justify-between mb-3"}
                ($ :div {:class "flex items-center gap-2"}
                  ($ :span {:class "text-xs font-extrabold uppercase text-base-content/40 tracking-wider"} (t :expense-reports/global-filters))
                  (when has-global-filters?
                    ($ :span {:class "ds-badge bg-primary/10 text-primary border-primary/20 font-bold ds-badge-xs uppercase tracking-wide"} (t :expense-reports/active))))

                ($ :div {:class "flex items-center gap-2"}
                  ($ button {:id "btn-reports-refresh"
                             :btn-type :ghost
                             :size :sm
                             :class "text-base-content/60 hover:text-primary hover:bg-primary/5 font-medium transition-colors"
                             :on-click #(rf/dispatch [:user-expenses/reports-refresh])}
                    (t :expense-reports/refresh-data))

                  (when has-global-filters?
                    ($ button {:id "btn-reports-clear-local-filters"
                               :btn-type :ghost
                               :size :sm
                               :class "text-error/70 hover:text-error hover:bg-error/10 font-medium transition-colors"
                               :on-click #(rf/dispatch [:user-expenses/reports-clear-local-filters])}
                      (t :expense-reports/clear-filters)))))

              ($ :div {:class "grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3"}
                ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-[10px] font-bold text-base-content/50 uppercase tracking-widest px-0.5" :for "reports-filter-months-back"} (t :expense-reports/filter-time-range))
                  ($ :select {:id "reports-filter-months-back"
                              :class "ds-select ds-select-sm ds-select-bordered w-full bg-white font-medium focus:border-primary focus:ring-2 focus:ring-primary/20 transition-all rounded-lg text-xs shadow-sm"
                              :value months-back
                              :on-change #(rf/dispatch [:user-expenses/reports-set-filter
                                                        :months-back
                                                        (js/parseInt (.. % -target -value) 10)])}
                    ($ :option {:value 1} (t :expense-reports/time-30d))
                    ($ :option {:value 3} (t :expense-reports/time-3mo))
                    ($ :option {:value 6} (t :expense-reports/time-6mo))
                    ($ :option {:value 12} (t :expense-reports/time-12mo))
                    ($ :option {:value 24} (t :expense-reports/time-24mo))))

                ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-[10px] font-bold text-base-content/50 uppercase tracking-widest px-0.5"
                             :for "reports-filter-supplier-toggle"}
                    (t :expense-reports/filter-supplier))
                  ($ report-multi-select {:id "reports-filter-supplier"
                                          :field-label (t :expense-reports/filter-supplier)
                                          :all-label (t :expense-reports/all-suppliers)
                                          :options suppliers*
                                          :selected-ids selected-supplier-ids
                                          :on-change #(rf/dispatch [:user-expenses/reports-set-filter
                                                                    :supplier-id
                                                                    %])}))

                ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-[10px] font-bold text-base-content/50 uppercase tracking-widest px-0.5"
                             :for "reports-filter-expense-category-toggle"}
                    (t :expense-reports/filter-expense-type))
                  ($ report-multi-select {:id "reports-filter-expense-category"
                                          :field-label (t :expense-reports/filter-expense-type)
                                          :all-label (t :expense-reports/all-expense-types)
                                          :options expense-categories*
                                          :selected-ids selected-expense-category-ids
                                          :on-change #(rf/dispatch [:user-expenses/reports-set-filter
                                                                    :expense-category-id
                                                                    %])}))

                ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-[10px] font-bold text-base-content/50 uppercase tracking-widest px-0.5"
                             :for "reports-filter-expense-context-toggle"}
                    (t :expense-reports/filter-expense-context))
                  ($ report-multi-select {:id "reports-filter-expense-context"
                                          :field-label (t :expense-reports/filter-expense-context)
                                          :all-label (t :expense-reports/all-expense-contexts)
                                          :options expense-contexts*
                                          :selected-ids selected-expense-context-ids
                                          :on-change #(rf/dispatch [:user-expenses/reports-set-filter
                                                                    :expense-context-id
                                                                    %])})))))))

      ($ :main {:class "max-w-7xl mx-auto px-4 sm:px-6 py-8 space-y-8"}
        ($ :div {:class "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6"}
          ($ stat-card {:title (t :expense-reports/stat-total-spent)
                        :value (report-utils/format-money total-sum primary-currency)
                        :subtitle (t :expense-reports/stat-total-desc)
                        :icon "💰"
                        :loading? summary-loading?})
          ($ stat-card {:title (t :expense-reports/stat-volume)
                        :value (report-utils/format-int total-expenses)
                        :subtitle (t :expense-reports/stat-volume-desc)
                        :icon "📋"
                        :loading? summary-loading?})
          ($ stat-card {:title (t :expense-reports/stat-average)
                        :value (if average-spend (report-utils/format-money average-spend primary-currency) "—")
                        :subtitle (t :expense-reports/stat-average-desc)
                        :icon "📊"
                        :loading? summary-loading?})
          ($ stat-card {:title (t :expense-reports/stat-scope)
                        :value selected-supplier-name
                        :subtitle (t :expense-reports/stat-scope-desc)
                        :icon "🏪"
                        :loading? false}))

        ($ expenses-tab {:day-of-week-loading? day-of-week-loading?
                         :day-of-week-error day-of-week-error
                         :day-pattern day-pattern
                         :selected-day-of-week selected-day-of-week
                         :day-pattern-max day-pattern-max
                         :primary-currency primary-currency
                         :size-distribution-loading? size-distribution-loading?
                         :size-distribution-error size-distribution-error
                         :size-buckets size-buckets
                         :selected-bucket-key selected-bucket-key
                         :size-buckets-max size-buckets-max
                         :daily-heatmap-loading? daily-heatmap-loading?
                         :daily-heatmap-error daily-heatmap-error
                         :heatmap-visible heatmap-visible
                         :heatmap-max heatmap-max
                         :selected-day selected-day
                         :selected-heatmap-day selected-heatmap-day})

        ($ categories-tab {:by-category-loading? by-category-loading?
                           :by-category-error by-category-error
                           :category-data category-data
                           :primary-currency primary-currency})))))
