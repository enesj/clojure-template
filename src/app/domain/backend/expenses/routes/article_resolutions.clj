(ns app.domain.backend.expenses.routes.article-resolutions
  "Admin API for the background article resolver (review queue). Mounted under
  /admin/api/expenses/article-resolutions."
  (:require
    [app.domain.backend.expenses.routes.routes-factory :as factory]
    [app.domain.backend.expenses.services.article-resolution.config :as resolver-config]
    [app.domain.backend.expenses.services.article-resolution.core :as core]
    [app.shared.adapters.database :as shared-db]
    [app.template.backend.routes.admin.utils :as utils]
    [clojure.string :as str]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log]))

(def ^:private statuses
  #{"pending" "processing" "resolved" "needs_review" "noise" "rejected" "failed"})

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defonce ^:private running? (atom false))

(defn- param
  [request k]
  (let [qp (:query-params request)]
    (or (get qp (name k)) (get qp k))))

(defn- body-map
  [request]
  (let [b (:body request)]
    (if (map? b) b {})))

(defn- id-param
  [request]
  (or (utils/parse-uuid-custom (get-in request [:path-params :id]))
    (throw (ex-info "Invalid article resolution id" {:status 400}))))

(defn- clamp
  [n lo hi]
  (-> n (max lo) (min hi)))

(defn list-handler
  "GET \"\": resolution rows of one status (default needs_review) with alias, unit and supplier."
  [db]
  (utils/with-error-handling
    (fn [request]
      (let [status (or (param request :status) "needs_review")
            limit  (clamp (utils/parse-int-param (:query-params request) :limit 50) 1 200)
            offset (max 0 (utils/parse-int-param (:query-params request) :offset 0))]
        (when-not (statuses status)
          (throw (ex-info (str "Invalid status: " status) {:status 400})))
        (let [rows  (jdbc/execute! db
                      [(str "SELECT r.id, r.status::text AS status, r.source, r.proposal, r.decision_reason,"
                         "       r.confidence, r.attempts, r.error, r.article_id, r.created_at, r.updated_at,"
                         "       r.resolved_at, aa.id AS alias_id, aa.raw_label, aa.unit, s.display_name AS supplier"
                         " FROM article_alias_resolutions r"
                         " JOIN article_aliases aa ON aa.id = r.alias_id"
                         " LEFT JOIN suppliers s ON s.id = aa.supplier_id"
                         " WHERE r.status = ?::alias_resolution_status"
                         " ORDER BY r.updated_at DESC, r.id"
                         " LIMIT ? OFFSET ?")
                       status limit offset]
                      opts)
              total (:c (jdbc/execute-one! db
                          ["SELECT COUNT(*) c FROM article_alias_resolutions WHERE status = ?::alias_resolution_status" status]
                          opts))]
          (utils/success-response {:resolutions (factory/to-app (shared-db/convert-pg-objects rows))
                                   :total total :limit limit :offset offset}))))
    "Failed to list article resolutions"))

(defn stats-handler
  "GET /stats: counts by status plus Claude call and token totals for the last 24 h and 7 d."
  [db]
  (utils/with-error-handling
    (fn [_request]
      (let [by-status (into {} (map (juxt :status :c))
                        (jdbc/execute! db
                          ["SELECT status::text AS status, COUNT(*) c FROM article_alias_resolutions GROUP BY 1"]
                          opts))
            window    (fn [hours]
                        (jdbc/execute-one! db
                          [(str "SELECT COUNT(*) calls, COALESCE(SUM(input_tokens),0) input_tokens,"
                             " COALESCE(SUM(output_tokens),0) output_tokens,"
                             " COALESCE(SUM(cache_creation_input_tokens),0) cache_creation_input_tokens,"
                             " COALESCE(SUM(cache_read_input_tokens),0) cache_read_input_tokens,"
                             " COUNT(*) FILTER (WHERE error IS NOT NULL) errors"
                             " FROM article_resolution_calls WHERE created_at >= NOW() - (? * INTERVAL '1 hour')")
                           hours]
                          opts))]
        (utils/success-response
          {:by-status (factory/to-app (into {} (map (fn [[k v]] [(keyword k) v])) by-status))
           :last-24h (factory/to-app (window 24))
           :last-7d (factory/to-app (window 168))})))
    "Failed to compute article resolution stats"))

