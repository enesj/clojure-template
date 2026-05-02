(ns app.domain.backend.expenses.routes.expense-contexts
	"Admin API routes for expense contexts."
	(:require
		[app.domain.backend.expenses.routes.route-configs :as configs]
		[app.domain.backend.expenses.routes.routes-factory :as factory]))

(defn routes
	[db]
	(let [config (-> configs/expense-context-config
								 (factory/register-entity-routes!))]
		(factory/build-extended-routes db config)))