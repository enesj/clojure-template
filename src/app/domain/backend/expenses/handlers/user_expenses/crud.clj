(ns app.domain.backend.expenses.handlers.user-expenses.crud
  "CRUD handlers for user expenses."
  (:require
    [app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
    [app.domain.backend.expenses.services.user-expense-settings :as user-settings]
    [app.domain.backend.expenses.services.user-expenses :as user-expenses]
    [app.shared.model-naming :as model-naming]
    [cheshire.core :as json]
    [clojure.string :as str]
    [taoensso.timbre :as log]))

(defn- parse-decimal-param
  [raw]
  (let [value (some-> raw str str/trim not-empty)]
    (when value
      (try
        (bigdec value)
        (catch Exception _
          nil)))))

(defn list-expenses-handler
  "Handler factory for listing user's expenses."
  [db]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/expenses-read-roles "Role assignment required")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)]
          (try
            (let [params (:query-params request)
                  sort-opts (h/parse-sort-params params)
                  text-filters (h/extract-text-filter-params params
                                 [:supplier-display-name :store-display-name
                                  :expense-category-name :payer-label
                                  :expense-context-name
                                  :notes])
                  ;; Support both legacy :from/:to and new :purchased-at-from/:purchased-at-to
                  purchased-from (or (h/get-param params :purchased-at-from) (h/get-param params :from))
                  purchased-to (or (h/get-param params :purchased-at-to) (h/get-param params :to))
                  created-from (h/get-param params :created-at-from)
                  created-to (h/get-param params :created-at-to)
                  currency-filter (h/get-param params :currency)
                  total-amount-min (parse-decimal-param (h/get-param params :total-amount-min))
                  total-amount-max (parse-decimal-param (h/get-param params :total-amount-max))
                  expense-context-id (h/try-parse-uuid (h/get-param params :expense_context_id))
                  expense-context-missing? (or (true? (h/parse-boolean-param params :expense-context-missing))
                                             (true? (h/parse-boolean-param params :missing-expense-context)))
                  source-filter (some-> (h/get-param params :source) str str/trim not-empty)
                  ;; Date highlight support
                  highlight-field-raw (h/get-param params :highlight-date-field)
                  highlight-field (some-> highlight-field-raw model-naming/ensure-app-keyword)
                  highlight-timezone (h/get-param params :highlight-timezone)
                  highlight-column (when highlight-field
                                     (get user-expenses/highlight-date-columns highlight-field))
                  opts (cond-> (merge {:from purchased-from
                                       :to purchased-to
                                       :created-at-from created-from
                                       :created-at-to created-to
                                       :supplier-id (h/try-parse-uuid (h/get-param params :supplier_id))
                                       :payer-id (h/try-parse-uuid (h/get-param params :payer_id))
                                       :expense-context-id expense-context-id
                                       :expense-context-missing? expense-context-missing?
                                       :limit (or (some-> (h/get-param params :limit) parse-long) 50)
                                       :offset (or (some-> (h/get-param params :offset) parse-long) 0)}
                                 text-filters)
                         currency-filter (assoc :currency currency-filter)
                         (some? total-amount-min) (assoc :total-amount-min total-amount-min)
                         (some? total-amount-max) (assoc :total-amount-max total-amount-max)
                         source-filter (assoc :source source-filter)
                         (seq sort-opts) (merge sort-opts))
                  uid (when-not (h/tenant-elevated? request) user-id)
                  expenses (user-expenses/list-user-expenses db tenant-id uid opts)
                  total (user-expenses/count-user-expenses db tenant-id uid opts)
                  date-highlights (when (and highlight-column highlight-timezone)
                                    (try
                                      {highlight-field
                                       (user-expenses/highlight-user-expense-days
                                         db tenant-id uid
                                         (assoc opts
                                           :highlight-column highlight-column
                                           :highlight-timezone highlight-timezone
                                           :highlight-field highlight-field))}
                                      (catch Exception e
                                        (log/warn e "Failed to get date highlights for expenses")
                                        nil)))]
              (h/json-response (cond-> {:data expenses
                                        :total total
                                        :limit (:limit opts)
                                        :offset (:offset opts)}
                                 (map? date-highlights)
                                 (assoc :date-highlights date-highlights))))
            (catch Exception e
              (log/error e "Error listing user expenses"
                {:user-id user-id
                 :query-params (:query-params request)
                 :path-params (:path-params request)
                 :message (.getMessage e)})
              (h/json-response {:error "Failed to list expenses"} 500)))))
      (h/unauthorized-response))))

