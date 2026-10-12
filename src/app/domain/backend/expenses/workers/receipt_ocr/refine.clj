(ns app.domain.backend.expenses.workers.receipt-ocr.refine
  "Cerebras-based receipt refinement: eligibility checks, single-result refine,
  parallel batch refine, and auto-post logic."
  (:require
    [app.domain.backend.expenses.integrations.cerebras :as cerebras]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-markdown :as receipt-md]
    [app.domain.backend.expenses.services.receipts.queries :as receipt-queries]
    [app.domain.backend.expenses.services.receipts.status :as receipt-status]
    [app.domain.backend.expenses.workers.receipt-ocr.common :as common]
    [app.domain.backend.expenses.workers.receipt-ocr.extraction :as extraction]
    [clojure.string :as str]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Executors TimeUnit TimeoutException]))

(defn- env-truthy?
  [k]
  (let [v (some-> (System/getenv k) str str/trim str/lower-case)]
    (contains? #{"1" "true" "yes" "y" "on"} v)))

(defonce ^:private receipt-refine-include-store-context?
  (env-truthy? "RECEIPT_REFINE_INCLUDE_STORE_CONTEXT"))

(defn- receipt-refine-context
  [db receipt-id]
  (try
    (when-let [ctx (receipt-queries/get-receipt-refine-context db receipt-id)]
      (let [supplier-key (some-> (:supplier_key ctx) str str/trim not-empty)
            store-key (some-> (:store_key ctx) str str/trim not-empty)
            fingerprint (when (and supplier-key store-key)
                          (str supplier-key "/" store-key))]
        (cond-> ctx
          fingerprint (assoc :store_fingerprint fingerprint))))
    (catch Exception e
      (log/warn e "Failed to load receipt refine context" {:receipt-id receipt-id})
      nil)))

(defn- store-receipt-refine-context-best-effort!
  [db receipt-id]
  (when-let [ctx (receipt-refine-context db receipt-id)]
    (try
      (receipt-status/store-receipt-refine-context! db receipt-id ctx)
      (catch Exception e
        (log/warn e "Failed to persist receipt refine context" {:receipt-id receipt-id})))
    ctx))

(defn- global-setting-enabled?
  [opts setting-key]
  (true? (get opts setting-key)))

(defn receipt-eligible-for-refine?
  "Return true when the receipt is eligible for Cerebras refinement.
   This now follows the global AI enhancement setting instead of a per-user flag."
  [_db _receipt opts]
  (or (true? (:force-refine? opts))
    (global-setting-enabled? opts :ai-receipt-enhancement?)))

(defn- maybe-refine-with-cerebras
  [db receipt extract-result {:keys [cerebras-cfg] :as _opts}]
  (let [receipt-id (:id receipt)
        markdown (:parsed-markdown extract-result)
        items (get-in extract-result [:extraction :items])
        items-sum (double (reduce + 0M (keep #(some-> (:line_total %) bigdec) items)))
        total-guess (some-> (get-in extract-result [:extraction :totals :total]) double)
        total-diff (when total-guess
                     (Math/abs (- total-guess items-sum)))
        total-mismatch? (and total-diff (> total-diff 0.01))
        raw-tables (when total-mismatch?
                     (some->> (:raw extract-result)
                       receipt-md/response->table-markdowns
                       not-empty))
        markdown (cond-> markdown
                   (seq raw-tables)
                   (str "\n\n---\nRaw OCR table (may contain additional items not captured above):\n\n"
                     (str/join "\n\n" raw-tables)))
        subject-ref (:subject_ref receipt)
        filename (:original_filename receipt)
        user-enabled? (receipt-eligible-for-refine? db receipt _opts)
        has-markdown? (boolean (seq (some-> markdown str str/trim)))
        cerebras-cfg? (map? cerebras-cfg)
        has-api-key? (boolean (and cerebras-cfg? (seq (:api-key cerebras-cfg))))
        provider-confidence (get-in extract-result [:extraction :provider_confidence])
        reasons (cond-> []
                  (not user-enabled?) (conj :user-disabled)
                  (not cerebras-cfg?) (conj :missing-cerebras-config)
                  (and cerebras-cfg? (not (seq (:api-key cerebras-cfg)))) (conj :missing-api-key)
                  (not has-markdown?) (conj :blank-markdown))]
    (if (seq reasons)
      (do
        (when user-enabled?
          (log/info "Cerebras receipt refine skipped"
            {:receipt-id receipt-id
             :subject-ref subject-ref
             :filename filename
             :reasons reasons
             :has-api-key? has-api-key?
             :has-markdown? has-markdown?}))
        extract-result)
      (let [ctx (store-receipt-refine-context-best-effort! db receipt-id)
            supplier-key (some-> (:supplier_key ctx) str str/trim not-empty)
            store-key (some-> (:store_key ctx) str str/trim not-empty)
            fingerprint (some-> (:store_fingerprint ctx) str str/trim not-empty)
            refine-opts (when (and receipt-refine-include-store-context? (map? ctx))
                          {:context ctx})]
        (try
          (log/info "Cerebras receipt refine starting"
            (cond-> {:receipt-id receipt-id
                     :subject-ref subject-ref
                     :filename filename
                     :include-store-context? receipt-refine-include-store-context?
                     :total-mismatch? total-mismatch?
                     :total-diff total-diff
                     :raw-tables-appended? (boolean (seq raw-tables))}
              supplier-key (assoc :supplier-key supplier-key)
              store-key (assoc :store-key store-key)
              fingerprint (assoc :fingerprint fingerprint)))
          (let [started (System/nanoTime)
                refine (if refine-opts
                         (cerebras/refine-receipt-markdown! cerebras-cfg markdown refine-opts)
                         (cerebras/refine-receipt-markdown! cerebras-cfg markdown))
                duration-ms (/ (- (System/nanoTime) started) 1000000.0)
                refined-extraction (cond-> (:extraction refine)
                                     provider-confidence
                                     (assoc :provider_confidence provider-confidence)

                                     ;; The LLM often returns no merchant when the name is missing; keep the
                                     ;; OCR store name/address so supplier can still be inferred from the address.
                                     (seq (get-in extract-result [:extraction :merchant]))
                                     (update :merchant #(merge (get-in extract-result [:extraction :merchant]) %)))]
            (log/info "Cerebras receipt refine applied"
              (cond-> {:receipt-id receipt-id
                       :subject-ref subject-ref
                       :filename filename
                       :duration-ms duration-ms
                       :model (:model refine)
                       :has-extraction? (boolean (:extraction refine))}
                supplier-key (assoc :supplier-key supplier-key)
                store-key (assoc :store-key store-key)
                fingerprint (assoc :fingerprint fingerprint)))
            (cond-> extract-result
              (:extraction refine) (assoc :extraction refined-extraction)
              refine (assoc :llm_refine refine)))
          (catch Exception e
            (when db
              (store-receipt-refine-context-best-effort! db receipt-id))
            (let [details (common/safe-ex-data e)]
              (log/warn e "Cerebras receipt refine failed; continuing without refine"
                (cond-> {:receipt-id receipt-id
                         :subject-ref subject-ref
                         :filename filename
                         :error_message (or (.getMessage e) (str (class e)))}
                  (seq details) (assoc :error_details details))))
            extract-result))))))

(defn maybe-refine-review-required
  "Run Cerebras refine on a review-required result, then re-persist if improved."
  [db receipt extract-result persist-result opts]
  (let [review-required? (true? (:review-required? persist-result))
        auto-post-present? (contains? persist-result :auto-post-after-upload?)
        auto-post-after-upload? (:auto-post-after-upload? persist-result)
        skip-auto-post? (true? (:skip-auto-post? opts))]
    (if (and receipt review-required?)
      (let [refined (maybe-refine-with-cerebras db receipt extract-result opts)]
        (if (map? (:llm_refine refined))
          (let [persist-opts (if skip-auto-post?
                               (assoc opts :auto-post-after-upload? false)
                               opts)
                updated (extraction/persist-extract-result! db (:id receipt) refined persist-opts)]
            (when (and db (:id receipt))
              (store-receipt-refine-context-best-effort! db (:id receipt)))
            (cond-> (assoc updated :receipt receipt :extract-result refined)
              auto-post-present? (assoc :auto-post-after-upload? auto-post-after-upload?)))
          (do
            (when (and db (true? (:clear-refine-pending? opts)))
              (try
                (receipt-status/clear-refine-pending! db (:id receipt))
                (catch Exception e
                  (log/warn e "Failed to clear refine_pending" {:receipt-id (:id receipt)}))))
            persist-result)))
      persist-result)))

(defn- result-receipt-id
  [result]
  (or (:receipt-id result)
    (some-> result :receipt :id)))

(defn strip-refine-metadata
  "Remove transient refine context keys from a result map."
  [result]
  (dissoc result :receipt :extract-result))

(defn refine-review-required-results!
  "Run parallel Cerebras refine for all review-required results in the batch."
  [db opts results]
  (let [batch-id (str (java.util.UUID/randomUUID))
        refine-opts (assoc (dissoc opts :defer-refine? :skip-auto-post?)
                      :skip-auto-post? true
                      :clear-refine-pending? true
                      :refine-batch-id batch-id)
        cerebras-cfg (:cerebras-cfg opts)
        max-concurrent (long (max 1 (or (:refine-concurrency cerebras-cfg) 5)))
        timeout-ms (long (or (:refine-timeout-ms cerebras-cfg)
                           (:socket-timeout-ms cerebras-cfg)
                           60000))
        refineable (->> results
                     (keep (fn [result]
                             (let [receipt (:receipt result)
                                   extract-result (:extract-result result)
                                   receipt-id (result-receipt-id result)]
                               (when (and receipt-id receipt extract-result (true? (:review-required? result)))
                                 {:receipt-id receipt-id
                                  :receipt receipt
                                  :extract-result extract-result
                                  :result result})))))
        total (count refineable)
        review-required-count (count (filter (fn [result]
                                               (true? (:review-required? result)))
                                       results))
        missing-context (long (max 0 (- review-required-count total)))
        sample-ids (->> refineable (map :receipt-id) (take 5) vec)
        eligible-refine-count (if db
                                (->> refineable
                                  (filter (fn [{:keys [receipt]}]
                                            (receipt-eligible-for-refine? db receipt refine-opts)))
                                  count)
                                0)
        has-eligible-refine? (pos? eligible-refine-count)]
    (if (zero? total)
      (do
        (when (pos? review-required-count)
          (log/info "Cerebras parallel refine skipped"
            {:batch-id batch-id
             :review-required-count review-required-count
             :missing-context missing-context
             :total-results (count results)
             :eligible-refine-count eligible-refine-count}))
        results)
      (let [started (System/nanoTime)
            pool (Executors/newFixedThreadPool max-concurrent)]
        (try
          (if has-eligible-refine?
            (log/info "Cerebras parallel refine starting"
              {:batch-id batch-id
               :count total
               :eligible-refine-count eligible-refine-count
               :max-concurrent max-concurrent
               :timeout-ms timeout-ms
               :receipt-ids-sample sample-ids
               :receipt-ids-sample-count (count sample-ids)})
            (log/info "Review-required post-process starting (no eligible refine)"
              {:batch-id batch-id
               :count total
               :eligible-refine-count eligible-refine-count
               :max-concurrent max-concurrent
               :timeout-ms timeout-ms
               :receipt-ids-sample sample-ids
               :receipt-ids-sample-count (count sample-ids)}))
          (let [futures (mapv
                          (fn [{:keys [receipt-id receipt extract-result result]}]
                            {:receipt-id receipt-id
                             :future (.submit pool
                                       ^java.util.concurrent.Callable
                                       (fn []
                                         [receipt-id (maybe-refine-review-required db receipt extract-result result refine-opts)]))})
                          refineable)
                refine-map (->> futures
                             (map (fn [{:keys [receipt-id future]}]
                                    (try
                                      (.get future timeout-ms TimeUnit/MILLISECONDS)
                                      (catch TimeoutException e
                                        (try (.cancel future true) (catch Exception _))
                                        (when db
                                          (try
                                            (receipt-status/clear-refine-pending! db receipt-id)
                                            (catch Exception ce
                                              (log/warn ce "Failed to clear refine_pending after timeout"
                                                {:batch-id batch-id
                                                 :receipt-id receipt-id}))))
                                        (log/warn e "Cerebras refine timed out (parallel)"
                                          {:batch-id batch-id
                                           :receipt-id receipt-id
                                           :timeout-ms timeout-ms})
                                        nil)
                                      (catch Exception e
                                        (when db
                                          (try
                                            (receipt-status/clear-refine-pending! db receipt-id)
                                            (catch Exception ce
                                              (log/warn ce "Failed to clear refine_pending after failure"
                                                {:batch-id batch-id
                                                 :receipt-id receipt-id}))))
                                        (log/warn e "Cerebras refine failed (parallel)"
                                          {:batch-id batch-id
                                           :receipt-id receipt-id})
                                        nil))))
                             (keep identity)
                             (into {}))
                results* (mapv (fn [result]
                                 (let [receipt-id (result-receipt-id result)]
                                   (if receipt-id
                                     (get refine-map receipt-id result)
                                     result)))
                           results)
                refined-count (count (filter (fn [result]
                                               (map? (get-in result [:extract-result :llm_refine])))
                                       results*))
                duration-ms (/ (- (System/nanoTime) started) 1000000.0)]
            (if has-eligible-refine?
              (log/info "Cerebras parallel refine complete"
                {:batch-id batch-id
                 :count (count refine-map)
                 :total-count total
                 :eligible-refine-count eligible-refine-count
                 :refined-count refined-count
                 :duration-ms duration-ms})
              (log/info "Review-required post-process complete (no eligible refine)"
                {:batch-id batch-id
                 :count (count refine-map)
                 :total-count total
                 :eligible-refine-count eligible-refine-count
                 :refined-count refined-count
                 :duration-ms duration-ms}))
            results*)
          (finally
            (.shutdown pool)
            (.awaitTermination pool 10 TimeUnit/SECONDS)))))))

(defn auto-post-extracted-results!
  "Persist final extraction results for all non-review-required receipts after defer-refine runs.

  This final pass writes aliases from the settled extraction and optionally auto-posts
  when that feature is enabled."
  [db opts results]
  (let [post-opts (assoc (dissoc opts :defer-refine? :skip-auto-post?)
                    :persist-item-aliases? true)
        post-map (->> results
                   (keep (fn [result]
                           (let [receipt-id (result-receipt-id result)
                                 receipt (:receipt result)
                                 extract-result (:extract-result result)
                                 auto-post? (if (contains? result :auto-post-after-upload?)
                                              (:auto-post-after-upload? result)
                                              (get opts :auto-post-after-upload? true))]
                             (when (and receipt-id receipt extract-result
                                     (= :ok (:result result))
                                     (= :extract (:stage result))
                                     (not (:review-required? result)))
                               (let [updated (extraction/persist-extract-result!
                                               db
                                               receipt-id
                                               extract-result
                                               (assoc post-opts :auto-post-after-upload? auto-post?))]
                                 [receipt-id updated])))))
                   (into {}))]
    (mapv (fn [result]
            (let [receipt-id (result-receipt-id result)]
              (if (and receipt-id (contains? post-map receipt-id))
                (get post-map receipt-id)
                result)))
      results)))
