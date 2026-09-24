(ns app.domain.frontend.expenses.events.user-expenses.utility
	"Events for guided utility bill entry."
	(:require
		[app.domain.frontend.expenses.admin.adapters.sync :as expenses-sync]
		[app.domain.frontend.expenses.events.user-expenses.endpoints :as endpoints]
		[app.domain.frontend.expenses.events.user-expenses.xhrio :as x]
		[app.template.frontend.api.http :as http]
		[app.template.frontend.db.db :refer [common-interceptors]]
		[app.template.frontend.shared.crud.success :as crud-success]
		[re-frame.core :as rf]
		[taoensso.timbre :as log]))

(rf/reg-event-fx
	:user-expenses/create-utility-expense
	common-interceptors
	(fn [{:keys [db]} [payload on-success]]
		{:db (-> db
					 (assoc-in [:user-expenses :form :loading?] true)
					 (assoc-in [:user-expenses :form :error] nil))
		 :http-xhrio (x/xhrio db
									 {:method :post
										:uri endpoints/utility-bills-endpoint
										:params (or payload {})
										:on-success [:user-expenses/create-utility-expense-success on-success]
										:on-failure [:user-expenses/create-utility-expense-failure]})}))

(rf/reg-event-fx
	:user-expenses/create-utility-expense-success
	common-interceptors
	(fn [{:keys [db]} [on-success response]]
		(let [expense (:data response)
					expense-id (or (:id expense) (:expense/id expense))
					highlight-id (some-> expense-id str)]
			(cond-> {:db (-> db
										 (assoc-in [:user-expenses :form :loading?] false)
										 (assoc-in [:user-expenses :form :error] nil)
										 (cond-> highlight-id
											 (crud-success/track-recently-created :expenses highlight-id)))
							 :dispatch-n [[:user-expenses/fetch-recent {:limit 25 :offset 0}]]
							 :fx [(when on-success
											[:dispatch-later {:ms 100
																				:dispatch [:user-expenses/call-modal-callback on-success]}])]}
				expense
				(assoc :dispatch [::expenses-sync/upsert-expenses [expense]])))))

(rf/reg-event-db
	:user-expenses/create-utility-expense-failure
	common-interceptors
	(fn [db [error]]
		(log/warn "Failed to create utility expense" {:error error})
		(-> db
			(assoc-in [:user-expenses :form :loading?] false)
			(assoc-in [:user-expenses :form :error] (http/extract-error-message error)))))