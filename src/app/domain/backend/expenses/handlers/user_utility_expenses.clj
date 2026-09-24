(ns app.domain.backend.expenses.handlers.user-utility-expenses
	"User-facing guided utility bill endpoint."
	(:require
		[app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
		[app.domain.backend.expenses.services.utility-expenses :as utility-expenses]
		[app.shared.model-naming :as model-naming]
		[clojure.string :as str]
		[taoensso.timbre :as log]))

(defn- uuid-param
	[body & ks]
	(some (fn [k]
					(when-let [value (h/get-param body k)]
						(h/try-parse-uuid value)))
		ks))

(defn- body-param
	[body & ks]
	(some #(h/get-param body %) ks))

(defn- normalize-payload
	[body]
	(let [body* (model-naming/app-map-keys->db (or body {}))]
		{:subcategory-id (uuid-param body* :subcategory_id :utility_subcategory_id :utility_kind_id)
		 :supplier-id (uuid-param body* :supplier_id)
		 :payer-id (uuid-param body* :payer_id)
		 :expense-context-id (uuid-param body* :expense_context_id)
		 :purchased-at (body-param body* :purchased_at :paid_at :date)
		 :amount (body-param body* :amount :total_amount)
		 :currency (some-> (body-param body* :currency) str str/trim str/upper-case not-empty)
		 :notes (body-param body* :notes)
		 :article-name (body-param body* :article_name)}))

(defn create-utility-expense-handler
	[db app-config]
	(fn [request]
		(if-not (h/get-user request)
			(h/unauthorized-response)
			(if-let [forbidden (h/ensure-role request h/expenses-write-roles
													 "Only members, admins, and owners can create utility expenses")]
				forbidden
				(try
					(let [payload (normalize-payload (h/read-body-params request))
								expense (utility-expenses/create-utility-expense!
													db
													(h/get-tenant-id request)
													(h/get-user-id request)
													payload
													app-config)]
						(h/json-response {:data (h/to-app expense)} 201))
					(catch clojure.lang.ExceptionInfo e
						(log/warn "Validation error creating utility expense"
							{:error (ex-message e) :data (ex-data e)})
						(h/json-response {:error (ex-message e)} (or (:status (ex-data e)) 400)))
					(catch Exception e
						(log/error e "Failed to create utility expense" {:message (.getMessage e)})
						(h/json-response {:error "Failed to create utility expense"} 500)))))))