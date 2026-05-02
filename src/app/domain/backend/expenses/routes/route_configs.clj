(ns app.domain.backend.expenses.routes.route-configs
  "Configuration maps for expenses domain route generation."
  (:require
    [app.domain.backend.expenses.privacy :as privacy]
    [app.domain.backend.expenses.routes.middleware :as private-resource-mw]
    [app.domain.backend.expenses.services.service-configs :as svc-configs]
    [app.template.backend.routes.admin.utils :as utils]
    [clojure.string :as str]))

;; =============================================================================
;; Entity Route Configurations
;; =============================================================================

(comment
  ;; Query param maps are normalized upstream by the routes factory.
  ;; Use direct keyword access like (:search qp).
  :ok)

;; =============================================================================
;; Shared Query-Param Helpers
;; =============================================================================

;; Most entities now use declarative :filter-params instead of custom functions.
;; See routes-factory/build-filter-params-fn for the supported spec formats.

;; article-query-params removed — now declared as :filter-params in article-config

(def category-config
  {:entity-key :category
   :entity-plural :categories
   :route-segment "categories"
   :service 'app.domain.backend.expenses.services.categories
   :default-limit 100
   :default-order-by "name"
   :required-fields [:name]
   :has-search? true
   :filter-params [:search :description]
   :date-range-columns {:created-at :created_at
                        :updated-at :updated_at}})

(def expense-category-config
  {:entity-key :expense-category
   :entity-plural :expense-categories
   :route-segment "expense-categories"
   :service 'app.domain.backend.expenses.services.expense-categories
   :default-limit 100
   :default-order-by "name"
   :required-fields [:name]
   :has-search? true
   :filter-params [:search]})

(def expense-context-config
  {:entity-key :expense-context
   :entity-plural :expense-contexts
   :route-segment "expense-contexts"
   :service 'app.domain.backend.expenses.services.expense-contexts
   :default-limit 100
   :default-order-by "name"
   :required-fields [:name]
   :has-search? true
   :filter-params {:search :string
                   :is-active :boolean}})

(def expense-template-config
  {:entity-key :expense-template
   :entity-plural :expense-templates
   :route-segment "expense-templates"
   :service 'app.domain.backend.expenses.services.expense-templates
   :default-limit 100
   :default-order-by "name"
   :required-fields [:name :kind]
   :has-search? true
   :filter-params {:search :string
                   :kind :string
                   :status :string
                   :expense-context-id :uuid
                   :expense-category-id :uuid}})

(def expense-template-line-config
  {:entity-key :expense-template-line
   :entity-plural :expense-template-lines
   :route-segment "expense-template-lines"
   :service 'app.domain.backend.expenses.services.expense-template-lines
   :default-limit 100
   :default-order-by "sort_order"
   :required-fields [:template-id :label]
   :has-search? true
   :filter-params {:search :string
                   :template-id :uuid
                   :article-id :uuid
                   :is-active :boolean}})

(def recurring-expense-reminder-config
  {:entity-key :recurring-expense-reminder
   :entity-plural :recurring-expense-reminders
   :route-segment "recurring-expense-reminders"
   :service 'app.domain.backend.expenses.services.recurring-expense-reminders
   :default-limit 100
   :default-order-by "due_date"
   :required-fields [:template-id :due-date]
   :has-search? false
   :filter-params {:status :string
                   :template-id :uuid}})

(def subcategory-config
  {:entity-key :subcategory
   :entity-plural :subcategories
   :route-segment "subcategories"
   :service 'app.domain.backend.expenses.services.subcategories
   :default-limit 100
   :default-order-by "name"
   :required-fields [:category-id :name]
   :has-search? true
   :filter-params [:search :category-name :description]
   :date-range-columns {:created-at :sc.created_at
                        :updated-at :sc.updated_at}})

(def supplier-config
  {:entity-key :supplier
   :entity-plural :suppliers
   :route-segment "suppliers"
   :service 'app.domain.backend.expenses.services.suppliers
   :default-limit 100
   :default-order-by "display_name"
   :required-fields [:display-name]
   :has-search? true
   :filter-params [:search :normalized-key]
   :date-range-columns {:created-at :created_at
                        :updated-at :updated_at}})

(def store-config
  {:entity-key :store
   :entity-plural :stores
   :route-segment "stores"
   :service 'app.domain.backend.expenses.services.stores
   :default-limit 100
   :default-order-by "display_name"
   :required-fields [:supplier-id :display-name]
   :has-search? false
   :filter-params [:search :supplier-display-name :normalized-key :address :city-name]
   ;; Table-qualified: store query JOINs cities + suppliers (alias :st)
   :date-range-columns {:created-at :st.created_at
                        :updated-at :st.updated_at}})

(def city-config
  {:entity-key :city
   :entity-plural :cities
   :route-segment "cities"
   :service 'app.domain.backend.expenses.services.cities
   :default-limit 100
   :default-order-by "name"
   :required-fields [:name]
   :has-search? true
   :filter-params [:search :normalized-key :zip :country]
   :date-range-columns {:created-at :created_at
                        :updated-at :updated_at}})

(def country-config
  {:entity-key :country
   :entity-plural :countries
   :route-segment "countries"
   :service 'app.domain.backend.expenses.services.countries
   :default-limit 500
   :default-order-by "country"
   :required-fields [:country :code]
   :parse-id (fn [v]
               (let [s (some-> v str str/trim)]
                 (if (seq s)
                   s
                   (throw (ex-info "Invalid id" {:status 400
                                                 :value v})))))})

