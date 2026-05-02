(ns app.domain.backend.expenses.handlers.user-articles
  "User-facing articles endpoints.

  These endpoints are mounted under /api/v1/expenses/articles and are intended
  for power users inside the main app.

  IMPORTANT:
  - These routes are role-gated to admin/owner.
  - Responses are normalized to {:data ...} to keep frontend handlers consistent."
  (:require
    [app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
    [app.domain.backend.expenses.services.article-aliases :as aliases]
    [app.domain.backend.expenses.services.articles :as articles]
    [app.domain.backend.expenses.services.subcategories :as subcategories]
    [clojure.string :as str]
    [taoensso.timbre :as log]))

;; -----------------------------------------------------------------------------
;; Handlers
;; -----------------------------------------------------------------------------

(defn- ensure-selectable-subcategory!
  [db tenant-id subcategory-id]
  (when subcategory-id
    (let [subcategory (subcategories/tenant-managed-subcategory-record db subcategory-id)]
      (cond
        (nil? subcategory)
        (throw (ex-info "Subcategory not found" {:status 404 :subcategory-id subcategory-id}))

        (and (:tenant_id subcategory)
          (not= tenant-id (:tenant_id subcategory)))
        (throw (ex-info "Subcategory is not available in this tenant"
                 {:status 403
                  :tenant-id tenant-id
                  :subcategory-id subcategory-id}))

        (false? (:is_active subcategory))
        (throw (ex-info "Disabled subcategories are not selectable for new edits"
                 {:status 400
                  :subcategory-id subcategory-id}))

        :else subcategory))))

(defn- parse-instant-param
  [raw]
  (when-let [value (some-> raw str str/trim not-empty)]
    (or (try
          (java.time.Instant/parse value)
          (catch Exception _ nil))
      (try
        (-> (java.time.LocalDate/parse value)
          (.atStartOfDay java.time.ZoneOffset/UTC)
          .toInstant)
        (catch Exception _ nil)))))

(defn list-articles-handler
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
                         (h/get-param qp :canonical-name))
                unit (some-> (h/get-param qp :unit) str str/trim not-empty)
                manufacturer-display-name (some-> (h/get-param qp :manufacturer-display-name) str str/trim not-empty)
                category-name (some-> (h/get-param qp :category-name) str str/trim not-empty)
                subcategory-name (some-> (h/get-param qp :subcategory-name) str str/trim not-empty)
                created-at-from (parse-instant-param (h/get-param qp :created-at-from))
                created-at-to (parse-instant-param (h/get-param qp :created-at-to))
                extra-filters (cond-> []
                                created-at-from (conj [:>= :a.created_at created-at-from])
                                created-at-to (conj [:<= :a.created_at created-at-to]))
                  sort-opts (h/parse-sort-params qp)
                opts (cond-> {:limit limit
                              :offset offset}
                       (some? search) (assoc :search search)
                       unit (assoc :unit unit)
                       manufacturer-display-name (assoc :manufacturer-display-name manufacturer-display-name)
                       category-name (assoc :category-name category-name)
                       subcategory-name (assoc :subcategory-name subcategory-name)
                       (seq extra-filters) (assoc :extra-filters extra-filters)
                    (seq sort-opts) (merge sort-opts))
                count-opts (cond-> {}
                             (some? search) (assoc :search search)
                             unit (assoc :unit unit)
                             manufacturer-display-name (assoc :manufacturer-display-name manufacturer-display-name)
                             category-name (assoc :category-name category-name)
                             subcategory-name (assoc :subcategory-name subcategory-name)
                             (seq extra-filters) (assoc :extra-filters extra-filters))
                rows (h/to-app (articles/list-articles db opts))
                total (long (or (:total (articles/count-articles db count-opts)) 0))]
            (h/json-response {:data rows
                              :total total
                              :limit limit
                              :offset offset}))
          (catch Exception e
            (log/error e "Failed to list articles" {:message (.getMessage e)})
            (h/json-response {:error "Failed to list articles"} 500)))))))

(defn create-article-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (try
          (let [body (h/read-body-params request)
                canonical-name (:canonical_name body)
                unit (some-> (or (:unit body) (:item_unit body) (:item-unit body)) str str/trim not-empty)
                article (h/to-app (articles/create-article! db {:canonical_name canonical-name
                                                                :unit unit}))]
            (h/json-response {:data article} 201))
          (catch clojure.lang.ExceptionInfo e
            (log/warn "Validation error creating article" {:error (ex-message e) :data (ex-data e)})
            (h/json-response {:error (ex-message e)} 400))
          (catch Exception e
            (log/error e "Failed to create article" {:message (.getMessage e)})
            (h/json-response {:error "Failed to create article"} 500)))))))

