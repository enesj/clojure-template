(ns app.domain.backend.expenses.services.service-configs.registry
  "Registry for expenses entity service configurations."
  (:require
    [app.domain.backend.expenses.services.service-configs.config-maps :as config-maps]
    [app.domain.backend.expenses.services.services-factory :as factory]))

(def ^:private entity-configs
  {:article-alias config-maps/article-alias-config
   :supplier-alias config-maps/supplier-alias-config
   :store-alias config-maps/store-alias-config

   :supplier config-maps/supplier-config
   :store config-maps/store-config
   :manufacturer config-maps/manufacturer-config
   :category config-maps/category-config
   :expense-category config-maps/expense-category-config
  :expense-context config-maps/expense-context-config
  :expense-template config-maps/expense-template-config
  :expense-template-line config-maps/expense-template-line-config
  :recurring-expense-reminder config-maps/recurring-expense-reminder-config
   :city config-maps/city-config
   :subcategory config-maps/subcategory-config
   :payer config-maps/payer-config
   :article config-maps/article-config
   :expense config-maps/expense-config
   :expense-item config-maps/expense-item-config
   :receipt config-maps/receipt-config})

(defn get-entity-config
  [entity-key]
  (when-let [config (get entity-configs entity-key)]
    (factory/register-entity-service! config)))

(defn register-all-entity-services!
  []
  (doseq [[entity-key config] entity-configs]
    (factory/register-entity-service!
      (assoc config :entity-key entity-key))))
