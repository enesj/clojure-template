(ns app.domain.backend.expenses.handlers.user-expense-contexts
	"User-facing expense context endpoints.

	Expense contexts are tenant-scoped labels such as a property, project, or
	utility context used for filtering and reporting normal expenses. Management
	is limited to admin/owner roles from the main app."
	(:require
		[app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
		[app.domain.backend.expenses.services.expense-contexts :as expense-contexts]
		[clojure.string :as str]
		[taoensso.timbre :as log]))

(defn- bool-param
	[body & keys]
	(reduce
		(fn [_ k]
			(if (contains? body k)
				(reduced (boolean (get body k)))
				nil))
		nil
		keys))

(defn- contains-any?
	[m keys]
	(boolean (some #(contains? m %) keys)))

(defn- context-payload
	[body tenant-id]
	(let [name-provided? (contains-any? body [:name])
				description-provided? (contains-any? body [:description])
				active-provided? (contains-any? body [:is_active :is-active :isActive])
				active-value (bool-param body :is_active :is-active :isActive)]
		(cond-> {}
			tenant-id (assoc :tenant_id tenant-id)
			name-provided? (assoc :name (some-> (:name body) str str/trim))
			description-provided? (assoc :description (:description body))
			active-provided? (assoc :is_active active-value))))

(defn list-expense-contexts-handler
	"List tenant expense contexts for user pages and selectors.

	By default only active contexts are returned. Management pages can pass
	`include_inactive=true` to include archived/inactive contexts."
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
								include-inactive? (true? (h/parse-boolean-param qp :include_inactive))
								is-active (h/parse-boolean-param qp :is_active)
								created-at-from (h/parse-instant-param (h/get-param qp :created-at-from))
								created-at-to (h/parse-instant-param (h/get-param qp :created-at-to))
								extra-filters (cond-> []
																(and (not include-inactive?) (nil? is-active))
																(conj [:= :is_active true])
																(some? is-active)
																(conj [:= :is_active is-active])
																created-at-from
																(conj [:>= :created_at created-at-from])
																created-at-to
																(conj [:<= :created_at created-at-to]))
								opts (cond-> {:limit limit
															:offset offset
															:search search}
											 tenant-id (assoc :tenant-id tenant-id)
											 (seq sort-opts) (merge sort-opts)
											 (seq extra-filters) (assoc :extra-filters extra-filters))
								rows (-> ((:list expense-contexts/service) db opts)
											 h/to-app
											 vec)
								count-opts (cond-> {:search search}
														 tenant-id (assoc :tenant-id tenant-id)
														 (seq extra-filters) (assoc :extra-filters extra-filters))
								total (long (or ((:count expense-contexts/service) db count-opts) 0))]
						(h/json-response {:data rows
															:total total
															:limit limit
															:offset offset}))
					(catch Exception e
						(log/error e "Failed to list expense contexts" {:message (.getMessage e)})
						(h/json-response {:error "Failed to list expense contexts"} 500)))))))

(defn create-expense-context-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-admin-or-owner request)]
				forbidden
				(try
					(let [body (h/read-body-params request)
								tenant-id (h/get-tenant-id request)
								payload (context-payload body tenant-id)
								context (h/to-app ((:create! expense-contexts/service) db payload))]
						(h/json-response {:data context} 201))
					(catch clojure.lang.ExceptionInfo e
						(log/warn "Validation error creating expense context" {:error (ex-message e) :data (ex-data e)})
						(h/json-response {:error (ex-message e)} 400))
					(catch Exception e
						(log/error e "Failed to create expense context" {:message (.getMessage e)})
						(h/json-response {:error "Failed to create expense context"} 500)))))))

(defn update-expense-context-handler
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-admin-or-owner request)]
				forbidden
				(let [context-id (h/parse-path-id request)]
					(if-not context-id
						(h/json-response {:error "Invalid expense context id"} 400)
						(try
							(let [body (h/read-body-params request)
										tenant-id (h/get-tenant-id request)
										updates (dissoc (context-payload body nil) :tenant_id)
										updated (some-> ((:update! expense-contexts/service) db context-id updates
																			(cond-> {}
																				tenant-id (assoc :tenant-id tenant-id)))
															h/to-app)]
								(if updated
									(h/json-response {:data updated})
									(h/not-found-response "Expense context not found")))
							(catch clojure.lang.ExceptionInfo e
								(log/warn "Validation error updating expense context" {:error (ex-message e)
																																				:data (ex-data e)
																																				:expense-context-id (str context-id)})
								(h/json-response {:error (ex-message e)} 400))
							(catch Exception e
								(log/error e "Failed to update expense context" {:message (.getMessage e)
																																	:expense-context-id (str context-id)})
								(h/json-response {:error "Failed to update expense context"} 500)))))))))

(defn batch-delete-expense-contexts-handler
	"Batch delete expense contexts (admin/owner only)."
	[db]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-admin-or-owner request)]
				forbidden
				(try
					(let [body (h/read-body-params request)
								tenant-id (h/get-tenant-id request)
								[raw-ids ids] (h/parse-batch-ids body [:ids :expense_context_ids :expense-context-ids :expenseContextIds])]
						(cond
							(empty? raw-ids)
							(h/json-response {:error "No expense context ids provided"} 400)

							(empty? ids)
							(h/json-response {:error "One or more expense context ids are invalid"} 400)

							:else
							(let [delete! (:delete! expense-contexts/service)
										delete-opts (cond-> {}
																	tenant-id (assoc :tenant-id tenant-id))
										result (h/batch-delete-entities #(delete! db % delete-opts) ids)]
								(if (and (zero? (:deleted-count result))
											(seq (:errors result)))
									(h/json-response {:error (:error (first (:errors result)))
																		:data result}
										400)
									(h/json-response {:data result})))))
					(catch clojure.lang.ExceptionInfo e
						(let [status (or (:status (ex-data e)) 400)]
							(h/json-response {:error (ex-message e)} status)))
					(catch Exception e
						(log/error e "Failed to batch delete expense contexts" {:message (.getMessage e)})
						(h/json-response {:error "Failed to delete expense contexts"} 500)))))))