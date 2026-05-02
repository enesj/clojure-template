(ns app.domain.frontend.expenses.events.user-expenses.reports.helpers
  "Shared data helpers for report filter state and fetch events."
  (:require
    [app.template.frontend.api.http :as http]
    [clojure.string :as str]))

(def reports-path [:user-expenses :reports])

(defn ->positive-int
  [value fallback]
  (let [parsed (cond
                 (number? value) value
                 (string? value) (js/parseInt value 10)
                 :else fallback)]
    (if (and (number? parsed)
          (not (js/isNaN parsed))
          (pos? parsed))
      (int parsed)
      fallback)))

(defn normalize-id
  [value]
  (let [v (some-> value str str/trim)]
    (when (seq v) v)))

(defn normalize-id-values
  [value]
  (let [values (cond
                 (nil? value) []
                 (sequential? value) value
                 :else [value])]
    (->> values
      (keep normalize-id)
      distinct
      vec)))

(defn normalize-id-filter
  [value]
  (let [values (normalize-id-values value)]
    (cond
      (empty? values) nil
      (= 1 (count values)) (first values)
      :else values)))

(defn normalize-month
  [value]
  (let [v (some-> value str str/trim)]
    (when (seq v) v)))

(defn default-report-filters
  []
  {:months-back 6
   :supplier-id nil
   :expense-category-id nil
    :expense-context-id nil
   :day-of-week nil
   :amount-bucket nil
   :selected-day nil})

(defn report-range-params
  [months-back]
  (let [months* (->positive-int months-back 6)
        now (js/Date.)
        from (js/Date. (.getTime now))]
    (if (= 1 months*)
      (.setUTCDate from (- (.getUTCDate from) 30))
      (do
        (.setUTCDate from 1)
        (.setUTCMonth from (- (.getUTCMonth from) months*))))
    (.setUTCHours from 0 0 0 0)
    {:from (.toISOString from)
     :to (.toISOString now)}))

(defn common-report-params
  [db]
  (let [{:keys [months-back
                supplier-id
                expense-category-id
                expense-context-id]} (get-in db (conj reports-path :filters))
        range-params (report-range-params months-back)
        supplier-id* (normalize-id-filter supplier-id)
        expense-category-id* (normalize-id-filter expense-category-id)
        expense-context-id* (normalize-id-filter expense-context-id)]
    (cond-> range-params
      supplier-id* (assoc :supplier_id supplier-id*)
      expense-category-id* (assoc :expense_category_id expense-category-id*)
      expense-context-id* (assoc :expense_context_id expense-context-id*))))

(defn finish-failure-message
  [error]
  (http/extract-error-message error))
