(ns app.domain.backend.expenses.handlers.user-expenses.reports
  "User-scoped reporting handlers for `/api/v1/expenses/reports/*`.

  Only reports that group by tenant-scoped fields remain here.
  Reports that group by global entities (suppliers, articles, categories)
  live in admin routes."
  (:require
    [app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
    [app.domain.backend.expenses.services.user-expense-reports.filters :as report-filters]
    [app.domain.backend.expenses.services.user-expense-reports.time :as report-time]
    [clojure.string :as str]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant LocalDate LocalDateTime OffsetDateTime ZoneId ZoneOffset]))

(def ^:private invalid ::invalid)

(defn- parse-instant-param
  [raw]
  (if (str/blank? (str (or raw "")))
    nil
    (or
      (try
        (Instant/parse (str raw))
        (catch Exception _ nil))
      (try
        (-> (OffsetDateTime/parse (str raw)) .toInstant)
        (catch Exception _ nil))
      (try
        (-> (LocalDateTime/parse (str raw))
          (.atZone (ZoneId/systemDefault))
          .toInstant)
        (catch Exception _ nil))
      (try
        (-> (LocalDate/parse (str raw))
          (.atStartOfDay ZoneOffset/UTC)
          .toInstant)
        (catch Exception _ nil))
      invalid)))

(defn- parse-uuid-param
  [params k]
  (let [raw (h/get-param params k)
        raw-values (cond
                     (nil? raw) []
                     (sequential? raw) raw
                     (string? raw) (str/split raw #",")
                     :else [raw])
        normalized-values (->> raw-values
                            (map #(some-> % str str/trim))
                            (remove str/blank?)
                            vec)]
    (if (empty? normalized-values)
      nil
      (let [parsed-values (mapv h/try-parse-uuid normalized-values)]
        (if (some nil? parsed-values)
          invalid
          (if (= 1 (count parsed-values))
            (first parsed-values)
            parsed-values))))))

(defn- parse-common-report-opts
  [params]
  (let [from (parse-instant-param (h/get-param params :from))
        to (parse-instant-param (h/get-param params :to))
        supplier-id (parse-uuid-param params :supplier_id)
        payer-id (parse-uuid-param params :payer_id)
        expense-category-id (parse-uuid-param params :expense_category_id)
        expense-context-id (parse-uuid-param params :expense_context_id)
        expense-context-missing? (or (true? (h/parse-boolean-param params :expense-context-missing))
                 (true? (h/parse-boolean-param params :missing-expense-context)))
        currency-raw (some-> (h/get-param params :currency) str str/trim str/upper-case)
        currency (when-not (str/blank? currency-raw) currency-raw)]
    (cond
      (= invalid from) {:error (h/json-response {:error "Invalid from date/time"} 400)}
      (= invalid to) {:error (h/json-response {:error "Invalid to date/time"} 400)}
      (= invalid supplier-id) {:error (h/json-response {:error "Invalid supplier_id"} 400)}
      (= invalid payer-id) {:error (h/json-response {:error "Invalid payer_id"} 400)}
      (= invalid expense-category-id) {:error (h/json-response {:error "Invalid expense_category_id"} 400)}
      (= invalid expense-context-id) {:error (h/json-response {:error "Invalid expense_context_id"} 400)}

      :else
      {:opts (cond-> {}
               from (assoc :from from)
               to (assoc :to to)
               supplier-id (assoc :supplier-id supplier-id)
               payer-id (assoc :payer-id payer-id)
               expense-category-id (assoc :expense-category-id expense-category-id)
               expense-context-id (assoc :expense-context-id expense-context-id)
               expense-context-missing? (assoc :expense-context-missing? true)
               currency (assoc :currency currency))})))

(defn- with-user-report-access
  [request on-success]
  (if-let [user-id (h/get-user-id request)]
    (if-let [forbidden (h/ensure-role request h/expenses-read-roles "Role assignment required")]
      forbidden
      (let [tenant-id (h/get-tenant-id request)]
        (on-success user-id tenant-id)))
    (h/unauthorized-response)))

;; ---------------------------------------------------------------------------
;; Day of week
;; ---------------------------------------------------------------------------

(defn day-of-week-spending-handler
  [db]
  (fn [request]
    (with-user-report-access
      request
      (fn [user-id tenant-id]
        (try
          (let [params (:query-params request)
                {:keys [error opts]} (parse-common-report-opts params)]
            (if error
              error
              (h/json-response {:data (report-time/day-of-week db user-id (assoc opts :tenant-id tenant-id))})))
          (catch Exception e
            (log/error e "Error getting day-of-week spending report"
              {:user-id user-id
               :query-params (:query-params request)
               :message (.getMessage e)})
            (h/json-response {:error "Failed to get day-of-week spending report"} 500)))))))

;; ---------------------------------------------------------------------------
;; Size distribution
;; ---------------------------------------------------------------------------

(defn expense-size-distribution-handler
  [db]
  (fn [request]
    (with-user-report-access
      request
      (fn [user-id tenant-id]
        (try
          (let [params (:query-params request)
                {:keys [error opts]} (parse-common-report-opts params)]
            (if error
              error
              (h/json-response {:data (report-time/size-distribution db user-id (assoc opts :tenant-id tenant-id))})))
          (catch Exception e
            (log/error e "Error getting expense size distribution report"
              {:user-id user-id
               :query-params (:query-params request)
               :message (.getMessage e)})
            (h/json-response {:error "Failed to get expense size distribution report"} 500)))))))

;; ---------------------------------------------------------------------------
;; Daily heatmap
;; ---------------------------------------------------------------------------

(defn daily-heatmap-handler
  [db]
  (fn [request]
    (with-user-report-access
      request
      (fn [user-id tenant-id]
        (try
          (let [params (:query-params request)
                {:keys [error opts]} (parse-common-report-opts params)]
            (if error
              error
              (h/json-response {:data (report-time/daily-heatmap db user-id (assoc opts :tenant-id tenant-id))})))
          (catch Exception e
            (log/error e "Error getting daily heatmap report"
              {:user-id user-id
               :query-params (:query-params request)
               :message (.getMessage e)})
            (h/json-response {:error "Failed to get daily heatmap report"} 500)))))))

;; ---------------------------------------------------------------------------
;; Filter options (utility — populates filter dropdowns)
;; ---------------------------------------------------------------------------

;; ---------------------------------------------------------------------------
;; By category
;; ---------------------------------------------------------------------------

(defn by-category-handler
  [db]
  (fn [request]
    (with-user-report-access
      request
      (fn [user-id tenant-id]
        (try
          (let [params (:query-params request)
                {:keys [error opts]} (parse-common-report-opts params)]
            (if error
              error
              (h/json-response {:data (report-time/by-category db user-id (assoc opts :tenant-id tenant-id))})))
          (catch Exception e
            (log/error e "Error getting by-category report"
              {:user-id user-id
               :query-params (:query-params request)
               :message (.getMessage e)})
            (h/json-response {:error "Failed to get by-category report"} 500)))))))

(defn filter-options-handler
  [db]
  (fn [request]
    (with-user-report-access
      request
      (fn [user-id tenant-id]
        (try
          (let [params (:query-params request)
                {:keys [error opts]} (parse-common-report-opts params)]
            (if error
              error
              (h/json-response {:data (report-filters/options db user-id (assoc opts :tenant-id tenant-id))})))
          (catch Exception e
            (log/error e "Error getting report filter options"
              {:user-id user-id
               :query-params (:query-params request)
               :message (.getMessage e)})
            (h/json-response {:error "Failed to get report filter options"} 500)))))))
