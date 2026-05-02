(ns app.domain.frontend.expenses.admin.adapters.sync
  "Template sync events for expenses entities.

   These events sync data between admin/user contexts and the shared
   template entity store."
  (:require
    [app.admin.frontend.events.entity-sync :as entity-sync]
    [app.admin.frontend.events.users.template.form-interceptors :as form-interceptors]
    [app.domain.frontend.expenses.admin.adapters.normalize :as normalize]
    [app.template.frontend.shared.utils.entity :as entity-utils]))

(entity-utils/register-sync-event!
  {:event-id ::sync-stores
   :entity-key :stores
   :normalize-fn normalize/store->template-entity
   :log-prefix "[expenses] Syncing stores to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-store-aliases
   :entity-key :store-aliases
   :normalize-fn normalize/store-alias->template-entity
   :log-prefix "[expenses] Syncing store aliases to template:"})

;; =============================================================================
;; Template sync events
;; =============================================================================

(entity-utils/register-sync-event!
  {:event-id ::sync-expenses
   :entity-key :expenses
   :normalize-fn normalize/expense->template-entity
   :log-prefix "[expenses] Syncing expenses to template:"})

(entity-utils/register-upsert-event!
  {:event-id ::upsert-expenses
   :entity-key :expenses
   :normalize-fn normalize/expense->template-entity
   :log-prefix "[expenses] Upserting expenses to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-expense-items
   :entity-key :expense-items
   :normalize-fn normalize/expense-item->template-entity
   :log-prefix "[expenses] Syncing expense items to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-receipts
   :entity-key :receipts
   :normalize-fn normalize/receipt->template-entity
   :log-prefix "[expenses] Syncing receipts to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-suppliers
   :entity-key :suppliers
   :normalize-fn normalize/supplier->template-entity
   :log-prefix "[expenses] Syncing suppliers to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-manufacturers
   :entity-key :manufacturers
   :normalize-fn normalize/manufacturer->template-entity
   :log-prefix "[expenses] Syncing manufacturers to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-categories
   :entity-key :categories
   :normalize-fn normalize/category->template-entity
   :log-prefix "[expenses] Syncing categories to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-expense-categories
   :entity-key :expense-categories
   :normalize-fn normalize/expense-category->template-entity
   :log-prefix "[expenses] Syncing expense categories to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-expense-contexts
   :entity-key :expense-contexts
   :normalize-fn normalize/expense-context->template-entity
   :log-prefix "[expenses] Syncing expense contexts to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-cities
   :entity-key :cities
   :normalize-fn normalize/city->template-entity
   :log-prefix "[expenses] Syncing cities to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-countries
   :entity-key :countries
   :normalize-fn normalize/country->template-entity
   :log-prefix "[expenses] Syncing countries to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-subcategories
   :entity-key :subcategories
   :normalize-fn normalize/subcategory->template-entity
   :log-prefix "[expenses] Syncing subcategories to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-payers
   :entity-key :payers
   :normalize-fn normalize/payer->template-entity
   :log-prefix "[expenses] Syncing payers to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-articles
   :entity-key :articles
   :normalize-fn normalize/article->template-entity
   :log-prefix "[expenses] Syncing articles to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-article-aliases
   :entity-key :article-aliases
   :normalize-fn normalize/article-alias->template-entity
   :log-prefix "[expenses] Syncing article aliases to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-supplier-aliases
   :entity-key :supplier-aliases
   :normalize-fn normalize/supplier-alias->template-entity
   :log-prefix "[expenses] Syncing supplier aliases to template:"})

(entity-utils/register-sync-event!
  {:event-id ::sync-unmapped-aliases
   :entity-key :unmapped-aliases
   :normalize-fn normalize/unmapped-alias->template-entity
   :log-prefix "[expenses] Syncing unmapped aliases to template:"})

;; =============================================================================
;; Register with generic entity-sync dispatcher
;; =============================================================================
;; This enables the admin/refresh-entity-list event to dispatch to our sync events

(entity-sync/register-sync-handler! :expenses
  {:sync-event-id ::sync-expenses})
(entity-sync/register-sync-handler! :expense-items
  {:sync-event-id ::sync-expense-items})
(entity-sync/register-sync-handler! :receipts
  {:sync-event-id ::sync-receipts})
(entity-sync/register-sync-handler! :suppliers
  {:sync-event-id ::sync-suppliers})
(entity-sync/register-sync-handler! :stores
  {:sync-event-id ::sync-stores})
(entity-sync/register-sync-handler! :store-aliases
  {:sync-event-id ::sync-store-aliases})
(entity-sync/register-sync-handler! :manufacturers
  {:sync-event-id ::sync-manufacturers})
(entity-sync/register-sync-handler! :categories
  {:sync-event-id ::sync-categories})
(entity-sync/register-sync-handler! :expense-categories
  {:sync-event-id ::sync-expense-categories})
(entity-sync/register-sync-handler! :expense-contexts
  {:sync-event-id ::sync-expense-contexts})
(entity-sync/register-sync-handler! :cities
  {:sync-event-id ::sync-cities})
(entity-sync/register-sync-handler! :countries
  {:sync-event-id ::sync-countries})
(entity-sync/register-sync-handler! :subcategories
  {:sync-event-id ::sync-subcategories})
(entity-sync/register-sync-handler! :payers
  {:sync-event-id ::sync-payers})
(entity-sync/register-sync-handler! :articles
  {:sync-event-id ::sync-articles})
(entity-sync/register-sync-handler! :article-aliases
  {:sync-event-id ::sync-article-aliases})
(entity-sync/register-sync-handler! :supplier-aliases
  {:sync-event-id ::sync-supplier-aliases})

(entity-sync/register-sync-handler! :unmapped-aliases
  {:sync-event-id ::sync-unmapped-aliases})

;; =============================================================================
;; Register with form interceptors bridge
;; =============================================================================
;; This enables admin form submission to work with these entities

(form-interceptors/register-bridge-entity! :suppliers)
(form-interceptors/register-bridge-entity! :manufacturers)
(form-interceptors/register-bridge-entity! :categories)
(form-interceptors/register-bridge-entity! :expense-categories)
(form-interceptors/register-bridge-entity! :expense-contexts)
(form-interceptors/register-bridge-entity! :cities)
(form-interceptors/register-bridge-entity! :countries)
(form-interceptors/register-bridge-entity! :subcategories)
(form-interceptors/register-bridge-entity! :expenses)
(form-interceptors/register-bridge-entity! :expense-items)
(form-interceptors/register-bridge-entity! :receipts)
(form-interceptors/register-bridge-entity! :payers)
(form-interceptors/register-bridge-entity! :articles)
(form-interceptors/register-bridge-entity! :article-aliases)
(form-interceptors/register-bridge-entity! :supplier-aliases)

(comment
  ;; Loading this namespace registers `:countries` with the generic sync dispatcher
  ;; (`:admin/refresh-entity-list`) and with the admin form bridge.
  ;;
  ;; Example event payload (after a successful list response):
  ;; [:admin/refresh-entity-list :countries
  ;;  {:countries [{:id "1" :country "Germany" :code "DE"}]}]
  :rcf)