(defn run-handler
  "POST /run: start one small sweep (at most one Claude call) in a background future.
  Returns 202 immediately; 409 while another /run is in progress."
  [db app-config]
  (utils/with-error-handling
    (fn [_request]
      (if (compare-and-set! running? false true)
        (let [batch-size (:batch-size (resolver-config/build-config app-config))]
          (future
            (try
              (log/info "Article resolver manual run" (core/run-sweep! db app-config {:claim-limit batch-size :force? true}))
              (catch Throwable t
                (log/warn t "Article resolver manual run failed"))
              (finally
                (reset! running? false))))
          (assoc (utils/success-response {:started true}) :status 202))
        (utils/error-response "A manual run is already in progress" :status 409)))
    "Failed to start article resolver run"))

(defn enqueue-backlog-handler
  "POST /enqueue-backlog {\"limit\": 100}: enqueue older unmapped aliases (max 500)."
  [db]
  (utils/with-error-handling
    (fn [request]
      (let [limit (clamp (or (let [l (:limit (body-map request))] (when (integer? l) l)) 100) 1 500)]
        (utils/success-response {:enqueued (core/enqueue-backlog! db {:limit limit}) :limit limit})))
    "Failed to enqueue backlog"))

(defn approve-handler
  "POST /:id/approve with optional overrides (canonical_name, category, subcategory, manufacturer,
  existing_article_key). Validation failures return 400 with :details {:problems [...]}."
  [db app-config]
  (utils/with-error-handling
    (fn [request]
      (let [id (id-param request)]
        (try
          (utils/success-response
            {:result (factory/to-app (core/approve! db app-config id (body-map request)))})
          (catch clojure.lang.ExceptionInfo e
            (let [{:keys [status problems]} (ex-data e)]
              (if (and (= 400 status) (seq problems))
                (utils/error-response (ex-message e) :status 400 :details {:problems problems})
                (throw e)))))))
    "Failed to approve article resolution"))

(defn reject-handler
  "POST /:id/reject: reject the group's needs_review rows."
  [db]
  (utils/with-error-handling
    (fn [request]
      (utils/success-response {:rejected (core/reject! db (id-param request))}))
    "Failed to reject article resolution"))

(defn requeue-handler
  "POST /:id/requeue: put the row's whole group back in the queue."
  [db]
  (utils/with-error-handling
    (fn [request]
      (utils/success-response {:requeued (core/requeue-group! db (id-param request))}))
    "Failed to requeue article resolution"))

(defn audit-candidates-handler
  "GET /audit-candidates?max_confidence=80&limit=50: unaudited low-confidence groups for the audit agent."
  [db]
  (utils/with-error-handling
    (fn [request]
      (let [qp    (:query-params request)
            max-c (clamp (utils/parse-int-param qp :max_confidence 80) 0 101)
            limit (clamp (utils/parse-int-param qp :limit 50) 1 200)
            rows  (core/audit-candidates db {:max-confidence max-c :limit limit})]
        (utils/success-response {:candidates (factory/to-app (shared-db/convert-pg-objects rows))
                                 :max-confidence max-c :limit limit})))
    "Failed to list audit candidates"))

(defn audited-handler
  "POST /:id/audited {\"verdict\": \"ok|fixed|rejected\", \"note\": \"...\"}: mark the row's group audited."
  [db]
  (utils/with-error-handling
    (fn [request]
      (let [{:keys [verdict note]} (body-map request)]
        (utils/success-response {:audited (core/mark-audited! db (id-param request)
                                            {:verdict (some-> verdict name) :note note})})))
    "Failed to mark article resolution audited"))

(defn routes
  "Route data for /article-resolutions (mounted under /admin/api/expenses)."
  [db app-config]
  ["/article-resolutions"
   ["" {:get (list-handler db)}]
   ["/stats" {:get (stats-handler db)}]
   ["/run" {:post (run-handler db app-config)}]
   ["/enqueue-backlog" {:post (enqueue-backlog-handler db)}]
   ["/audit-candidates" {:get (audit-candidates-handler db)}]
   ["/:id/audited" {:post (audited-handler db)}]
   ["/:id/approve" {:post (approve-handler db app-config)}]
   ["/:id/reject" {:post (reject-handler db)}]
   ["/:id/requeue" {:post (requeue-handler db)}]])
