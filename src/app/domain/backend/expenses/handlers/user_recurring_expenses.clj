(ns app.domain.backend.expenses.handlers.user-recurring-expenses
	"User-facing recurring expense template and reminder endpoints."
	(:require
		[app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
		[app.domain.backend.expenses.services.expense-templates :as expense-templates]
		[app.domain.backend.expenses.services.recurring-expense-reminders :as reminders]
		[app.shared.model-naming :as model-naming]
		[clojure.string :as str]
		[taoensso.timbre :as log])
	(:import
		[java.time Instant LocalDate]))

(def ^:private template-fields
	[:name
	 :kind
	 :status
	 :default_expense_category_id
	 :default_expense_context_id
	 :default_supplier_id
	 :default_payer_id
	 :default_currency
	 :default_notes
	 :recurrence_frequency
	 :recurrence_interval
	 :next_due_date
	 :reminder_days_before])

(defn- blank->nil
	[value]
	(if (and (string? value) (str/blank? value))
		nil
		value))

(defn- enum-string
	[value]
	(some-> value blank->nil str str/trim str/lower-case))

(defn- currency-string
	[value]
	(some-> value blank->nil str str/trim str/upper-case))

(defn- positive-int-or
	[value fallback]
	(let [parsed (cond
								 (integer? value) value
								 (number? value) (int value)
								 (string? value) (try
																	 (Integer/parseInt (str/trim value))
																	 (catch Exception _ fallback))
								 :else fallback)]
		(if (pos? parsed) parsed fallback)))

(defn- parse-local-date
	[value]
	(cond
		(nil? value) nil
		(instance? LocalDate value) value
		:else (try
						(LocalDate/parse (str/trim (str value)))
						(catch Exception _ nil))))

(defn- parse-instant
	[value]
	(or (h/parse-instant-param value)
		(try
			(some-> value str str/trim Instant/parse)
			(catch Exception _ nil))))

(defn- normalize-template-payload
	[body tenant-id]
	(let [db-body (model-naming/app-map-keys->db (or body {}))
				payload (select-keys db-body template-fields)]
		(cond-> (-> payload
							(update :name #(some-> % blank->nil str str/trim))
							(update :kind enum-string)
							(update :status enum-string)
							(update :default_currency currency-string)
							(update :recurrence_frequency enum-string)
							(update :recurrence_interval #(when (some? %) (positive-int-or % 1)))
							(update :reminder_days_before #(if (some? %) (max 0 (positive-int-or % 0)) %))
							(update :next_due_date parse-local-date))
			tenant-id (assoc :tenant_id tenant-id))))

(defn list-templates-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-role request h/reference-data-read-roles "Role assignment required")]
				forbidden
				(try
					(let [qp (:query-params request)
								tenant-id (h/get-tenant-id request)
								limit (h/parse-page-limit qp 100)
								offset (h/parse-page-offset qp)
								search (or (h/get-param qp :search)
												 (h/get-param qp :name))
								sort-opts (h/parse-sort-params qp)
								kind (enum-string (h/get-param qp :kind))
								status (enum-string (h/get-param qp :status))
								opts (cond-> {:limit limit
															:offset offset
															:search search}
											 tenant-id (assoc :tenant-id tenant-id)
											 (seq sort-opts) (merge sort-opts)
											 kind (assoc :kind kind)
											 status (assoc :status status))
								rows (-> ((:list expense-templates/service) db opts) h/to-app vec)
								total (long (or ((:count expense-templates/service) db
																 (cond-> {:search search}
																	 tenant-id (assoc :tenant-id tenant-id)
																	 kind (assoc :kind kind)
																	 status (assoc :status status)))
															0))]
						(h/json-response {:data rows
															:total total
															:limit limit
															:offset offset}))
					(catch Exception e
						(log/error e "Failed to list expense templates" {:message (.getMessage e)})
						(h/json-response {:error "Failed to list expense templates"} 500)))))))

