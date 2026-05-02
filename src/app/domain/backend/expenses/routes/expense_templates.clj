(ns app.domain.backend.expenses.routes.expense-templates
	"Admin API routes for expense templates."
	(:require
		[app.domain.backend.expenses.routes.route-configs :as configs]
		[app.domain.backend.expenses.routes.routes-factory :as factory]))

(defn routes
	[db]
	(let [config (-> configs/expense-template-config
								 (factory/register-entity-routes!))]
		(factory/build-extended-routes db config)))