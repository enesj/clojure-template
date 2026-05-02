(ns app.domain.backend.expenses.services.subcategories
  "Subcategory CRUD services using the factory pattern.

  Subcategories belong to a category via `:category_id` and are referenced by
  articles via `:subcategory_id`."
  (:require
    [app.domain.backend.expenses.services.related-records :as rr]
    [app.domain.backend.expenses.services.service-configs :as configs]
    [app.domain.backend.expenses.services.services-factory :as factory]
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

;; ============================================================================
;; Service Registration
;; ============================================================================

(def config
  (configs/get-entity-config :subcategory))

;; ============================================================================
;; Generated CRUD Operations
;; ============================================================================

(def service
  (factory/build-entity-service config))

;; ============================================================================
;; Tenant-managed utility/subscription taxonomy
;; ============================================================================

(def managed-category-names
  "Article categories whose tenant-local subcategories may be managed by tenant admins/owners."
  #{"Utilities" "Subscriptions"})

(def managed-category-name-list
  "Deterministic order for query parameters and UI options."
  ["Utilities" "Subscriptions"])

(defn- update-count
  [result]
  (or (:next.jdbc/update-count result)
    (:update-count result)
    0))

(defn managed-category-name?
  [category-name]
  (contains? managed-category-names (some-> category-name str str/trim)))

(defn managed-category-filter
  "HoneySQL filter for the fixed tenant-manageable article categories."
  []
  [:in :c.name managed-category-name-list])

(defn tenant-visible-filter
  "HoneySQL filter for global defaults plus the current tenant's local subcategories."
  [tenant-id]
  (if tenant-id
    [:or
     [:is :sc.tenant_id nil]
     [:= :sc.tenant_id tenant-id]]
    [:is :sc.tenant_id nil]))

(defn active-filter
  "HoneySQL filter for subcategories that may appear in new/edit pickers."
  []
  [:= :sc.is_active true])

(defn tenant-list-filters
  "Return extra filters for user-facing subcategory lists.

  `managed-only?` limits rows to Utilities and Subscriptions. `include-disabled?`
  is intended for the tenant management page; picker-style calls should omit it."
  [tenant-id {:keys [managed-only? include-disabled?]}]
  (cond-> [(tenant-visible-filter tenant-id)]
    managed-only? (conj (managed-category-filter))
    (not include-disabled?) (conj (active-filter))))

(defn category-record
  [db category-id]
  (jdbc/execute-one!
    db
    (sql/format {:select [:id :name]
                 :from [:categories]
                 :where [:= :id category-id]
                 :limit 1})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn ensure-managed-category!
  [db category-id]
  (let [category (category-record db category-id)]
    (cond
      (nil? category)
      (throw (ex-info "Category not found" {:status 404 :category-id category-id}))

      (not (managed-category-name? (:name category)))
      (throw (ex-info "Only Utilities and Subscriptions subcategories can be managed by tenant users"
               {:status 400
                :category-id category-id
                :category-name (:name category)
                :allowed-categories managed-category-names}))

      :else category)))

(defn tenant-managed-subcategory-record
  [db subcategory-id]
  (jdbc/execute-one!
    db
    (sql/format {:select [[:sc/id :id]
                          [:sc/category_id :category_id]
                          [:sc/tenant_id :tenant_id]
                          [:sc/name :name]
                          [:sc/is_active :is_active]
                          [:sc/is_system_default :is_system_default]
                          [:c/name :category_name]]
                 :from [[:subcategories :sc]]
                 :join [[:categories :c] [:= :c/id :sc/category_id]]
                 :where [:= :sc/id subcategory-id]
                 :limit 1})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn ensure-tenant-managed-subcategory!
  [db tenant-id subcategory-id]
  (let [subcategory (tenant-managed-subcategory-record db subcategory-id)]
    (cond
      (nil? subcategory)
      (throw (ex-info "Subcategory not found" {:status 404 :subcategory-id subcategory-id}))

      (not= tenant-id (:tenant_id subcategory))
      (throw (ex-info "Only tenant-owned subcategories can be managed"
               {:status 403
                :tenant-id tenant-id
                :subcategory-id subcategory-id}))

      (:is_system_default subcategory)
      (throw (ex-info "System default subcategories cannot be changed by tenant users"
               {:status 403
                :subcategory-id subcategory-id}))

      (not (managed-category-name? (:category_name subcategory)))
      (throw (ex-info "Only Utilities and Subscriptions subcategories can be managed by tenant users"
               {:status 400
                :subcategory-id subcategory-id
                :category-name (:category_name subcategory)
                :allowed-categories managed-category-names}))

      :else subcategory)))

(defn create-tenant-subcategory!
  [db {:keys [tenant-id category-id name description created-by-subject-ref]}]
  (when-not tenant-id
    (throw (ex-info "tenant-id is required" {:status 400})))
  (ensure-managed-category! db category-id)
  ((:create! service)
   db
   (cond-> {:tenant_id tenant-id
            :category_id category-id
            :name name
            :is_active true
            :is_system_default false}
     (some? description) (assoc :description description)
     (some? created-by-subject-ref) (assoc :created_by_subject_ref created-by-subject-ref))))

(defn update-tenant-subcategory!
  [db tenant-id subcategory-id updates]
  (let [current (ensure-tenant-managed-subcategory! db tenant-id subcategory-id)
        requested-category-id (:category_id updates)]
    (when (and (contains? updates :category_id)
            (not= requested-category-id (:category_id current)))
      (throw (ex-info "Tenant-managed subcategories cannot be moved to another category"
               {:status 400
                :subcategory-id subcategory-id
                :category-id requested-category-id})))
    ((:update! service)
     db
     subcategory-id
     (-> updates
       (dissoc :tenant_id :is_system_default :created_by_subject_ref)
       (assoc :category_id (:category_id current))))))

(defn disable-tenant-subcategory!
  [db tenant-id subcategory-id]
  (ensure-tenant-managed-subcategory! db tenant-id subcategory-id)
  (pos?
    (update-count
      (jdbc/execute-one!
        db
        (sql/format {:update :subcategories
                     :set {:is_active false}
                     :where [:and
                             [:= :id subcategory-id]
                             [:= :tenant_id tenant-id]
                             [:= :is_system_default false]]})))))

;; ============================================================================
;; Related Records
;; ============================================================================

(defn- list-related-articles
  [db subcategory-id limit]
  (jdbc/execute!
    db
    (sql/format {:select [[:a.id :id]
                          [:a.canonical_name :canonical_name]
                          [:a.normalized_key :normalized_key]
                          [:a.link :link]
                          [:a.created_at :created_at]
                          [:a.updated_at :updated_at]
                          [:m.display_name :manufacturer_display_name]]
                 :from [[:articles :a]]
                 :left-join [[:manufacturers :m] [:= :m.id :a.manufacturer_id]]
                 :where [:= :a.subcategory_id subcategory-id]
                 :order-by [[:a.canonical_name :asc]]
                 :limit limit})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn list-related-records
  "List records related to a subcategory by type.

  Supported types: articles."
  [db subcategory-id {:keys [type limit]}]
  (when-not subcategory-id
    (throw (ex-info "subcategory-id is required" {:status 400})))
  (let [related-type (rr/normalize-related-type type)
        related-limit (rr/clamp-related-limit limit)]
    (case related-type
      :articles (list-related-articles db subcategory-id related-limit)
      (throw (ex-info
               "Invalid related type. Expected one of: articles."
               {:status 400 :type type})))))

(defn- count-related-articles [db subcategory-id]
  (:cnt (jdbc/execute-one! db
          (sql/format {:select [[[:count :*] :cnt]]
                       :from [[:articles :a]]
                       :where [:= :a.subcategory_id subcategory-id]})
          {:builder-fn rs/as-unqualified-lower-maps})))

(defn count-all-related
  "Count related records for all types for a subcategory."
  [db subcategory-id]
  (when-not subcategory-id
    (throw (ex-info "subcategory-id is required" {:status 400})))
  {"articles" (or (count-related-articles db subcategory-id) 0)})