(defn create-template-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-admin-or-owner request)]
				forbidden
				(try
					(let [body (h/read-body-params request)
								tenant-id (h/get-tenant-id request)
								payload (normalize-template-payload body tenant-id)
								template (h/to-app ((:create! expense-templates/service) db payload))]
						(h/json-response {:data template} 201))
					(catch clojure.lang.ExceptionInfo e
						(h/json-response {:error (ex-message e)} (or (:status (ex-data e)) 400)))
					(catch Exception e
						(log/error e "Failed to create expense template" {:message (.getMessage e)})
						(h/json-response {:error "Failed to create expense template"} 500)))))))

(defn update-template-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-admin-or-owner request)]
				forbidden
				(let [template-id (h/parse-path-id request)]
					(if-not template-id
						(h/json-response {:error "Invalid expense template id"} 400)
						(try
							(let [tenant-id (h/get-tenant-id request)
										body (h/read-body-params request)
										updates (dissoc (normalize-template-payload body nil) :tenant_id)
										updated (some-> ((:update! expense-templates/service) db template-id updates
																			(cond-> {}
																				tenant-id (assoc :tenant-id tenant-id)))
															h/to-app)]
								(if updated
									(h/json-response {:data updated})
									(h/not-found-response "Expense template not found")))
							(catch clojure.lang.ExceptionInfo e
								(h/json-response {:error (ex-message e)} (or (:status (ex-data e)) 400)))
							(catch Exception e
								(log/error e "Failed to update expense template" {:message (.getMessage e)
																																	 :expense-template-id (str template-id)})
								(h/json-response {:error "Failed to update expense template"} 500)))))))))

(defn archive-template-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-admin-or-owner request)]
				forbidden
				(let [template-id (h/parse-path-id request)]
					(if-not template-id
						(h/json-response {:error "Invalid expense template id"} 400)
						(try
							(let [tenant-id (h/get-tenant-id request)
										updated (some-> ((:update! expense-templates/service) db template-id {:status "archived"}
																			(cond-> {}
																				tenant-id (assoc :tenant-id tenant-id)))
															h/to-app)]
								(if updated
									(h/json-response {:data updated})
									(h/not-found-response "Expense template not found")))
							(catch Exception e
								(log/error e "Failed to archive expense template" {:message (.getMessage e)
																																		:expense-template-id (str template-id)})
								(h/json-response {:error "Failed to archive expense template"} 500)))))))))

(defn generate-reminders-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-admin-or-owner request)]
				forbidden
				(try
					(let [body (h/read-body-params request)
								tenant-id (h/get-tenant-id request)
								today (parse-local-date (or (:today body) (:date body)))
								result (reminders/generate-due-reminders! db tenant-id (cond-> {}
																																					today (assoc :today today)))]
						(h/json-response {:data (h/to-app result)}))
					(catch Exception e
						(log/error e "Failed to generate recurring expense reminders" {:message (.getMessage e)})
						(h/json-response {:error "Failed to generate recurring expense reminders"} 500)))))))

(defn list-reminders-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-role request h/reference-data-read-roles "Role assignment required")]
				forbidden
				(try
					(let [qp (:query-params request)
								tenant-id (h/get-tenant-id request)
								limit (h/parse-page-limit qp 100)
								offset (h/parse-page-offset qp)
								status (enum-string (h/get-param qp :status))
								actionable? (if (some? (h/get-param qp :actionable))
															(true? (h/parse-boolean-param qp :actionable))
															(nil? status))
								rows (-> (reminders/list-tenant-reminders db tenant-id
													 {:limit limit
														:offset offset
														:status status
														:actionable? actionable?})
												 h/to-app
												 vec)]
						(h/json-response {:data rows
															:total (count rows)
															:limit limit
															:offset offset}))
					(catch Exception e
						(log/error e "Failed to list recurring expense reminders" {:message (.getMessage e)})
						(h/json-response {:error "Failed to list recurring expense reminders"} 500)))))))

