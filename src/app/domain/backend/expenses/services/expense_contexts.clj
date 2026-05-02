(ns app.domain.backend.expenses.services.expense-contexts
	"Expense context CRUD services using factory pattern."
	(:require
		[app.domain.backend.expenses.services.service-configs :as configs]
		[app.domain.backend.expenses.services.services-factory :as factory]))

(def config (configs/get-entity-config :expense-context))

(def service (factory/build-entity-service config))

(defn list-expense-contexts
	[db opts]
	((:list service) db opts))

(defn count-expense-contexts
	[db opts]
	((:count service) db opts))

(defn create-expense-context!
	[db data]
	((:create! service) db data))

(defn update-expense-context!
	[db expense-context-id updates]
	((:update! service) db expense-context-id updates))

(defn delete-expense-context!
	[db expense-context-id]
	((:delete! service) db expense-context-id))