(ns app.domain.backend.expenses.handlers.user-subcategories
  "User-facing subcategories endpoints.

  These endpoints are mounted under /api/v1/expenses/subcategories and are intended
  for power users inside the main app.

  IMPORTANT:
  - These routes are role-gated to admin/owner.
  - Responses are normalized to {:data ...} to keep frontend handlers consistent."
  (:require
    [app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
    [app.domain.backend.expenses.services.subcategories :as subcategories]
    [clojure.string :as str]
    [taoensso.timbre :as log]))

;; -----------------------------------------------------------------------------
;; Helpers
;; -----------------------------------------------------------------------------

(defn- category-id-provided?
  [body]
  (or (contains? body :category_id)
    (contains? body :category-id)
    (contains? body :categoryId)))

(defn- extract-category-id
  [body]
  (or (:category_id body)
    (:category-id body)
    (:categoryId body)))

(defn- parse-category-id
  [body]
  (when (category-id-provided? body)
    (let [raw (extract-category-id body)
          s (some-> raw str str/trim)]
      (when (and (some? s) (not (str/blank? s)))
        (h/try-parse-uuid s)))))

;; -----------------------------------------------------------------------------
;; Handlers
;; -----------------------------------------------------------------------------

(defn list-subcategories-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-role request h/reference-data-read-roles
                           "Role assignment required")]
        forbidden
        (try
          (let [qp (:query-params request)
                limit (h/parse-page-limit qp 200)
                offset (h/parse-page-offset qp)
                search (or (h/get-param qp :search)
                         (h/get-param qp :name))
                sort-opts (h/parse-sort-params qp)
                category-name (h/get-param qp :category-name)
                description (h/get-param qp :description)
                tenant-id (h/get-tenant-id request)
                managed-only? (h/parse-boolean-param qp :managed-taxonomy-only)
                include-disabled? (h/parse-boolean-param qp :include-disabled)
                created-at-from (h/parse-instant-param (h/get-param qp :created-at-from))
                created-at-to (h/parse-instant-param (h/get-param qp :created-at-to))
                extra-filters (cond-> (subcategories/tenant-list-filters
                                         tenant-id
                                         {:managed-only? managed-only?
                                          :include-disabled? include-disabled?})
                                created-at-from (conj [:>= :sc.created_at created-at-from])
                                created-at-to (conj [:<= :sc.created_at created-at-to]))
                opts (cond-> {:limit limit
                              :offset offset
                              :search search}
                    (seq sort-opts) (merge sort-opts)
                       (some? category-name) (assoc :category-name category-name)
                       (some? description) (assoc :description description)
                       (seq extra-filters) (assoc :extra-filters extra-filters))
                rows (h/to-app ((:list subcategories/service) db opts))
                rows (cond-> rows (sequential? rows) vec)
                count-opts (cond-> (select-keys opts [:search :category-name :description])
                             (seq extra-filters) (assoc :extra-filters extra-filters))
                total (long (or ((:count subcategories/service) db count-opts) 0))]
            (h/json-response {:data rows
                              :total total
                              :limit limit
                              :offset offset}))
          (catch Exception e
            (log/error e "Failed to list subcategories" {:message (.getMessage e)})
            (h/json-response {:error "Failed to list subcategories"} 500)))))))

(defn create-subcategory-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (try
          (let [body (h/read-body-params request)
                tenant-id (h/get-tenant-id request)
                user-id (h/get-user-id request)
                category-id (parse-category-id body)
                name (:name body)
                description-provided? (or (contains? body :description)
                                        (contains? body :description_text)
                                        (contains? body :description-text)
                                        (contains? body :descriptionText))
                description (or (:description body)
                              (:description_text body)
                              (:description-text body)
                              (:descriptionText body))]

            (when-not category-id
              (throw (ex-info "Invalid category id" {:status 400})))

            (let [subcategory (h/to-app
                                (subcategories/create-tenant-subcategory!
                                  db
                                  (cond-> {:tenant-id tenant-id
                                           :category-id category-id
                                           :name name
                                           :created-by-subject-ref (some-> user-id str)}
                                    description-provided? (assoc :description description))))]
              (h/json-response {:data subcategory} 201)))
          (catch clojure.lang.ExceptionInfo e
            (log/warn "Validation error creating subcategory" {:error (ex-message e) :data (ex-data e)})
            (h/json-response {:error (ex-message e)} (or (:status (ex-data e)) 400)))
          (catch Exception e
            (log/error e "Failed to create subcategory" {:message (.getMessage e)})
            (h/json-response {:error "Failed to create subcategory"} 500)))))))

