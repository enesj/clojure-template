(ns app.domain.backend.expenses.routes.expense-template-lines
	"Admin API routes for expense template lines."
	(:require
		[app.domain.backend.expenses.routes.route-configs :as configs]
		[app.domain.backend.expenses.routes.routes-factory :as factory]))

(defn routes
	[db]
	(let [config (-> configs/expense-template-line-config
								 (factory/register-entity-routes!))]
		(factory/build-extended-routes db config)))