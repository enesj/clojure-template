(ns app.domain.backend.expenses.services.expense-template-lines
	"Expense template line CRUD services using factory pattern."
	(:require
		[app.domain.backend.expenses.services.service-configs :as configs]
		[app.domain.backend.expenses.services.services-factory :as factory]))

(def config (configs/get-entity-config :expense-template-line))

(def service (factory/build-entity-service config))

(defn list-expense-template-lines
	[db opts]
	((:list service) db opts))

(defn count-expense-template-lines
	[db opts]
	((:count service) db opts))

(defn create-expense-template-line!
	[db data]
	((:create! service) db data))

(defn update-expense-template-line!
	[db expense-template-line-id updates]
	((:update! service) db expense-template-line-id updates))

(defn delete-expense-template-line!
	[db expense-template-line-id]
	((:delete! service) db expense-template-line-id))