(def manufacturer-config
  {:entity-key :manufacturer
   :entity-plural :manufacturers
   :route-segment "manufacturers"
   :service 'app.domain.backend.expenses.services.manufacturers
   :default-limit 100
   :default-order-by "display_name"
   :required-fields [:display-name]
   :has-search? true
   :filter-params [:search :normalized-key]
   :date-range-columns {:created-at :created_at
                        :updated-at :updated_at}})

(def payer-config
  {:entity-key :payer
   :entity-plural :payers
   :route-segment "payers"
   :service 'app.domain.backend.expenses.services.payers
   :default-limit 100
   :default-order-by "label"
   :required-fields [:label]
   :has-search? false
   :route-middleware [(fn [handler] (private-resource-mw/wrap-block-private-admin-resource handler))]})

(def article-config
  {:entity-key :article
   :entity-plural :articles
   :route-segment "articles"
   :service 'app.domain.backend.expenses.services.articles
   :default-limit 50
   :default-order-by "canonical_name"
   :required-fields [:canonical-name]
   :has-search? true
   :filter-params [:search :category-name :subcategory-name :manufacturer-display-name :unit]
   ;; Table-qualified: article query JOINs manufacturers/subcategories/categories (alias :a)
   :date-range-columns {:created-at :a.created_at
                        :updated-at :a.updated_at}})

(def expense-config
  {:entity-key :expense
   :entity-plural :expenses
   :route-segment "entries"
   :service 'app.domain.backend.expenses.services.expenses
   :default-limit 50
   :default-order-by "created_at"
   :default-order-dir "desc"
   :required-fields []
   :has-count? true
   :has-search? false
   :filter-params {:from :string
                   :to :string
                   :supplier-id :uuid
                   :payer-id :uuid
                   :expense-context-id :uuid
                   :source :string}
   :transform-response {:transform privacy/admin-expenses-view}})

(def expense-item-config
  {:entity-key :expense-item
   :entity-plural :expense-items
   :route-segment "expense-items"
   :service 'app.domain.backend.expenses.services.expense-items
   :default-limit 100
   :default-order-by "created_at"
   ;; NOTE: raw_label is resolved server-side into expense_items.alias_id.
   ;; Keeping :required-fields minimal avoids rejecting requests that only send raw_label.
   :required-fields [:expense-id :line-total]
   :has-search? true
   :route-middleware [(fn [handler] (private-resource-mw/wrap-block-private-admin-resource handler))]
   :filter-params [:search]})

(def article-alias-config
  {:entity-key :article-alias
   :entity-plural :article-aliases
   :route-segment "article-aliases"
   :service 'app.domain.backend.expenses.services.article-aliases
   :default-limit 50
   :default-order-by "raw_label"
   :required-fields [:supplier-id :raw-label :raw-label-normalized]
   :has-search? false
   :filter-params {:supplier-display-name :string
                   :article-canonical-name :string
                   :raw-label :string
                   :raw-label-normalized :string
                   :unit :string
                   :supplier-id :uuid
                   :article-id :uuid}
   :date-range-columns {:created-at :aa.created_at}

   ;; Admin edit forms can send numeric fields as strings (HTML inputs always
   ;; emit strings). Coerce them at the boundary so Postgres doesn't reject the
   ;; update with a type error.
   :transform-request (fn [body]
                        (cond-> body
                          (contains? body :confidence)
                          (assoc :confidence (utils/parse-int-param body :confidence nil))))})

(def supplier-alias-config
  {:entity-key :supplier-alias
   :entity-plural :supplier-aliases
   :route-segment "supplier-aliases"
   :service 'app.domain.backend.expenses.services.supplier-aliases
   :default-limit 50
   :default-order-by "raw_label"
   :required-fields [:raw-label :raw-label-normalized]
   :has-search? false
   :filter-params {:supplier-id :uuid
                   :unmapped-only :boolean
                   :search :string
                   :supplier-display-name :string
                   :raw-label-normalized :string}

   ;; Allow clients to omit raw-label-normalized; compute it server-side.
   ;; Also coerce numeric fields (e.g. confidence) since form submissions are strings.
   :transform-request (fn [body]
                        (let [raw-label (:raw-label body)
                              normalized (or (:raw-label-normalized body)
                                           (when raw-label
                                             (svc-configs/normalize-supplier-key raw-label)))
                              confidence (when (contains? body :confidence)
                                           (utils/parse-int-param body :confidence nil))]
                          (cond-> body
                            normalized (assoc :raw-label-normalized normalized)
                            (contains? body :confidence) (assoc :confidence confidence))))})

(def store-alias-config
  {:entity-key :store-alias
   :entity-plural :store-aliases
   :route-segment "store-aliases"
   :service 'app.domain.backend.expenses.services.store-aliases
   :default-limit 50
   :default-order-by "raw_label"
   :required-fields [:raw-label :raw-label-normalized]
   :has-search? false
   :filter-params {:search :string
                   :supplier-display-name :string
                   :store-display-name :string
                   :store-address :string
                   :raw-label :string
                   :raw-label-normalized :string
                   :confidence-min :int
                   :confidence-max :int}
   :date-range-columns {:created-at :sta.created_at}

   ;; Allow clients to omit raw-label-normalized; compute it server-side.
   ;; Also coerce numeric fields (e.g. confidence) since form submissions are strings.
   :transform-request (fn [body]
                        (let [raw-label (:raw-label body)
                              normalized (or (:raw-label-normalized body)
                                           (when raw-label
                                             (svc-configs/normalize-store-key raw-label)))
                              confidence (when (contains? body :confidence)
                                           (utils/parse-int-param body :confidence nil))]
                          (cond-> body
                            normalized (assoc :raw-label-normalized normalized)
                            (contains? body :confidence) (assoc :confidence confidence))))})

;; =============================================================================
;; Configuration Map
;; =============================================================================



