(ns app.domain.frontend.expenses.events.user-expenses.reports.state
  "Report filter state management events.

  Simplified after moving global-entity reports to admin."
  (:require
    [app.domain.frontend.expenses.events.user-expenses.reports.helpers :as h]
    [app.template.frontend.db.db :refer [common-interceptors]]
    [re-frame.core :as rf]))

(def ^:private refresh-filter-keys
  #{:months-back
    :supplier-id
    :expense-category-id
    :expense-context-id})

(defn- normalize-filter-value
  [k value]
  (case k
    :months-back (h/->positive-int value 6)
    :supplier-id (h/normalize-id-filter value)
    :expense-category-id (h/normalize-id-filter value)
    :expense-context-id (h/normalize-id-filter value)
    :day-of-week (some-> (h/->positive-int value nil) int)
    :amount-bucket (h/normalize-id value)
    :selected-day (h/normalize-month value)
    value))

(rf/reg-event-fx
  :user-expenses/init-reports
  common-interceptors
  (fn [{:keys [db]} _]
    (let [defaults (h/default-report-filters)
          existing (or (get-in db (conj h/reports-path :filters)) {})
          filters (merge defaults (into {} (remove (comp nil? val) existing)))]
      {:db (assoc-in db (conj h/reports-path :filters) filters)
      :dispatch-n [[:user-expenses/fetch-expense-categories]
              [:user-expenses/fetch-expense-contexts]
                    [:user-expenses/reports-refresh]]})))

(rf/reg-event-fx
  :user-expenses/reports-refresh
  common-interceptors
  (fn [{:keys [db]} _]
    (let [months-back (h/->positive-int (get-in db (conj h/reports-path :filters :months-back)) 6)]
      {:dispatch-n [[:user-expenses/fetch-report-summary]
                    [:user-expenses/fetch-by-month {:months-back months-back}]
                    [:user-expenses/fetch-by-supplier {:limit 25}]
                    [:user-expenses/fetch-report-filter-options]
                    [:user-expenses/fetch-report-day-of-week]
                    [:user-expenses/fetch-report-size-distribution]
                    [:user-expenses/fetch-report-daily-heatmap]
                    [:user-expenses/fetch-report-by-category]]})))

(rf/reg-event-fx
  :user-expenses/reports-set-filter
  common-interceptors
  (fn [{:keys [db]} [k value]]
    (let [value* (normalize-filter-value k value)
          db* (assoc-in db (conj h/reports-path :filters k) value*)]
      (cond-> {:db db*}
        (contains? refresh-filter-keys k) (assoc :dispatch [:user-expenses/reports-refresh])))))

(rf/reg-event-db
  :user-expenses/reports-toggle-day-of-week
  common-interceptors
  (fn [db [iso-day]]
    (let [day* (some-> (h/->positive-int iso-day nil) int)
          current (get-in db (conj h/reports-path :filters :day-of-week))
          next-value (when-not (= current day*) day*)]
      (assoc-in db (conj h/reports-path :filters :day-of-week) next-value))))

(rf/reg-event-db
  :user-expenses/reports-toggle-amount-bucket
  common-interceptors
  (fn [db [bucket-key]]
    (let [bucket* (h/normalize-id bucket-key)
          current (get-in db (conj h/reports-path :filters :amount-bucket))
          next-value (when-not (= current bucket*) bucket*)]
      (assoc-in db (conj h/reports-path :filters :amount-bucket) next-value))))

(rf/reg-event-db
  :user-expenses/reports-toggle-selected-day
  common-interceptors
  (fn [db [day]]
    (let [day* (h/normalize-month day)
          current (get-in db (conj h/reports-path :filters :selected-day))
          next-value (when-not (= current day*) day*)]
      (assoc-in db (conj h/reports-path :filters :selected-day) next-value))))

(rf/reg-event-fx
  :user-expenses/reports-clear-local-filters
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (-> db
           (assoc-in (conj h/reports-path :filters :supplier-id) nil)
           (assoc-in (conj h/reports-path :filters :expense-category-id) nil)
           (assoc-in (conj h/reports-path :filters :expense-context-id) nil)
           (assoc-in (conj h/reports-path :filters :day-of-week) nil)
           (assoc-in (conj h/reports-path :filters :amount-bucket) nil)
           (assoc-in (conj h/reports-path :filters :selected-day) nil))
     :dispatch [:user-expenses/reports-refresh]}))
