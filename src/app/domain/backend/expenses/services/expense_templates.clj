(ns app.domain.backend.expenses.services.expense-templates
	"Expense template CRUD services using factory pattern."
	(:require
		[app.domain.backend.expenses.services.service-configs :as configs]
		[app.domain.backend.expenses.services.services-factory :as factory]))

(def config (configs/get-entity-config :expense-template))

(def service (factory/build-entity-service config))

(defn list-expense-templates
	[db opts]
	((:list service) db opts))

(defn count-expense-templates
	[db opts]
	((:count service) db opts))

(defn create-expense-template!
	[db data]
	((:create! service) db data))

(defn update-expense-template!
	[db expense-template-id updates]
	((:update! service) db expense-template-id updates))

(defn delete-expense-template!
	[db expense-template-id]
	((:delete! service) db expense-template-id))