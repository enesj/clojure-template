(ns app.domain.frontend.expenses.events.user-expenses.expense-contexts
	"User-facing expense contexts list + CRUD (admin/owner only)."
	(:require
		[app.domain.frontend.expenses.admin.adapters.sync :as expenses-sync]
		[app.domain.frontend.expenses.events.user-expenses.endpoints :as endpoints]
		[app.domain.frontend.expenses.events.user-expenses.list-support :as list-support]
		[app.domain.frontend.expenses.events.user-expenses.xhrio :as x]
		[app.shared.adapters.normalization :as normalization]
		[app.shared.model-naming :as model-naming]
		[app.template.frontend.api.http :as http]
		[app.template.frontend.db.db :refer [common-interceptors]]
		[app.template.frontend.shared.crud.success :as crud-success]
		[re-frame.core :as rf]
		[taoensso.timbre :as log]))

(defn- prepare-expense-context-payload
	[form-data]
	(-> (or form-data {})
		normalization/convert-db-keys->app-keys
		(select-keys [:name :description :is-active])
		model-naming/app-map-keys->db))

(rf/reg-event-fx
	:user-expenses/refresh-expense-contexts-list
	common-interceptors
	(fn [{:keys [db]} [opts]]
		{:dispatch [:user-expenses/fetch-expense-contexts
								(merge (list-support/build-list-request-params db :expense-contexts 100)
									{:include_inactive true}
									(when (map? opts) opts))]}))

(rf/reg-event-fx
	:user-expenses/fetch-expense-contexts
	common-interceptors
	(fn [{:keys [db]} [params]]
		(list-support/entity-list-fetch-fx db
			{:entity-key :expense-contexts
			 :default-request-params {:limit 100 :offset 0}
			 :params params
			 :uri endpoints/expense-contexts-endpoint
			 :on-success [:user-expenses/fetch-expense-contexts-success]
			 :on-failure [:user-expenses/fetch-expense-contexts-failure]})))

(rf/reg-event-fx
	:user-expenses/fetch-expense-contexts-success
	common-interceptors
	(fn [{:keys [db]} [response]]
		(let [rows (vec (or (:data response) []))
					fx (list-support/entity-list-success-fx db :expense-contexts response ::expenses-sync/sync-expense-contexts)]
			(assoc fx :db (assoc-in (:db fx) [:user-expenses :expense-contexts :items] rows)))))

(rf/reg-event-db
	:user-expenses/fetch-expense-contexts-failure
	common-interceptors
	(fn [db [error]]
		(log/warn "Failed to fetch expense contexts" {:error error})
		(-> db
			(list-support/finish-entity-load :expense-contexts error)
			(assoc-in [:user-expenses :expense-contexts :error] (http/extract-error-message error)))))

(rf/reg-event-fx
	:user-expenses/create-expense-context-modal
	common-interceptors
	(fn [{:keys [db]} [form-data on-success]]
		{:db (-> db
					 (assoc-in [:user-expenses :form :loading?] true)
					 (assoc-in [:user-expenses :form :error] nil))
		 :http-xhrio (x/xhrio db
									 {:method :post
										:uri endpoints/expense-contexts-endpoint
										:params (prepare-expense-context-payload form-data)
										:on-success [:user-expenses/create-expense-context-modal-success on-success]
										:on-failure [:user-expenses/create-expense-context-modal-failure]})}))

(rf/reg-event-fx
	:user-expenses/create-expense-context-modal-success
	common-interceptors
	(fn [{:keys [db]} [on-success response]]
		(let [expense-context (:data response)
					context-id (:id expense-context)
					highlight-id (some-> context-id str)]
			{:db (-> db
						 (assoc-in [:user-expenses :form :loading?] false)
						 (assoc-in [:user-expenses :form :error] nil)
						 (cond-> highlight-id
							 (crud-success/track-recently-created :expense-contexts highlight-id)))
			 :dispatch-n [[:user-expenses/refresh-expense-contexts-list]]
			 :fx [(when on-success
							[:dispatch-later {:ms 100
																:dispatch [:user-expenses/call-modal-callback on-success expense-context]}])]})))

(rf/reg-event-db
	:user-expenses/create-expense-context-modal-failure
	common-interceptors
	(fn [db [error]]
		(log/warn "Failed to create expense context" {:error error})
		(-> db
			(assoc-in [:user-expenses :form :loading?] false)
			(assoc-in [:user-expenses :form :error] (http/extract-error-message error)))))

(rf/reg-event-fx
	:user-expenses/update-expense-context-modal
	common-interceptors
	(fn [{:keys [db]} [context-id form-data on-success]]
		(let [context-id-str (some-> context-id str)]
			{:db (-> db
						 (assoc-in [:user-expenses :form :loading?] true)
						 (assoc-in [:user-expenses :form :error] nil))
			 :http-xhrio (x/xhrio db
										 {:method :put
											:uri (str endpoints/expense-contexts-endpoint "/" context-id-str)
											:params (prepare-expense-context-payload form-data)
											:on-success [:user-expenses/update-expense-context-modal-success context-id-str on-success]
											:on-failure [:user-expenses/update-expense-context-modal-failure]})})))

(rf/reg-event-fx
	:user-expenses/update-expense-context-modal-success
	common-interceptors
	(fn [{:keys [db]} [context-id on-success _response]]
		(let [highlight-id (some-> context-id str)]
			{:db (-> db
						 (assoc-in [:user-expenses :form :loading?] false)
						 (assoc-in [:user-expenses :form :error] nil)
						 (cond-> (seq highlight-id)
							 (crud-success/track-recently-updated :expense-contexts highlight-id)))
			 :dispatch-n [[:user-expenses/refresh-expense-contexts-list]]
			 :fx [(when on-success
							[:dispatch-later {:ms 100
																:dispatch [:user-expenses/call-modal-callback on-success]}])]})))

(rf/reg-event-db
	:user-expenses/update-expense-context-modal-failure
	common-interceptors
	(fn [db [error]]
		(log/warn "Failed to update expense context" {:error error})
		(-> db
			(assoc-in [:user-expenses :form :loading?] false)
			(assoc-in [:user-expenses :form :error] (http/extract-error-message error)))))

(rf/reg-event-fx
	:user-expenses/delete-expense-context
	common-interceptors
	(fn [{:keys [db]} [context-id]]
		(let [context-id-str (some-> context-id str)]
			{:db (-> db
						 (assoc-in [:user-expenses :form :loading?] true)
						 (assoc-in [:user-expenses :form :error] nil))
			 :http-xhrio (x/xhrio db
										 {:method :delete
											:uri (str endpoints/expense-contexts-endpoint "/batch")
											:params {:ids [context-id-str]}
											:on-success [:user-expenses/delete-expense-context-success]
											:on-failure [:user-expenses/delete-expense-context-failure]})})))

(rf/reg-event-fx
	:user-expenses/delete-expense-context-success
	common-interceptors
	(fn [{:keys [db]} [_response]]
		{:db (assoc-in db [:user-expenses :form :loading?] false)
		 :dispatch [:user-expenses/refresh-expense-contexts-list]}))

(rf/reg-event-db
	:user-expenses/delete-expense-context-failure
	common-interceptors
	(fn [db [error]]
		(log/warn "Failed to delete expense context" {:error error})
		(-> db
			(assoc-in [:user-expenses :form :loading?] false)
			(assoc-in [:user-expenses :form :error] (http/extract-error-message error)))))