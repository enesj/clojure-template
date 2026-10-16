(ns app.domain.backend.expenses.services.article-resolution.queue
  "Work queue for the article resolver, stored in article_alias_resolutions.
  Every UPDATE sets updated_at explicitly (no trigger)."
  (:require
    [cheshire.core :as json]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn status-cast
  "HoneySQL cast of a status keyword/string to the alias_resolution_status enum."
  [s]
  [:cast (name s) :alias_resolution_status])

(defn- update-count [r] (long (or (::jdbc/update-count r) 0)))

(defn reclaim-stale!
  "Move `processing` rows untouched for `seconds` back to `pending`. Returns the number of rows."
  [db seconds]
  (update-count
    (jdbc/execute-one! db
      [(str "UPDATE article_alias_resolutions"
         " SET status = 'pending'::alias_resolution_status, updated_at = NOW()"
         " WHERE status = 'processing'::alias_resolution_status"
         "   AND updated_at < NOW() - (? * INTERVAL '1 second')")
       seconds])))

(def ^:private enqueue-select
  (str "INSERT INTO article_alias_resolutions (id, alias_id, status, attempts, created_at, updated_at)"
    " SELECT gen_random_uuid(), aa.id, 'pending'::alias_resolution_status, 0, NOW(), NOW()"
    " FROM article_aliases aa"
    " WHERE aa.article_id IS NULL"
    "%s"
    "   AND NOT EXISTS (SELECT 1 FROM article_alias_resolutions r WHERE r.alias_id = aa.id)"
    " ORDER BY aa.created_at"
    " LIMIT ?"
    " ON CONFLICT (alias_id) DO NOTHING"))

(defn enqueue-recent!
  "Enqueue unmapped aliases created within the last `lookback-hours`, at most `claim-limit`.
  Returns the number enqueued."
  [db {:keys [lookback-hours claim-limit]}]
  (update-count
    (jdbc/execute-one! db
      [(format enqueue-select "   AND aa.created_at >= NOW() - (? * INTERVAL '1 hour')")
       lookback-hours claim-limit])))

(defn ready?
  "Settle window: true when at least `min-rows` rows are pending or the oldest pending row has waited
  `settle-minutes`. Lets several receipts share one Claude call (the system prompt is a fixed cost)."
  [db {:keys [min-rows settle-minutes]}]
  (let [{:keys [n oldest_ok]}
        (jdbc/execute-one! db
          [(str "SELECT COUNT(*) AS n,"
             " COALESCE(MIN(created_at) <= NOW() - (? * INTERVAL '1 minute'), false) AS oldest_ok"
             " FROM article_alias_resolutions WHERE status = 'pending'::alias_resolution_status")
           settle-minutes]
          opts)]
    (and (pos? n)
      (or (>= n min-rows) (boolean oldest_ok)))))

(defn enqueue-backlog!
  "Enqueue up to `limit` unmapped aliases regardless of age. Returns the number enqueued."
  [db {:keys [limit]}]
  (update-count
    (jdbc/execute-one! db [(format enqueue-select "") limit])))

(defn claim!
  "Atomically move up to `limit` pending rows to processing (attempts + 1). Own transaction.
  Returns the claimed resolution ids."
  [db limit]
  (jdbc/with-transaction [tx db]
    (mapv :id
      (jdbc/execute! tx
        [(str "UPDATE article_alias_resolutions r"
           " SET status = 'processing'::alias_resolution_status, attempts = r.attempts + 1, updated_at = NOW()"
           " WHERE r.id IN (SELECT id FROM article_alias_resolutions"
           "                WHERE status = 'pending'::alias_resolution_status"
           "                ORDER BY created_at"
           "                LIMIT ?"
           "                FOR UPDATE SKIP LOCKED)"
           " RETURNING r.id")
         limit]
        opts))))

(defn load-claimed
  "Resolution rows joined with their alias and supplier name."
  [db ids]
  (if (empty? ids)
    []
    (jdbc/execute! db
      (sql/format
        {:select [[:r.id :resolution_id] :r.attempts [:aa.id :alias_id] :aa.raw_label :aa.raw_label_normalized
                  :aa.unit [:aa.article_id :alias_article_id] [:s.display_name :supplier]]
         :from [[:article_alias_resolutions :r]]
         :join [[:article_aliases :aa] [:= :aa.id :r.alias_id]]
         :left-join [[:suppliers :s] [:= :s.id :aa.supplier_id]]
         :where [:in :r.id ids]})
      opts)))

(def ^:private terminal-statuses #{"resolved" "noise" "rejected"})

(defn finalize!
  "Set the outcome of `resolution-ids` in one UPDATE. All outcome fields are written (nil when absent),
  `resolved_at` only for terminal statuses."
  [db resolution-ids {:keys [status source proposal decision-reason confidence article-id call-id error]}]
  (when (seq resolution-ids)
    (jdbc/execute-one! db
      (sql/format
        {:update :article_alias_resolutions
         :set {:status (status-cast status)
               :source source
               :proposal (when proposal [:cast (json/generate-string proposal) :jsonb])
               :decision_reason decision-reason
               :confidence confidence
               :article_id article-id
               :call_id call-id
               :error error
               :updated_at [:now]
               :resolved_at (when (terminal-statuses (name status)) [:now])}
         :where [:in :id resolution-ids]}))))

(defn requeue!
  "Set rows back to pending with attempts 0 and no error. Returns the number of rows."
  [db resolution-ids]
  (if (empty? resolution-ids)
    0
    (update-count
      (jdbc/execute-one! db
        (sql/format
          {:update :article_alias_resolutions
           :set {:status (status-cast :pending) :attempts 0 :error nil :resolved_at nil :updated_at [:now]}
           :where [:in :id resolution-ids]})))))

(defn record-call!
  "Insert an article_resolution_calls row; returns its id."
  [db {:keys [model served-model request-id group-count stop-reason usage duration-ms error]}]
  (let [id (random-uuid)]
    (jdbc/execute-one! db
      (sql/format
        {:insert-into :article_resolution_calls
         :values [{:id id
                   :model model
                   :served_model served-model
                   :request_id request-id
                   :group_count (or group-count 0)
                   :stop_reason stop-reason
                   :input_tokens (:input_tokens usage)
                   :output_tokens (:output_tokens usage)
                   :cache_creation_input_tokens (:cache_creation_input_tokens usage)
                   :cache_read_input_tokens (:cache_read_input_tokens usage)
                   :duration_ms duration-ms
                   :error error}]}))
    id))

(defn audit-candidates
  "Unaudited groups for the audit agent, lowest confidence first, one row per (label, unit):
  resolved `llm` rows below `max-confidence` (with the applied article) and needs_review rows.
  Proposal comes back as JSON text; `alias_count` = rows in the group."
  [db {:keys [max-confidence limit]}]
  (jdbc/execute! db
    [(str "SELECT * FROM ("
       " SELECT DISTINCT ON (aa.raw_label_normalized, aa.unit)"
       "        r.id AS resolution_id, r.status::text AS status, r.confidence, r.decision_reason,"
       "        r.proposal::text AS proposal, aa.raw_label, aa.unit, s.display_name AS supplier,"
       "        a.id AS article_id, a.canonical_name, c.name AS category, sc.name AS subcategory,"
       "        m.display_name AS manufacturer,"
       "        COUNT(*) OVER (PARTITION BY aa.raw_label_normalized, aa.unit) AS alias_count"
       " FROM article_alias_resolutions r"
       " JOIN article_aliases aa ON aa.id = r.alias_id"
       " LEFT JOIN suppliers s ON s.id = aa.supplier_id"
       " LEFT JOIN articles a ON a.id = r.article_id"
       " LEFT JOIN subcategories sc ON sc.id = a.subcategory_id"
       " LEFT JOIN categories c ON c.id = sc.category_id"
       " LEFT JOIN manufacturers m ON m.id = a.manufacturer_id"
       " WHERE r.audited_at IS NULL"
       "   AND ((r.status = 'resolved'::alias_resolution_status AND r.source = 'llm' AND r.confidence < ?)"
       "        OR r.status = 'needs_review'::alias_resolution_status)"
       " ORDER BY aa.raw_label_normalized, aa.unit, r.confidence) g"
       " ORDER BY confidence NULLS FIRST, raw_label"
       " LIMIT ?")
     max-confidence limit]
    opts))

(defn mark-audited!
  "Stamp audited_at / audit_verdict / audit_note on `resolution-ids`. Status and mapping are untouched.
  Returns the number of rows."
  [db resolution-ids {:keys [verdict note]}]
  (if (empty? resolution-ids)
    0
    (update-count
      (jdbc/execute-one! db
        (sql/format
          {:update :article_alias_resolutions
           :set {:audited_at [:now] :audit_verdict verdict :audit_note note :updated_at [:now]}
           :where [:in :id resolution-ids]})))))

(defn group-rows
  "Resolution rows in any of `statuses` that share (raw_label_normalized, unit) with `resolution-id`'s alias.
  Proposal comes back as JSON text."
  [db resolution-id statuses]
  (jdbc/execute! db
    (into [(str "SELECT r.id AS resolution_id, aa.id AS alias_id, aa.raw_label, aa.raw_label_normalized, aa.unit,"
             "       r.status::text AS status, r.proposal::text AS proposal, r.attempts, r.call_id"
             " FROM article_alias_resolutions r"
             " JOIN article_aliases aa ON aa.id = r.alias_id"
             " WHERE r.status::text IN (" (apply str (interpose ", " (repeat (count statuses) "?"))) ")"
             "   AND (aa.raw_label_normalized, aa.unit) ="
             "       (SELECT a2.raw_label_normalized, a2.unit FROM article_alias_resolutions r2"
             "          JOIN article_aliases a2 ON a2.id = r2.alias_id WHERE r2.id = ?)")]
      (concat (map name statuses) [resolution-id]))
    opts))

(defn release-processing!
  "Safety net: put any of `resolution-ids` still `processing` back to pending with `error`.
  With `:restore-attempt? true` the attempt consumed by the claim is given back (credential or
  configuration problems must not exhaust a row's retries). Returns the number of rows released."
  ([db resolution-ids error]
   (release-processing! db resolution-ids error nil))
  ([db resolution-ids error {:keys [restore-attempt?]}]
   (if (empty? resolution-ids)
     0
     (update-count
       (jdbc/execute-one! db
         (sql/format
           {:update :article_alias_resolutions
            :set (cond-> {:status (status-cast :pending) :error error :updated_at [:now]}
                   restore-attempt? (assoc :attempts [:greatest [:- :attempts 1] 0]))
            :where [:and [:in :id resolution-ids] [:= :status (status-cast :processing)]]}))))))