(defn get-expense-handler
  "Handler factory for getting a single user expense."
  [db]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/expenses-read-roles "Role assignment required")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)
              expense-id (or (h/try-parse-uuid (get-in request [:path-params :id]))
                           (h/try-parse-uuid (get-in request [:parameters :path :id])))]
          (if expense-id
            (try
              (let [uid (when-not (h/tenant-elevated? request) user-id)
                    expense (user-expenses/get-user-expense-with-items db tenant-id uid expense-id)]
                (if expense
                  (h/json-response {:data expense})
                  (h/not-found-response "Expense not found")))
              (catch Exception e
                (log/error e "Error getting user expense" {:expense-id expense-id})
                (h/json-response {:error "Failed to get expense"} 500)))
            (h/json-response {:error "Invalid expense ID"} 400))))
      (h/unauthorized-response))))

(defn create-expense-handler
  "Handler factory for creating a user expense.
   `app-config` is optional; when provided, enables exchange rate fetching
   for non-BAM currency expenses."
  [db & [app-config]]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/expenses-write-roles "Only members, admins, and owners can create expenses")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)]
          (try
            (let [body (or (:body-params request) (json/parse-string (slurp (:body request)) true))
                  default-expense-category-id (when-not (or (:expense_category_id body)
                                                          (:expense-category-id body))
                                                (user-settings/effective-default-expense-category-id db tenant-id user-id))
                  expense-data (cond-> (select-keys body [:supplier_id :store_id :payer_id :expense_category_id :expense_context_id :article_id :purchased_at :total_amount :currency :notes :receipt_id])
                                 (:expense-category-id body)
                                 (assoc :expense_category_id (:expense-category-id body))

                                 (:expense-context-id body)
                                 (assoc :expense_context_id (:expense-context-id body))

                                 default-expense-category-id
                                 (assoc :expense_category_id default-expense-category-id))
                  items (or (:items body) [])]
              (log/debug "Creating user expense" {:user-id user-id :tenant-id tenant-id :expense-data expense-data})
              (let [expense (user-expenses/create-user-expense! db tenant-id user-id expense-data items app-config)]
                ;; Sticky default: update default payer if it changed
                (try
                  (user-settings/update-sticky-default-payer! db tenant-id user-id (:payer_id expense-data))
                  (catch Exception e
                    (log/warn e "Failed to update sticky default payer")))
                (h/json-response {:data expense} 201)))
            (catch clojure.lang.ExceptionInfo e
              (log/warn "Validation error creating expense" {:error (ex-message e) :data (ex-data e)})
              (h/json-response {:error (ex-message e)} 400))
            (catch Exception e
              (log/error e "Error creating user expense")
              (h/json-response {:error "Failed to create expense"} 500)))))
      (h/unauthorized-response))))

(defn update-expense-handler
  "Handler factory for updating a user expense."
  [db]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/expenses-write-roles "Only members, admins, and owners can modify expenses")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)
              expense-id (or (h/try-parse-uuid (get-in request [:path-params :id]))
                           (h/try-parse-uuid (get-in request [:parameters :path :id])))]
          (if expense-id
            (try
              (let [body (h/read-body-params request)
                    updates (cond-> (select-keys body [:supplier_id :store_id :payer_id :expense_category_id :expense_context_id :purchased_at :total_amount :currency :notes :items])
                              (contains? body :expense-category-id)
                              (assoc :expense_category_id (:expense-category-id body))

                              (contains? body :expense-context-id)
                              (assoc :expense_context_id (:expense-context-id body)))]
                (if-let [expense (user-expenses/update-user-expense! db tenant-id user-id expense-id updates)]
                  (h/json-response {:data expense})
                  (h/not-found-response "Expense not found or access denied")))
              (catch clojure.lang.ExceptionInfo e
                (log/warn "Validation error updating expense" {:error (ex-message e) :data (ex-data e)})
                (h/json-response {:error (ex-message e)} 400))
              (catch Exception e
                ;; NOTE: Our dev timbre output fn does not always print throwable stack traces.
                ;; Include message + class explicitly so 500s are debuggable from logs.
                (log/error "Error updating user expense"
                  {:expense-id expense-id
                   :exception (str (class e))
                   :message (.getMessage e)})
                (h/json-response {:error "Failed to update expense"} 500)))
            (h/json-response {:error "Invalid expense ID"} 400))))
      (h/unauthorized-response))))
