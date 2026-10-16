(ns app.domain.backend.expenses.services.article-resolution.core
  "Background alias -> article resolver: queue sweep, free stages (noise / sibling / exact),
  Claude resolution in chunks, validation, apply and review operations.

  Hard rules: no DB transaction is open across the Claude HTTP call; in :dry-run mode the
  worker never mutates the catalog (articles, aliases, subcategories, manufacturers, expense_items);
  a manual `approve!` always applies."
  (:require
    [app.domain.backend.expenses.integrations.claude :as claude]
    [app.domain.backend.expenses.integrations.typesafe :as typesafe]
    [app.domain.backend.expenses.services.article-resolution.catalog :as catalog]
    [app.domain.backend.expenses.services.article-resolution.config :as config]
    [app.domain.backend.expenses.services.article-resolution.heuristics :as heuristics]
    [app.domain.backend.expenses.services.article-resolution.noise :as noise]
    [app.domain.backend.expenses.services.article-resolution.prompt :as prompt]
    [app.domain.backend.expenses.services.article-resolution.queue :as queue]
    [app.domain.backend.expenses.services.article-resolution.taxonomy :as taxonomy]
    [app.domain.backend.expenses.services.article-resolution.validation :as validation]
    [app.domain.backend.expenses.services.article-resolution.verify :as verify]
    [app.domain.backend.expenses.services.articles.normalization :as normalization]
    [cheshire.core :as json]
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log]))

;; ----------------------------------------------------------------------------
;; Groups and proposals
;; ----------------------------------------------------------------------------

(defn- build-groups
  "Group claimed rows by (raw_label_normalized, unit): one decision per group, applied to every alias."
  [rows]
  (->> rows
    (group-by (juxt :raw_label_normalized :unit))
    (map (fn [[[norm unit] rs]]
           {:raw-label (:raw_label (first rs))
            :raw-label-normalized norm
            :unit unit
            :stores (vec (distinct (keep :supplier rs)))
            :alias-ids (mapv :alias_id rs)
            :resolution-ids (mapv :resolution_id rs)
            :attempts (apply max (map :attempts rs))}))
    (sort-by (juxt :raw-label-normalized :unit))
    vec))