(defn update-article-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (let [article-id (h/parse-path-id request)]
          (if-not article-id
            (h/json-response {:error "Invalid article id"} 400)
            (try
              (let [body (h/read-body-params request)
                  tenant-id (h/get-tenant-id request)
                    canonical-provided? (contains? body :canonical_name)
                    unit-provided? (or (contains? body :unit)
                                     (contains? body :item_unit)
                                     (contains? body :item-unit))
                    unit (when unit-provided?
                           (some-> (or (:unit body) (:item_unit body) (:item-unit body)) str str/trim not-empty))

                    manufacturer-id-provided? (or (contains? body :manufacturer_id)
                                                (contains? body :manufacturer-id)
                                                (contains? body :manufacturerId))
                    manufacturer-id-raw (when manufacturer-id-provided?
                                          (or (:manufacturer_id body)
                                            (:manufacturer-id body)
                                            (:manufacturerId body)))
                    manufacturer-id (when manufacturer-id-provided?
                                      (let [v (some-> manufacturer-id-raw str str/trim)]
                                        (when-not (str/blank? v)
                                          (h/try-parse-uuid v))))

                    subcategory-id-provided? (or (contains? body :subcategory_id)
                                               (contains? body :subcategory-id)
                                               (contains? body :subcategoryId))
                    subcategory-id-raw (when subcategory-id-provided?
                                         (or (:subcategory_id body)
                                           (:subcategory-id body)
                                           (:subcategoryId body)))
                    subcategory-id (when subcategory-id-provided?
                                     (let [v (some-> subcategory-id-raw str str/trim)]
                                       (when-not (str/blank? v)
                                         (h/try-parse-uuid v))))]

                (when (and manufacturer-id-provided?
                        (some? manufacturer-id-raw)
                        (not (str/blank? (some-> manufacturer-id-raw str str/trim)))
                        (nil? manufacturer-id))
                  (throw (ex-info "Invalid manufacturer id" {:status 400
                                                             :manufacturer-id manufacturer-id-raw
                                                             :article-id article-id})))

                (when (and subcategory-id-provided?
                        (some? subcategory-id-raw)
                        (not (str/blank? (some-> subcategory-id-raw str str/trim)))
                        (nil? subcategory-id))
                  (throw (ex-info "Invalid subcategory id" {:status 400
                                                            :subcategory-id subcategory-id-raw
                                                            :article-id article-id})))

                (when subcategory-id-provided?
                  (ensure-selectable-subcategory! db tenant-id subcategory-id))

                (let [updates (cond-> {}
                                canonical-provided?
                                (assoc :canonical_name (:canonical_name body))

                                unit-provided?
                                (assoc :unit unit)

                                (contains? body :category)
                                (assoc :category (:category body))

                                subcategory-id-provided?
                                (assoc :subcategory_id subcategory-id)

                                (contains? body :link)
                                (assoc :link (:link body))

                                manufacturer-id-provided?
                                (assoc :manufacturer_id manufacturer-id))

                      updated (articles/update-article! db article-id updates)]
                  (if updated
                    (h/json-response {:data (h/to-app updated)})
                    (h/not-found-response "Article not found"))))
              (catch clojure.lang.ExceptionInfo e
                (log/warn "Validation error updating article" {:error (ex-message e)
                                                               :data (ex-data e)
                                                               :article-id (str article-id)})
                (h/json-response {:error (ex-message e)} 400))
              (catch Exception e
                (log/error e "Failed to update article" {:message (.getMessage e)
                                                         :article-id (str article-id)})
                (h/json-response {:error "Failed to update article"} 500)))))))))

(defn batch-delete-articles-handler
  "Batch delete articles (admin/owner only).

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
                [raw-ids ids] (h/parse-batch-ids body [:ids :article_ids :article-ids :articleIds])]
            (cond
              (empty? raw-ids)
              (h/json-response {:error "No article ids provided"} 400)

              (empty? ids)
              (h/json-response {:error "One or more article ids are invalid"} 400)

              :else
              (h/json-response {:data (h/batch-delete-entities #(articles/delete-article! db %) ids)})))
          (catch Exception e
            (log/error e "Failed to batch delete articles" {:message (.getMessage e)})
            (h/json-response {:error "Failed to delete articles"} 500)))))))

