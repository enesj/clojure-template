(ns app.domain.backend.expenses.services.recurring-expense-reminders
	"Recurring expense reminder CRUD services using factory pattern."
	(:require
		[app.domain.backend.expenses.services.service-configs :as configs]
		[app.domain.backend.expenses.services.services-factory :as factory]))

(def config (configs/get-entity-config :recurring-expense-reminder))

(def service (factory/build-entity-service config))

(defn list-recurring-expense-reminders
	[db opts]
	((:list service) db opts))

(defn count-recurring-expense-reminders
	[db opts]
	((:count service) db opts))

(defn create-recurring-expense-reminder!
	[db data]
	((:create! service) db data))

(defn update-recurring-expense-reminder!
	[db recurring-expense-reminder-id updates]
	((:update! service) db recurring-expense-reminder-id updates))

(defn delete-recurring-expense-reminder!
	[db recurring-expense-reminder-id]
	((:delete! service) db recurring-expense-reminder-id))