(defn update-subcategory-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (let [subcategory-id (h/parse-path-id request)]
          (if-not subcategory-id
            (h/json-response {:error "Invalid subcategory id"} 400)
            (try
              (let [body (h/read-body-params request)
                  tenant-id (h/get-tenant-id request)
                    category-id-provided?* (category-id-provided? body)
                    category-id (parse-category-id body)
                    name-provided? (contains? body :name)
                    name (:name body)
                    description-provided? (or (contains? body :description)
                                            (contains? body :description_text)
                                            (contains? body :description-text)
                                            (contains? body :descriptionText))
                    description (or (:description body)
                                  (:description_text body)
                                  (:description-text body)
                                  (:descriptionText body))]

                (when (and category-id-provided?*
                        (some? (extract-category-id body))
                        (not (str/blank? (some-> (extract-category-id body) str str/trim)))
                        (nil? category-id))
                  (throw (ex-info "Invalid category id" {:status 400
                                                         :subcategory-id (str subcategory-id)
                                                         :category-id (extract-category-id body)})))

                (let [updates (cond-> {}
                                category-id-provided?* (assoc :category_id category-id)
                                name-provided? (assoc :name name)
                                description-provided? (assoc :description description))
                      updated (some-> (subcategories/update-tenant-subcategory!
                                        db tenant-id subcategory-id updates)
                                h/to-app)]
                  (if updated
                    (h/json-response {:data updated})
                    (h/not-found-response "Subcategory not found"))))
              (catch clojure.lang.ExceptionInfo e
                (log/warn "Validation error updating subcategory" {:error (ex-message e)
                                                                   :data (ex-data e)
                                                                   :subcategory-id (str subcategory-id)})
                (h/json-response {:error (ex-message e)} (or (:status (ex-data e)) 400)))
              (catch Exception e
                (log/error e "Failed to update subcategory" {:message (.getMessage e)
                                                             :subcategory-id (str subcategory-id)})
                (h/json-response {:error "Failed to update subcategory"} 500)))))))))

(defn batch-delete-subcategories-handler
  "Batch delete subcategories (admin/owner only).

  Expects JSON body like:
  {:ids [<uuid> ...]}

  Returns:
  {:data {:deleted-count n :deleted-ids [...] :errors [...]}}"
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (try
          (let [body (h/read-body-params request)
                [raw-ids ids] (h/parse-batch-ids body [:ids :subcategory_ids :subcategory-ids :subcategoryIds])]
            (cond
              (empty? raw-ids)
              (h/json-response {:error "No subcategory ids provided"} 400)

              (empty? ids)
              (h/json-response {:error "One or more subcategory ids are invalid"} 400)

              :else
              (let [tenant-id (h/get-tenant-id request)]
                (h/json-response {:data (h/batch-delete-entities
                                           #(subcategories/disable-tenant-subcategory! db tenant-id %)
                                           ids)}))))
          (catch clojure.lang.ExceptionInfo e
            (log/warn "Validation error disabling subcategories" {:error (ex-message e) :data (ex-data e)})
            (h/json-response {:error (ex-message e)} (or (:status (ex-data e)) 400)))
          (catch Exception e
            (log/error e "Failed to batch delete subcategories" {:message (.getMessage e)})
            (h/json-response {:error "Failed to delete subcategories"} 500)))))))
