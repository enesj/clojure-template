(ns app.domain.backend.expenses.handlers.user-categories
  "User-facing categories endpoints.

  These endpoints are mounted under /api/v1/expenses/categories and are intended
  for power users inside the main app.

  IMPORTANT:
  - These routes are role-gated to admin/owner.
  - Responses are normalized to {:data ...} to keep frontend handlers consistent."
  (:require
    [app.domain.backend.expenses.handlers.user-expenses.helpers :as h]
    [app.domain.backend.expenses.services.categories :as categories]
    [app.domain.backend.expenses.services.subcategories :as subcategories]
    [taoensso.timbre :as log]))

;; -----------------------------------------------------------------------------
;; Handlers
;; -----------------------------------------------------------------------------

(defn list-categories-handler
  [db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-role request h/reference-data-read-roles "Role assignment required")]
        forbidden
        (try
          (let [qp (:query-params request)
                limit (h/parse-page-limit qp 200)
                offset (h/parse-page-offset qp)
                search (or (h/get-param qp :search)
                         (h/get-param qp :name))
                sort-opts (h/parse-sort-params qp)
                description (h/get-param qp :description)
                managed-only? (h/parse-boolean-param qp :managed-taxonomy-only)
                created-at-from (h/parse-instant-param (h/get-param qp :created-at-from))
                created-at-to (h/parse-instant-param (h/get-param qp :created-at-to))
                extra-filters (cond-> []
                                managed-only? (conj [:in :name subcategories/managed-category-name-list])
                                created-at-from (conj [:>= :created_at created-at-from])
                                created-at-to (conj [:<= :created_at created-at-to]))
                opts (cond-> {:limit limit
                              :offset offset
                              :search search}
                    (seq sort-opts) (merge sort-opts)
                       (some? description) (assoc :description description)
                       (seq extra-filters) (assoc :extra-filters extra-filters))
                rows (h/to-app ((:list categories/service) db opts))
                rows (cond-> rows (sequential? rows) vec)
                count-opts (cond-> (select-keys opts [:search :description])
                             (seq extra-filters) (assoc :extra-filters extra-filters))
                total (long (or ((:count categories/service) db count-opts) 0))]
            (h/json-response {:data rows
                              :total total
                              :limit limit
                              :offset offset}))
          (catch Exception e
            (log/error e "Failed to list categories" {:message (.getMessage e)})
            (h/json-response {:error "Failed to list categories"} 500)))))))

(defn create-category-handler
  [_db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (h/forbidden-response "Article categories are fixed. Tenant users can manage subcategories under Utilities and Subscriptions only.")))))

(defn update-category-handler
  [_db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (h/forbidden-response "Article categories are fixed. Tenant users can manage subcategories under Utilities and Subscriptions only.")))))

(defn batch-delete-categories-handler
  "Batch delete categories (admin/owner only).

  Expects JSON body like:
  {:ids [<uuid> ...]}

  Returns:
  {:data {:deleted-count n :deleted-ids [...] :errors [...]}}"
  [_db]
  (fn [request]
    (if-not (h/get-user request)
      (h/unauthorized-response)
      (if-let [forbidden (h/ensure-admin-or-owner request)]
        forbidden
        (h/forbidden-response "Article categories are fixed. Tenant users can manage subcategories under Utilities and Subscriptions only.")))))
