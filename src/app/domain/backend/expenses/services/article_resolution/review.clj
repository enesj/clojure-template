(ns app.domain.backend.expenses.services.article-resolution.review
  "Evaluation helpers for the dry-run phase: export a review sheet of needs_review groups,
  score the reviewer's verdicts, and check the catalog for near-duplicate articles."
  (:require
    [app.domain.backend.expenses.integrations.claude :as claude]
    [app.domain.backend.expenses.services.article-resolution.catalog :as catalog]
    [app.domain.backend.expenses.services.article-resolution.config :as resolver-config]
    [app.domain.backend.expenses.services.article-resolution.heuristics :as heuristics]
    [app.domain.backend.expenses.services.article-resolution.prompt :as prompt]
    [app.domain.backend.expenses.services.article-resolution.taxonomy :as taxonomy]
    [app.domain.backend.expenses.services.article-resolution.validation :as validation]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(def verdicts
  "Allowed reviewer verdicts."
  #{:ok :wrong-name :wrong-category :wrong-subcategory :wrong-manufacturer
    :should-be-noise :should-not-be-noise :wrong-existing-match})

(def prices-per-million
  "USD per million tokens (PLAN Appendix D). Sonnet cache write is assumed at 1.25x input."
  {"claude-opus-5-5"   {:input 4.0 :output 20.0 :cache-read 0.20 :cache-write 5.0}
   "claude-sonnet-5-5" {:input 2.0 :output 10.0 :cache-read 0.20 :cache-write 2.5}})

(defn- parse-proposal
  [text]
  (when text (json/parse-string text true)))

(defn review-entries
  "One entry per (raw_label_normalized, unit) group of resolution rows in `status`
  (default needs_review): {:resolution-id :label :unit :stores :proposal :auto-applicable? :problems :verdict nil}."
  ([db] (review-entries db "needs_review"))
  ([db status]
   (->> (jdbc/execute! db
          [(str "SELECT r.id AS resolution_id, aa.raw_label, aa.raw_label_normalized, aa.unit,"
             "       s.display_name AS supplier, r.proposal::text AS proposal"
             " FROM article_alias_resolutions r"
             " JOIN article_aliases aa ON aa.id = r.alias_id"
             " LEFT JOIN suppliers s ON s.id = aa.supplier_id"
             " WHERE r.status = ?::alias_resolution_status"
             " ORDER BY aa.raw_label_normalized, aa.unit, r.id")
           status]
          {:builder-fn rs/as-unqualified-lower-maps})
     (group-by (juxt :raw_label_normalized :unit))
     vals
     (map (fn [rows]
            (let [first-row (first rows)
                  proposal  (parse-proposal (:proposal first-row))]
              {:resolution-id (:resolution_id first-row)
               :label (:raw_label first-row)
               :unit (:unit first-row)
               :stores (vec (sort (distinct (keep :supplier rows))))
               :proposal proposal
               :auto-applicable? (boolean (:auto_applicable proposal))
               :problems (vec (:problems proposal))
               :verdict nil})))
     (sort-by :label)
     vec)))

(defn export-review-sheet!
  "Write the review sheet (EDN vector, pretty printed) to `path`; returns the number of entries."
  ([db path] (export-review-sheet! db path "needs_review"))
  ([db path status]
   (let [entries (review-entries db status)]
     (io/make-parents path)
     (spit path (with-out-str (pprint/pprint entries)))
     (count entries))))

(defn- call-cost
  [model {:keys [input_tokens output_tokens cache_read_input_tokens cache_creation_input_tokens]}]
  (when-let [p (prices-per-million model)]
    (/ (+ (* (or input_tokens 0) (:input p))
         (* (or output_tokens 0) (:output p))
         (* (or cache_read_input_tokens 0) (:cache-read p))
         (* (or cache_creation_input_tokens 0) (:cache-write p)))
      1e6)))

(defn calls-cost
  "Total USD of article_resolution_calls for `model` (optionally since a java.time.Instant)."
  [db model since]
  (let [row (jdbc/execute-one! db
              [(str "SELECT COALESCE(SUM(input_tokens),0) input_tokens, COALESCE(SUM(output_tokens),0) output_tokens,"
                 " COALESCE(SUM(cache_read_input_tokens),0) cache_read_input_tokens,"
                 " COALESCE(SUM(cache_creation_input_tokens),0) cache_creation_input_tokens"
                 " FROM article_resolution_calls WHERE model = ? AND created_at >= COALESCE(?::timestamptz, '-infinity')")
               model (some-> since str)]
              {:builder-fn rs/as-unqualified-lower-maps})]
    (call-cost model row)))

(defn score-entries
  "Pure scoring of reviewed entries. :auto-precision = share of :ok among auto-applicable entries that
  have a verdict; :coverage = auto-applicable / groups. :reviewed counts entries with a verdict."
  [entries]
  (let [reviewed (filter :verdict entries)
        auto     (filter :auto-applicable? entries)
        auto-rev (filter :verdict auto)
        ok-auto  (count (filter #(= :ok (:verdict %)) auto-rev))]
    {:groups (count entries)
     :reviewed (count reviewed)
     :auto-applicable (count auto)
     :auto-reviewed (count auto-rev)
     :auto-precision (when (seq auto-rev) (double (/ ok-auto (count auto-rev))))
     :coverage (when (seq entries) (double (/ (count auto) (count entries))))
     :by-verdict (frequencies (keep :verdict reviewed))}))

(defn score-review-sheet
  "Score a reviewed sheet EDN at `path`. With `{:db db :model m :since inst}` also reports
  :usd-total and :usd-per-group from article_resolution_calls."
  ([path] (score-review-sheet path nil))
  ([path {:keys [db model since]}]
   (let [entries (edn/read-string (slurp path))
         unknown (remove verdicts (keep :verdict entries))
         scores  (score-entries entries)]
     (when (seq unknown)
       (throw (ex-info "Unknown verdicts in sheet" {:unknown (vec (distinct unknown)) :allowed verdicts})))
     (cond-> scores
       (and db model (seq entries))
       (as-> s (let [usd (calls-cost db model since)]
                 (assoc s :usd-total usd :usd-per-group (when usd (/ usd (count entries)))))))))) 

(defn near-duplicates
  "Near-duplicate article groups (same unit, same normalized name without size/punctuation).
  Must be empty or explainable (real size/variant differences) after an :auto run."
  [db]
  (->> (jdbc/execute! db ["SELECT canonical_name, unit FROM articles"] {:builder-fn rs/as-unqualified-lower-maps})
    (group-by :unit)
    (mapcat (fn [[unit articles]]
              (map #(assoc % :unit unit)
                (validation/detect-near-duplicates (map (fn [a] {:canonical-name (:canonical_name a)}) articles)))))
    vec))

(defn- golden-diff
  "Fields where Claude's item differs from the golden :expected (canonical_name compared case-insensitively)."
  [expected item]
  (let [norm #(some-> % str str/lower-case str/trim)]
    (->> [:kind :canonical_name :category :subcategory :manufacturer]
      (remove (fn [k]
                (if (= :canonical_name k)
                  (= (norm (get expected k)) (norm (get item k)))
                  (= (get expected k) (get item k)))))
      vec)))

(defn run-golden!
  "Send the golden set (test/resources/article_resolution/golden.edn) to Claude in one call per
  `batch-size` entries and compare with the reviewed answers. Never writes the catalog. Candidates
  are empty (the golden articles already exist, so candidates would give the answer away); hints
  come from the heuristics. `claude-overrides` are merged into the Claude config (e.g. {:effort \"low\"}).
  Costs one paid call per batch. Returns {:total :exact :diffs [..] :usage {..} :cost-usd}."
  [db app-config claude-overrides]
  (let [cfg      (resolver-config/build-config app-config)
        ccfg     (merge (:claude cfg) claude-overrides)
        tax      (taxonomy/current)
        index    (catalog/category-index db (:excluded-categories cfg))
        golden   (edn/read-string (slurp "test/resources/article_resolution/golden.edn"))
        results  (for [chunk (partition-all (:batch-size cfg) golden)]
                   (let [groups (mapv (fn [{:keys [label unit stores]}]
                                        {:raw-label label :unit unit :stores stores :candidates []
                                         :hint (heuristics/resolve-hint tax (keys index) label (first stores))})
                                  chunk)
                         resp   (claude/create-message! ccfg {:system (prompt/system-blocks index tax)
                                                              :user (prompt/user-message groups)
                                                              :schema (prompt/response-schema index (:batch-size cfg))})
                         items  (into {} (map (juxt :id identity)) (:items (json/parse-string (:text resp) true)))]
                     {:usage (:usage resp)
                      :rows  (map-indexed (fn [i g] [g (get items (str "g" (inc i)))]) chunk)}))
        rows     (mapcat :rows results)
        usage    (apply merge-with + (map #(select-keys (:usage %) [:input_tokens :output_tokens
                                                                    :cache_read_input_tokens
                                                                    :cache_creation_input_tokens])
                                       results))
        diffs    (keep (fn [[g item]]
                         (let [d (if item (golden-diff (:expected g) item) [:missing])]
                           (when (seq d)
                             {:label (:label g) :fields d :expected (:expected g)
                              :got (some-> item (select-keys [:kind :canonical_name :category :subcategory
                                                               :manufacturer :confidence :reason]))})))
                   rows)]
    {:total (count rows)
     :exact (- (count rows) (count diffs))
     :diffs (vec diffs)
     :usage usage
     :cost-usd (call-cost (:model ccfg) usage)}))

(comment
  (def db (:database @system.state/state))
  (export-review-sheet! db "tmp/resolver-review-opus.edn")
  (score-review-sheet "tmp/resolver-review-opus.edn" {:db db :model "claude-opus-5-5"})
  (near-duplicates db)
  ;; paid: one Claude call per batch of the 30 golden entries
  (run-golden! db (:config @system.state/state) {})
  (run-golden! db (:config @system.state/state) {:effort "low"})
  :rcf)