(defn reminder-prefill-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-role request h/reference-data-read-roles "Role assignment required")]
				forbidden
				(let [reminder-id (h/parse-path-id request)]
					(if-not reminder-id
						(h/json-response {:error "Invalid recurring reminder id"} 400)
						(try
							(if-let [prefill (reminders/reminder-prefill db (h/get-tenant-id request) reminder-id)]
								(h/json-response {:data (h/to-app prefill)})
								(h/not-found-response "Recurring reminder not found"))
							(catch Exception e
								(log/error e "Failed to build recurring reminder prefill" {:message (.getMessage e)
																																						:recurring-reminder-id (str reminder-id)})
								(h/json-response {:error "Failed to build recurring reminder prefill"} 500)))))))))

(defn skip-reminder-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-role request h/expenses-write-roles "Role assignment required")]
				forbidden
				(let [reminder-id (h/parse-path-id request)]
					(if-not reminder-id
						(h/json-response {:error "Invalid recurring reminder id"} 400)
						(try
							(if-let [updated (reminders/skip-reminder! db (h/get-tenant-id request) reminder-id)]
								(h/json-response {:data (h/to-app updated)})
								(h/not-found-response "Recurring reminder not found"))
							(catch Exception e
								(log/error e "Failed to skip recurring reminder" {:message (.getMessage e)
																																	 :recurring-reminder-id (str reminder-id)})
								(h/json-response {:error "Failed to skip recurring reminder"} 500)))))))))

(defn snooze-reminder-handler
	[db]
	(fn [request]
		(cond
			(not (h/get-user request)) (h/unauthorized-response)
			:else
			(if-let [forbidden (h/ensure-role request h/expenses-write-roles "Role assignment required")]
				forbidden
				(let [reminder-id (h/parse-path-id request)
						body (h/read-body-params request)
						snoozed-until (parse-instant (or (:snoozed_until body)
																 (:snoozed-until body)
																 (:until body)))]
					(cond
						(not reminder-id)
						(h/json-response {:error "Invalid recurring reminder id"} 400)

						(not snoozed-until)
						(h/json-response {:error "snoozed_until is required"} 400)

						:else
						(try
							(if-let [updated (reminders/snooze-reminder! db (h/get-tenant-id request) reminder-id snoozed-until)]
								(h/json-response {:data (h/to-app updated)})
								(h/not-found-response "Recurring reminder not found"))
							(catch Exception e
								(log/error e "Failed to snooze recurring reminder" {:message (.getMessage e)
																							 :recurring-reminder-id (str reminder-id)})
								(h/json-response {:error "Failed to snooze recurring reminder"} 500)))))))))

(defn record-reminder-handler
	[db]
	(fn [request]
		(cond
			(not (h/get-user request)) (h/unauthorized-response)
			:else
			(if-let [forbidden (h/ensure-role request h/expenses-write-roles "Role assignment required")]
				forbidden
				(let [reminder-id (h/parse-path-id request)
						body (h/read-body-params request)
						recorded-expense-id (h/try-parse-uuid (or (:recorded_expense_id body)
																		(:recorded-expense-id body)
																		(:expense_id body)
																		(:expense-id body)))]
					(cond
						(not reminder-id)
						(h/json-response {:error "Invalid recurring reminder id"} 400)

						(not recorded-expense-id)
						(h/json-response {:error "recorded_expense_id is required"} 400)

						:else
						(try
							(if-let [updated (reminders/record-reminder! db (h/get-tenant-id request) reminder-id recorded-expense-id)]
								(h/json-response {:data (h/to-app updated)})
								(h/not-found-response "Recurring reminder not found"))
							(catch Exception e
								(log/error e "Failed to record recurring reminder" {:message (.getMessage e)
																							 :recurring-reminder-id (str reminder-id)})
								(h/json-response {:error "Failed to record recurring reminder"} 500)))))))))