(defn list-unmapped-aliases-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-role request h/reference-data-read-roles
                           "Role assignment required")]
        forbidden
        (try
          (let [tenant-id (h/get-tenant-id request)
                qp (:query-params request)
                supplier-id (h/try-parse-uuid (h/get-param qp :supplier_id))
                unit (some-> (h/get-param qp :unit) str str/trim not-empty)
                supplier-name (or (h/get-param qp :supplier-name)
                                (h/get-param qp :supplier-display-name))
                raw-label (h/get-param qp :raw-label)
                raw-label-normalized (h/get-param qp :raw-label-normalized)
                occurrence-count-min (some-> (h/get-param qp :occurrence-count-min) parse-long)
                occurrence-count-max (some-> (h/get-param qp :occurrence-count-max) parse-long)
                limit (h/parse-page-limit qp 50)
                offset (h/parse-page-offset qp)
                  sort-opts (h/parse-sort-params qp)
                opts (cond-> {:limit limit :offset offset}
                       supplier-id (assoc :supplier-id supplier-id)
                       unit (assoc :unit unit)
                       tenant-id (assoc :tenant-id tenant-id)
                       (some? supplier-name) (assoc :supplier-name supplier-name)
                       (some? raw-label) (assoc :raw-label raw-label)
                       (some? raw-label-normalized) (assoc :raw-label-normalized raw-label-normalized)
                       (some? occurrence-count-min) (assoc :occurrence-count-min occurrence-count-min)
                       (some? occurrence-count-max) (assoc :occurrence-count-max occurrence-count-max)
                    (seq sort-opts) (merge sort-opts))
                rows (h/to-app (aliases/list-unmapped-aliases db opts))
                total (long (or (aliases/count-unmapped-aliases db opts) 0))]
            (h/json-response {:data rows
                              :total total
                              :limit limit
                              :offset offset}))
          (catch Exception e
            (log/error e "Failed to list unmapped aliases" {:message (.getMessage e)})
            (h/json-response {:error "Failed to list unmapped aliases"} 500)))))))

(defn map-alias-to-article-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (try
          (let [alias-id (h/parse-path-id request :alias-id)
                body (h/read-body-params request)
                article-id (h/try-parse-uuid (or (:article-id body) (:article_id body)))]
            (when-not alias-id
              (throw (ex-info "Invalid alias-id" {:status 400})))
            (when-not article-id
              (throw (ex-info "Invalid article-id" {:status 400})))

            (let [updated (aliases/map-alias-to-article! db alias-id article-id)]
              (h/json-response {:data (h/to-app updated)})))
          (catch clojure.lang.ExceptionInfo e
            (let [{:keys [status]} (ex-data e)]
              (h/json-response {:error (ex-message e)} (or status 400))))
          (catch Exception e
            (log/error e "Failed to map alias" {:message (.getMessage e)})
            (h/json-response {:error "Failed to map alias"} 500)))))))

(defn batch-create-aliases-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (try
          (let [article-id (h/parse-path-id request)
                body (h/read-body-params request)
                supplier-id (h/try-parse-uuid (or (:supplier-id body) (:supplier_id body)))
                raw-labels (or (:raw-labels body)
                             (:raw_labels body)
                             (:raw-labels-text body)
                             (:raw_labels_text body))
                unit (or (:unit body) (:item-unit body) (:item_unit body))
                allow-reassign? (boolean (or (:allow-reassign? body) (:allow_reassign? body)))]
            (when-not article-id
              (throw (ex-info "Invalid article id" {:status 400})))
            (when-not supplier-id
              (throw (ex-info "Invalid supplier-id" {:status 400})))

            (let [result (articles/batch-create-aliases!
                           db
                           {:supplier-id supplier-id
                            :article-id article-id
                            :raw-labels raw-labels
                            :unit unit
                            :allow-reassign? allow-reassign?})]
              (h/json-response {:data (h/to-app result)})))
          (catch clojure.lang.ExceptionInfo e
            (let [{:keys [status]} (ex-data e)]
              (h/json-response {:error (ex-message e)} (or status 400))))
          (catch Exception e
            (log/error e "Failed to batch-create aliases" {:message (.getMessage e)})
            (h/json-response {:error "Failed to batch-create aliases"} 500)))))))
