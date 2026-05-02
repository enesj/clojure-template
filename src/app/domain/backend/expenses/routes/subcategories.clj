(ns app.domain.backend.expenses.routes.subcategories
  "Admin API routes for subcategories."
  (:require
    [app.domain.backend.expenses.routes.route-configs :as configs]
    [app.domain.backend.expenses.routes.routes-factory :as factory]
    [app.domain.backend.expenses.services.subcategories :as subcategories]
    [app.template.backend.routes.admin.utils :as utils]))

(defn routes [db]
  (let [config (-> configs/subcategory-config
                 (assoc :custom-query-params (fn [_]
                                               {:extra-filters [[:is :sc.tenant_id nil]]}))
                 (factory/register-entity-routes!))
        base-routes (factory/build-extended-routes db config)]
    (into base-routes
      [["/:id/related-records"
        {:get (fn [request]
                ((utils/with-error-handling
                   (fn [req]
                     (let [subcategory-id (utils/parse-uuid-custom (get-in req [:path-params :id]))
                           qp (:query-params req)
                           related-type (or (get qp "type") (:type qp))
                           limit (utils/parse-int-param qp :limit 100)]
                       (when-not subcategory-id
                         (throw (ex-info "Invalid subcategory id" {:status 400})))
                       (let [rows (subcategories/list-related-records
                                    db subcategory-id
                                    {:type related-type :limit limit})]
                         (utils/success-response {:related-records (factory/to-app rows)}))))
                   "Failed to list subcategory related records")
                 request))}]
       ["/:id/related-records/counts"
        {:get (fn [request]
                ((utils/with-error-handling
                   (fn [req]
                     (let [subcategory-id (utils/parse-uuid-custom (get-in req [:path-params :id]))]
                       (when-not subcategory-id
                         (throw (ex-info "Invalid subcategory id" {:status 400})))
                       (utils/success-response {:counts (subcategories/count-all-related db subcategory-id)})))
                   "Failed to count subcategory related records")
                 request))}]])))
