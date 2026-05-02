(ns app.domain.backend.expenses.routes.recurring-expense-reminders
	"Admin API routes for recurring expense reminders."
	(:require
		[app.domain.backend.expenses.routes.route-configs :as configs]
		[app.domain.backend.expenses.routes.routes-factory :as factory]))

(defn routes
	[db]
	(let [config (-> configs/recurring-expense-reminder-config
								 (factory/register-entity-routes!))]
		(factory/build-extended-routes db config)))