(defn- auto-applicable?
  [{:keys [action]} problems]
  (boolean (and (empty? problems) (#{:create :map-existing :noise} action))))

(defn- build-proposal
  "Proposal JSON map (snake_case) stored on every resolution row."
  [group {:keys [decision problems hint] :as outcome}]
  (let [d decision]
    {:action (some-> (:action d) name (str/replace "-" "_"))
     :kind (:kind d)
     :canonical_name (:canonical-name d)
     :category (:category d)
     :subcategory (:subcategory d)
     :subcategory_is_new (boolean (:subcategory-is-new d))
     :manufacturer (:manufacturer d)
     :manufacturer_raw (:manufacturer-raw d)
     :existing_article_key (:existing-article-key d)
     :article_id (some-> (:article-id d) str)
     :llm_confidence (:confidence d)
     :reason (:reason d)
     :auto_applicable (auto-applicable? d problems)
     :problems (mapv name problems)
     :hint (when hint (select-keys hint [:category :subcategory :manufacturer]))
     :verifier (:verifier outcome)
     :candidates (mapv #(select-keys % [:key :name]) (:candidates group))}))

;; ----------------------------------------------------------------------------
;; Apply
;; ----------------------------------------------------------------------------

(defn- problem!
  [message problems]
  (throw (ex-info message {:problems problems})))

(defn apply-decision!
  "Apply a :create / :map-existing decision to every alias of `group` in ONE transaction:
  taxonomy + article, alias mapping, expense_items backfill, resolution rows -> resolved.
  `outcome` = {:source :proposal :decision-reason :call-id}. Returns {:article-id :mapped :created?}.
  Throws ex-info with :problems in ex-data (the transaction is rolled back)."
  [db cfg group decision {:keys [source proposal decision-reason call-id]}]
  (jdbc/with-transaction [tx db]
    (let [created (atom false)
          article-id
          (case (:action decision)
            :map-existing
            (or (:article-id decision)
              (:id (catalog/find-article tx (catalog/catalog-keys (:existing-article-key decision)) (:unit group)))
              (:id (catalog/find-article tx [(:existing-article-key decision)] (:unit group)))
              (problem! "Existing article not found" [:unknown-existing-key]))

            :create
            (let [index (catalog/category-index tx (:excluded-categories cfg))
                  cat   (or (get index (:category decision)) (problem! "Unknown category" [:unknown-category]))
                  sub   (or (catalog/ensure-subcategory! tx (:id cat) (:subcategory decision)
                              (:allow-new-subcategories? cfg))
                          (problem! "Subcategory not allowed" [:new-subcategory-not-allowed]))
                  mfr   (when (:manufacturer decision)
                          (catalog/ensure-manufacturer! tx (:manufacturer decision) (:allow-new-manufacturers? cfg)))
                  art   (catalog/ensure-article! tx {:canonical-name (:canonical-name decision)
                                                     :unit (:unit group)
                                                     :subcategory-id (:id sub)
                                                     :manufacturer-id (:id mfr)})]
              (reset! created (:created? art))
              (:id art))

            (problem! "Decision is not applicable" [:unknown]))
          confidence (:confidence decision)
          mapped     (catalog/map-aliases! tx (:alias-ids group) article-id confidence)]
      (catalog/backfill-expense-items! tx mapped article-id)
      (queue/finalize! tx (:resolution-ids group)
        {:status :resolved
         ;; nothing mapped = every alias was mapped meanwhile by someone else
         :source (if (empty? mapped) "external" source)
         :proposal proposal
         :decision-reason decision-reason
         :confidence confidence
         :article-id article-id
         :call-id call-id})
      {:article-id article-id :mapped (count mapped) :created? @created})))

;; ----------------------------------------------------------------------------
;; Finalize one group
;; ----------------------------------------------------------------------------

(defn- count-rows! [summary k group]
  (swap! summary update k + (count (:resolution-ids group))))

(defn- retry-or-fail!
  [db cfg summary group error-msg call-id]
  (let [retry? (< (:attempts group) (:max-attempts cfg))]
    (queue/finalize! db (:resolution-ids group)
      {:status (if retry? :pending :failed) :error error-msg :call-id call-id})
    (count-rows! summary (if retry? :retried :failed) group)))

(defn- finalize-group!
  "Store the outcome of one group according to :apply-mode. Never leaves the group in `processing`."
  [db cfg summary group {:keys [decision problems source call-id] :as outcome}]
  (let [proposal   (build-proposal group outcome)
        confidence (:confidence decision)
        reason     (when (seq problems) (str/join "," (map name problems)))
        review!    (fn [reason* error]
                     (queue/finalize! db (:resolution-ids group)
                       {:status :needs_review :source source :proposal proposal
                        :decision-reason reason* :confidence confidence :call-id call-id :error error})
                     (count-rows! summary :needs-review group))
        applicable (auto-applicable? decision problems)]
    (cond
      (= :dry-run (:apply-mode cfg))
      (review! (or reason "dry-run") nil)

      (not applicable)
      (review! reason nil)

      (= :noise (:action decision))
      (do (queue/finalize! db (:resolution-ids group)
            {:status :noise :source source :proposal proposal :confidence confidence :call-id call-id
             :decision-reason (:reason decision)})
        (count-rows! summary :noise group))

      :else
      (try
        (apply-decision! db cfg group decision
          {:source source :proposal proposal :call-id call-id :decision-reason (:reason decision)})
        (count-rows! summary :resolved group)
        (catch Exception e
          (log/warn e "Applying article resolution failed; sending to review" {:label (:raw-label group)})
          (review! (or (some->> (:problems (ex-data e)) (map name) (str/join ","))
                     "apply-failed")
            (ex-message e)))))))

;; ----------------------------------------------------------------------------
;; Free stages
;; ----------------------------------------------------------------------------

(defn- free-outcome
  "noise -> sibling -> exact; first hit wins. Returns an outcome or nil."
  [db group]
  (if-let [reason (noise/ocr-noise-reason {:raw_label (:raw-label group)
                                           :raw_label_normalized (:raw-label-normalized group)})]
    {:source "noise"
     :decision {:action :noise :kind "noise" :confidence 100 :reason reason}
     :problems []}
    (if-let [sibling (catalog/sibling-article-id db (:raw-label-normalized group) (:unit group))]
      {:source "sibling"
       :decision {:action :map-existing :kind "article" :article-id sibling :confidence 95
                  :reason "Another alias with the same label is already mapped"}
       :problems []}
      (when-let [exact (catalog/find-article db (catalog/catalog-keys (:raw-label group)) (:unit group))]
        {:source "exact"
         :decision {:action :map-existing :kind "article" :article-id (:id exact) :confidence 95
                    :reason "Article key equals the label's catalog key"}
         :problems []}))))

;; ----------------------------------------------------------------------------
;; Claude resolution
;; ----------------------------------------------------------------------------

(defn- http-snippet
  "First 300 chars of the model's response text (model output, never the request)."
  [s]
  (let [s (str/replace s #"\s+" " ")]
    (if (> (count s) 300) (str (subs s 0 300) "…") s)))

(defn- parse-items
  "Index Claude's items by id; nil when the text is not the expected JSON."
  [text]
  (try
    (let [parsed (json/parse-string text true)]
      (when (sequential? (:items parsed))
        (into {} (map (juxt :id identity)) (:items parsed))))
    (catch Exception _ nil)))

(defn- config-error?
  "True for failures caused by credentials/configuration rather than by the labels:
  missing key, 401/403, or a 400 that complains about the key or workspace."
  [{:keys [type status body-snippet]}]
  (boolean
    (or (= :claude/missing-api-key type)
      (contains? #{401 403} status)
      (and (= 400 status) (re-find #"(?i)workspace|api key|x-api-key" (str body-snippet))))))

(defn- verifier-on?
  [cfg]
  (and (get-in cfg [:verifier :enabled?])
    (not (str/blank? (get-in cfg [:verifier :typesafe :api-key])))))

(defn- verify-item
  "Ask Jev for a second opinion on one Claude item. Returns verify/apply-answers output, or nil
  (verifier errors never block resolution; noise/unknown items are not verified; in :fill-category
  mode only items without a valid category are sent)."
  [cfg index group item]
  (when (verify/needs-verification? (:verifier cfg) index item)
    (try
      (let [{:keys [answers]} (typesafe/evaluate! (get-in cfg [:verifier :typesafe])
                                (verify/state-for group item)
                                (verify/questions index item (:verifier cfg)))]
        (verify/apply-answers (:verifier cfg) index item answers))
      (catch Exception e
        (log/warn "Jev verification failed; keeping Claude's result" {:label (:raw-label group) :error (ex-message e)})
        nil))))

(defn- resolve-chunk!
  "Resolve one chunk of groups with a single Claude call. Context (categories, subcategories,
  candidates) is refreshed right before the call because the previous chunk may have created rows.
  Groups missing from a partial response are re-sent once in a follow-up call (the model sometimes
  stops after the first item); the call row's :error keeps a response snippet for diagnosis."
  ([db cfg tax summary groups]
   (resolve-chunk! db cfg tax summary groups true))
  ([db cfg tax summary groups retry-missing?]
   (let [index   (catalog/category-index db (:excluded-categories cfg))
         groups  (mapv #(assoc % :candidates
                          (catalog/article-candidates db (normalization/normalize-catalog-key (:raw-label %))
                            (:unit %) (:candidate-limit cfg)))
                   groups)
         ctx     {:category-index index :taxonomy tax :cfg cfg}
         ccfg    (assoc (:claude cfg) :db db)
         started (System/nanoTime)
         result  (try
                   {:ok (claude/create-message! ccfg {:system (prompt/system-blocks index tax)
                                                      :user (prompt/user-message groups)
                                                      :schema (prompt/response-schema index (:batch-size cfg))})}
                   (catch clojure.lang.ExceptionInfo e {:error e}))
         _       (swap! summary update :calls inc)
         ms      (long (/ (- (System/nanoTime) started) 1e6))
         ok      (:ok result)
         err     (:error result)
         items   (when ok (parse-items (:text ok)))
         missing (when ok
                   (count (remove #(contains? items (str "g" (inc %))) (range (count groups)))))
         call-id (queue/record-call! db
                   {:model (:model ccfg)
                    :served-model (:model ok)
                    :request-id (or (:id ok) (:id (ex-data err)))
                    :group-count (count groups)
                    :stop-reason (or (:stop-reason ok)
                                   (when err (some-> (:type (ex-data err)) name)))
                    :usage (or (:usage ok) (:usage (ex-data err)))
                    :duration-ms (or (:duration-ms ok) ms)
                    :error (cond
                             err (str (ex-message err) " " (pr-str (select-keys (ex-data err) [:type :status :body-snippet])))
                             (pos? missing) (str "Missing " missing "/" (count groups) " groups; response: "
                                              (some-> (:text ok) http-snippet)))})]
     (cond
      ;; Output too long: halve the chunk and retry each half.
       (and err (= :claude/max-tokens (:type (ex-data err))) (> (count groups) 1))
       (let [[a b] (split-at (quot (count groups) 2) groups)]
         (resolve-chunk! db cfg tax summary (vec a))
         (resolve-chunk! db cfg tax summary (vec b)))

       err
       (let [{:keys [type status body-snippet] :as data} (ex-data err)
             msg (str (ex-message err) " [" (some-> type name) (when status (str " " status)) "]"
                   (when body-snippet (str " " (subs body-snippet 0 (min 200 (count body-snippet))))))]
         (if (config-error? data)
          ;; Credentials / workspace problem: not the rows' fault. Give the attempt back and stop the sweep.
           (do (swap! summary assoc :config-error true)
             (doseq [g groups]
               (queue/release-processing! db (:resolution-ids g) msg {:restore-attempt? true})
               (count-rows! summary :retried g)))
           (doseq [g groups]
             (retry-or-fail! db cfg summary g msg call-id))))

       :else
       (let [indexed  (mapv (fn [i g] [g (get items (str "g" (inc i)))]) (range) groups)
            ;; Partial response: re-send the missing groups once, right away (prompt cache still warm).
             resend   (when (and retry-missing? (seq items) (pos? missing))
                        (vec (keep (fn [[g item]] (when (nil? item) g)) indexed)))
            ;; Jev calls run in parallel; DB writes stay sequential below.
             verified (if (verifier-on? cfg)
                        (vec (pmap (fn [[g item]] (when item (verify-item cfg index g item))) indexed))
                        (vec (repeat (count indexed) nil)))]
         (doseq [[[g item] v] (map vector indexed verified)]
           (cond
             (and (nil? item) resend)
             nil ; handled by the follow-up call below

             (nil? item)
             (retry-or-fail! db cfg summary g
               (if items "Group missing from Claude response" "Unparseable Claude response") call-id)

             :else
             (let [{:keys [decision problems]} (validation/validate-llm-item ctx g (or (:item v) item))]
               (finalize-group! db cfg summary g
                 {:decision decision
                  :problems (vec (distinct (concat problems (:problems v))))
                  :source "llm" :call-id call-id :hint (:hint g)
                  :verifier (:summary v)}))))
         (when (seq resend)
           (swap! summary update :resent (fnil + 0) (count resend))
           (resolve-chunk! db cfg tax summary resend false)))))))

;; ----------------------------------------------------------------------------
;; Sweep
;; ----------------------------------------------------------------------------

(defn run-sweep!
  "One worker iteration. `opts` may override :claim-limit; :force? true skips the settle window
  (:settle-minutes). Returns a summary map (:settling true when it waited)
  {:reclaimed n :enqueued n :claimed n :groups n :resolved n :needs-review n :noise n :failed n :retried n :calls n}
  (row counts, except :groups and :calls). Adds :config-error true when Claude rejected the
  credentials/workspace; the worker backs off on it."
  ([db app-config] (run-sweep! db app-config nil))
  ([db app-config opts]
   (let [cfg       (config/build-config app-config)
         tax       (taxonomy/current)
         summary   (atom {:reclaimed 0 :enqueued 0 :claimed 0 :groups 0 :resolved 0 :needs-review 0
                          :noise 0 :failed 0 :retried 0 :calls 0})
         _         (swap! summary assoc :reclaimed (queue/reclaim-stale! db (:stale-processing-seconds cfg)))
         _         (swap! summary assoc :enqueued (queue/enqueue-recent! db cfg))
         settled?  (or (:force? opts)
                     (not (pos? (:settle-minutes cfg)))
                     (queue/ready? db {:min-rows (:batch-size cfg) :settle-minutes (:settle-minutes cfg)}))
         _         (when-not settled? (swap! summary assoc :settling true))
         ids       (if settled? (queue/claim! db (or (:claim-limit opts) (:claim-limit cfg))) [])]
     (swap! summary assoc :claimed (count ids))
     (when (seq ids)
       (try
         (let [rows     (queue/load-claimed db ids)
               {mapped true open false} (group-by #(some? (:alias_article_id %)) rows)
               groups   (build-groups open)
               category-names (keys (catalog/category-index db (:excluded-categories cfg)))]
           (swap! summary assoc :groups (count groups))
           ;; Aliases mapped in the meantime (manual run, skill) need no work.
           (when (seq mapped)
             (queue/finalize! db (mapv :resolution_id mapped) {:status :resolved :source "external"})
             (swap! summary update :resolved + (count mapped)))
           (let [pending (reduce
                           (fn [acc g]
                             (if-let [outcome (try (free-outcome db g)
                                                (catch Exception e
                                                  (log/warn e "Free stage failed" {:label (:raw-label g)})
                                                  nil))]
                               (do (finalize-group! db cfg summary g outcome) acc)
                               (conj acc (assoc g :hint (heuristics/resolve-hint tax category-names
                                                          (:raw-label g) (first (:stores g)))))))
                           []
                           groups)]
             (doseq [chunk (partition-all (:batch-size cfg) pending)]
               (if (:config-error @summary)
                 ;; An earlier chunk hit a credentials problem: do not call Claude again this sweep.
                 (doseq [g chunk]
                   (queue/release-processing! db (:resolution-ids g) "Skipped after credentials error"
                     {:restore-attempt? true}))
                 (try
                   (resolve-chunk! db cfg tax summary (vec chunk))
                   (catch Exception e
                     (log/warn e "Article resolution chunk failed")
                     (doseq [g chunk]
                       (queue/release-processing! db (:resolution-ids g) (ex-message e)))))))))
         (finally
           ;; Never leave a claimed row in `processing`.
           (queue/release-processing! db ids "Sweep ended before the row was resolved"))))
     @summary)))

;; ----------------------------------------------------------------------------
;; Review operations
;; ----------------------------------------------------------------------------

(defn enqueue-backlog!
  "Enqueue up to :limit older unmapped aliases for resolution. Returns the number enqueued."
  [db {:keys [limit] :as opts}]
  (queue/enqueue-backlog! db (assoc opts :limit (or limit 100))))

(defn requeue!
  "Set resolution rows back to pending (attempts 0, error cleared). Returns the number of rows."
  [db resolution-ids]
  (queue/requeue! db resolution-ids))

(defn requeue-group!
  "Requeue every non-processing row of the group that `resolution-id` belongs to."
  [db resolution-id]
  (requeue! db (mapv :resolution_id
                 (queue/group-rows db resolution-id [:pending :resolved :needs_review :noise :rejected :failed]))))

(defn- review-group
  "Group map for the needs_review rows sharing `resolution-id`'s label + unit; throws 409/404 otherwise."
  [db resolution-id]
  (let [rows (queue/group-rows db resolution-id [:needs_review])
        row  (some #(when (= resolution-id (:resolution_id %)) %) rows)]
    (when-not row
      (throw (ex-info "Resolution is not in needs_review" {:status 409 :resolution-id resolution-id})))
    {:row row
     :group {:raw-label (:raw_label row)
             :raw-label-normalized (:raw_label_normalized row)
             :unit (:unit row)
             :alias-ids (mapv :alias_id rows)
             :resolution-ids (mapv :resolution_id rows)
             :attempts (apply max (map :attempts rows))}}))

(defn- override-keys
  [overrides]
  (into {} (map (fn [[k v]] [(keyword (str/replace (name k) "-" "_")) v])) overrides))

(defn reject!
  "Mark the group's needs_review rows rejected. Returns the number of rows."
  [db resolution-id]
  (let [{:keys [group]} (review-group db resolution-id)]
    (queue/finalize! db (:resolution-ids group) {:status :rejected :source "manual"})
    (count (:resolution-ids group))))

(def audit-verdicts #{"ok" "fixed" "rejected"})

(defn audit-candidates
  "Unaudited groups for the periodic audit agent (see queue/audit-candidates). Defaults: groups resolved
  below confidence 80 plus the review queue, 50 at a time. Free: no Claude call."
  [db {:keys [max-confidence limit] :or {max-confidence 80 limit 50}}]
  (queue/audit-candidates db {:max-confidence max-confidence :limit limit}))

(defn mark-audited!
  "Mark every row of `resolution-id`'s group (any status) as audited. `verdict` is \"ok\" (left as is),
  \"fixed\" (the agent corrected the article/mapping) or \"rejected\". Returns the number of rows.
  Throws {:status 400} for an unknown verdict, {:status 404} for an unknown id."
  [db resolution-id {:keys [verdict note]}]
  (when-not (audit-verdicts verdict)
    (throw (ex-info (str "Invalid audit verdict: " verdict) {:status 400})))
  (let [rows (queue/group-rows db resolution-id ["pending" "processing" "resolved" "needs_review"
                                                 "noise" "rejected" "failed"])]
    (when (empty? rows)
      (throw (ex-info "Article resolution not found" {:status 404 :resolution-id resolution-id})))
    (queue/mark-audited! db (mapv :resolution_id rows) {:verdict verdict :note note})))

(defn- current-article
  [db article-id]
  (jdbc/execute-one! db
    [(str "SELECT a.id, a.canonical_name, a.unit, a.subcategory_id, a.manufacturer_id,"
       "       sc.name AS subcategory, c.name AS category, m.display_name AS manufacturer"
       " FROM articles a"
       " LEFT JOIN subcategories sc ON sc.id = a.subcategory_id"
       " LEFT JOIN categories c ON c.id = sc.category_id"
       " LEFT JOIN manufacturers m ON m.id = a.manufacturer_id"
       " WHERE a.id = ?")
     article-id]
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn- remap-group!
  "Point the group's aliases, their expense items and resolution rows from `from-id` to `to-id`."
  [tx group from-id to-id]
  (let [alias-ids (:alias-ids group)]
    (jdbc/execute-one! tx (sql/format {:update :article_aliases :set {:article_id to-id :updated_at [:now]}
                                       :where [:and [:in :id alias-ids] [:= :article_id from-id]]}))
    (jdbc/execute-one! tx (sql/format {:update :expense_items :set {:article_id to-id}
                                       :where [:and [:in :alias_id alias-ids] [:= :article_id from-id]]}))
    (jdbc/execute-one! tx (sql/format {:update :article_alias_resolutions :set {:article_id to-id :updated_at [:now]}
                                       :where [:in :id (:resolution-ids group)]}))))

(defn correct!
  "Audit fix for a resolved group. `corrections` (snake_case keys, any subset): canonical_name, category,
  subcategory, manufacturer (nil clears it) or existing_article_key (remap to that article).
  - existing_article_key -> the group's aliases move to that article;
  - the article is used only by this group and the corrected name is free -> the article is edited in place;
  - otherwise (shared article, or the new name already exists) -> the corrected article is found or created
    and only this group moves to it. The old article is kept (nothing is deleted).
  Marks the group audited with verdict \"fixed\". No Claude call.
  Returns {:action :remapped|:updated :article-id uuid}. Throws {:status 400|404|409}."
  [db app-config resolution-id corrections]
  (let [cfg   (config/build-config app-config)
        c     (override-keys corrections)
        rows  (queue/group-rows db resolution-id ["resolved"])
        _     (when (empty? rows)
                (throw (ex-info "Resolution is not resolved" {:status 409 :resolution-id resolution-id})))
        group {:alias-ids (mapv :alias_id rows) :resolution-ids (mapv :resolution_id rows)}
        old   (some->> (jdbc/execute-one! db ["SELECT article_id FROM article_aliases WHERE id = ?" (first (:alias-ids group))]
                         {:builder-fn rs/as-unqualified-lower-maps})
                :article_id (current-article db))
        _     (when-not old (throw (ex-info "Group has no article" {:status 409 :resolution-id resolution-id})))
        result
        (jdbc/with-transaction [tx db]
          (if-let [k (some-> (:existing_article_key c) str/trim not-empty)]
            (let [target (or (catalog/find-article tx (catalog/catalog-keys k) (:unit old))
                           (catalog/find-article tx [k] (:unit old))
                           (throw (ex-info "Existing article not found" {:status 400 :problems ["unknown-existing-key"]})))]
              (remap-group! tx group (:id old) (:id target))
              {:action :remapped :article-id (:id target)})
            (let [index   (catalog/category-index tx #{})
                  name*   (or (some-> (:canonical_name c) str/trim not-empty) (:canonical_name old))
                  cat     (or (:category c) (:category old))
                  sub     (or (:subcategory c) (:subcategory old))
                  cat-row (or (get index cat) (throw (ex-info "Unknown category" {:status 400 :problems ["unknown-category"]})))
                  sub-row (or (catalog/ensure-subcategory! tx (:id cat-row) sub (:allow-new-subcategories? cfg))
                            (throw (ex-info "Subcategory not allowed" {:status 400 :problems ["new-subcategory-not-allowed"]})))
                  ;; same brand -> parent normalization as Claude's answers (brand-parent-mappings.edn)
                  mfr     (if (contains? c :manufacturer)
                            (validation/normalize-manufacturer (taxonomy/current) (:manufacturer c))
                            (:manufacturer old))
                  mfr-row (when mfr (catalog/ensure-manufacturer! tx mfr (:allow-new-manufacturers? cfg)))
                  clash   (catalog/find-article tx (catalog/catalog-keys name*) (:unit old))
                  shared? (pos? (:c (jdbc/execute-one! tx
                                      (sql/format {:select [[[:count :*] :c]] :from [:article_aliases]
                                                   :where [:and [:= :article_id (:id old)] [:not-in :id (:alias-ids group)]]})
                                      {:builder-fn rs/as-unqualified-lower-maps})))]
              (if (or shared? (and clash (not= (:id clash) (:id old))))
                (let [target (catalog/ensure-article! tx {:canonical-name name* :unit (:unit old)
                                                         :subcategory-id (:id sub-row) :manufacturer-id (:id mfr-row)})]
                  (remap-group! tx group (:id old) (:id target))
                  {:action :remapped :article-id (:id target)})
                (do (jdbc/execute-one! tx
                      (sql/format {:update :articles
                                   :set {:canonical_name name*
                                         :normalized_key (normalization/normalize-catalog-key name*)
                                         :subcategory_id (:id sub-row)
                                         :manufacturer_id (:id mfr-row)
                                         :updated_at [:now]}
                                   :where [:= :id (:id old)]}))
                    {:action :updated :article-id (:id old)})))))]
    (queue/mark-audited! db (:resolution-ids group)
      {:verdict "fixed" :note (str "correct!: " (pr-str (select-keys c [:canonical_name :category :subcategory
                                                                          :manufacturer :existing_article_key])))})
    result))

(defn- stored-low-confidence-ids
  "One needs_review resolution id per (label, unit) group held back only for low confidence, at or above
  `min-confidence`. Noise proposals are skipped (they have their own :noise-threshold)."
  [db min-confidence]
  (mapv :id
    (jdbc/execute! db
      [(str "SELECT DISTINCT ON (aa.raw_label_normalized, aa.unit) r.id"
         " FROM article_alias_resolutions r JOIN article_aliases aa ON aa.id = r.alias_id"
         " WHERE r.status = 'needs_review'::alias_resolution_status AND r.decision_reason = 'low-confidence'"
         "   AND r.confidence >= ? AND r.proposal->>'kind' = 'article'"
         " ORDER BY aa.raw_label_normalized, aa.unit, r.confidence DESC")
       min-confidence]
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn apply-stored!
  "Apply stored needs_review proposals that were held back only by `low-confidence`, once their confidence
  reaches the current :auto-apply-threshold (after lowering it). No Claude call: the stored proposal is
  re-validated against the current catalog (a since-created article with the same key is reused) and
  applied with source \"llm\", decision reason \"stored-proposal\" and its original confidence.
  With {:dry-run? true} nothing is written. Returns {:groups n :applied n :created n :mapped n :problems {..}}."
  [db app-config {:keys [dry-run?]}]
  (let [cfg   (config/build-config app-config)
        tax   (taxonomy/current)
        index (catalog/category-index db (:excluded-categories cfg))
        ctx   {:category-index index :taxonomy tax :cfg cfg}
        ids   (stored-low-confidence-ids db (:auto-apply-threshold cfg))]
    (reduce
      (fn [acc rid]
        (let [{:keys [row group]} (review-group db rid)
              p      (json/parse-string (:proposal row) true)
              ex-key (let [k (:existing_article_key p)] (when-not (str/blank? k) k))
              group  (assoc group :candidates
                       (cond-> (vec (catalog/article-candidates db (normalization/normalize-catalog-key (:raw-label group))
                                      (:unit group) (:candidate-limit cfg)))
                         ex-key (conj {:key ex-key})))
              item   {:kind "article" :canonical_name (:canonical_name p) :category (:category p)
                      :subcategory (:subcategory p) :manufacturer (:manufacturer p)
                      :existing_article_key ex-key :confidence (:llm_confidence p) :reason (:reason p)}
              {:keys [decision problems]} (validation/validate-llm-item ctx group item)
              problems (or (seq problems) (when-not (auto-applicable? decision []) [:not-applicable]))]
          (cond
            (seq problems)
            (update acc :problems #(merge-with + % (frequencies (map name problems))))

            dry-run?
            (-> acc (update :applied inc)
              (update (if (= :map-existing (:action decision)) :mapped :created) inc))

            :else
            (try
              (let [r (apply-decision! db cfg group decision
                        {:source "llm" :proposal (assoc p :auto_applicable true :problems [])
                         :decision-reason "stored-proposal"
                         :call-id (:call_id row)})]
                (-> acc (update :applied inc) (update (if (:created? r) :created :mapped) inc)))
              (catch Exception e
                (update acc :problems #(merge-with + % {(or (some->> (:problems (ex-data e)) first name)
                                                             "apply-failed") 1})))))))
      {:groups (count ids) :applied 0 :created 0 :mapped 0 :problems {}}
      ids)))

(defn approve!
  "Apply a needs_review proposal (with optional overrides: canonical_name, category, subcategory,
  manufacturer, existing_article_key) to every needs_review row of the same group. Source \"manual\".
  Throws ex-info {:status 400 :problems [...]} when validation fails (confidence is not checked)."
  [db app-config resolution-id overrides]
  (let [cfg      (config/build-config app-config)
        tax      (taxonomy/current)
        {:keys [row group]} (review-group db resolution-id)
        proposal (or (some-> (:proposal row) (json/parse-string true)) {})
        ov       (select-keys (override-keys overrides)
                   [:canonical_name :category :subcategory :manufacturer :existing_article_key])
        p        (merge proposal ov)
        index    (catalog/category-index db (:excluded-categories cfg))
        ex-key   (let [k (:existing_article_key p)] (when-not (str/blank? k) k))
        cands    (cond-> (vec (catalog/article-candidates db (normalization/normalize-catalog-key (:raw-label group))
                                (:unit group) (:candidate-limit cfg)))
                   (and ex-key (catalog/find-article db [ex-key] (:unit group)))
                   (conj {:key ex-key}))
        group    (assoc group :candidates cands)
        ctx      {:category-index index :taxonomy tax :cfg cfg}
        merged-proposal (assoc p :approved_by_override (boolean (seq ov)))]
    (cond
      ;; Confirm noise.
      (and (= "noise" (:action p)) (empty? ov))
      (do (queue/finalize! db (:resolution-ids group)
            {:status :noise :source "manual" :proposal merged-proposal :decision-reason "approved noise"})
        {:status :noise :resolution-ids (:resolution-ids group)})

      ;; Sibling / exact proposals carry the article id.
      (and (= "map_existing" (:action p)) (:article_id p) (nil? (:existing_article_key ov)))
      (let [r (apply-decision! db cfg group
                {:action :map-existing :article-id (parse-uuid (:article_id p)) :confidence 100}
                {:source "manual" :proposal merged-proposal :decision-reason "approved"})]
        (assoc r :status :resolved :resolution-ids (:resolution-ids group)))

      :else
      (let [item {:kind "article" :canonical_name (:canonical_name p) :category (:category p)
                  :subcategory (:subcategory p) :manufacturer (:manufacturer p)
                  :existing_article_key ex-key :confidence 100 :reason (:reason p)}
            {:keys [decision problems]} (validation/validate-llm-item ctx group item)]
        (when (seq problems)
          (throw (ex-info "Proposal is not valid" {:status 400 :problems (mapv name problems)})))
        (let [r (try
                  (apply-decision! db cfg group decision
                    {:source "manual" :proposal merged-proposal :decision-reason "approved"})
                  (catch clojure.lang.ExceptionInfo e
                    (if-let [ps (:problems (ex-data e))]
                      (throw (ex-info (ex-message e) {:status 400 :problems (mapv name ps)}))
                      (throw e))))]
          (assoc r :status :resolved :resolution-ids (:resolution-ids group)))))))

(comment
  (def db (:database @system.state/state))
  (def app-config (:config @system.state/state))
  (enqueue-backlog! db {:limit 20})
  (run-sweep! db app-config)
  ;; => summary with :needs-review ~20 in :dry-run mode, no catalog rows created
  :rcf)
