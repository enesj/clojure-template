#!/usr/bin/env bb

;; Batch article research: local heuristics + optional Perplexity web search.
;;
;; Reads unmapped aliases from DB, applies local resolution heuristics,
;; optionally batches unresolved items to Perplexity sonar-pro for web research,
;; and outputs draft articles.edn + mappings.edn files for create_articles.clj
;; and map_aliases.clj.
;;
;; Usage:
;;   bb articles-research [dev|test|prod] [options]
;;   bb scripts/bb/articles/articles_research.clj dev --skip-research --pretty
;;
;; Output files (in tmp/):
;;   articles-suggested.edn  → feed to create_articles.clj --articles-file
;;   mappings-suggested.edn  → feed to map_aliases.clj --mappings-file
;;   noise-candidates.edn    → review, then delete_unmapped_aliases.clj
;;   research-summary.edn    → overview stats
;;   needs-research.edn      → (only with --skip-research) items requiring web search

(ns scripts.bb.articles.articles-research
  (:require
    [articles.db :as db]
    [articles.research.heuristics :as heuristics]
    [articles.research.noise :as noise]
    [articles.research.output :as output]
    [articles.research.perplexity :as perplexity]
    [articles.research.validation :as validation]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str]))

;; ============================================================
;; Cross-supplier deduplication
;; ============================================================

(def ^:private default-unit "kom")

(defn- normalize-unit
  [unit]
  (or (some-> unit str str/trim str/lower-case not-empty)
    default-unit))

(defn- article-key+unit
  [label unit]
  [(db/normalize-key label) (normalize-unit unit)])

(defn- group-aliases
  "Group unmapped aliases by raw_label_normalized + unit so weighted and piece-count variants stay separate.
  Returns a vec of maps sorted by alias count descending."
  [aliases]
  (->> aliases
    (group-by (fn [{:keys [raw_label_normalized unit]}]
                [raw_label_normalized (normalize-unit unit)]))
    (map (fn [[[norm-label unit] items]]
           {:raw-label-normalized norm-label
            :raw-label (:raw_label (first items))
            :unit unit
            :suppliers (vec (distinct (keep :supplier items)))
            :alias-ids (mapv :id items)}))
    (sort-by (comp - count :alias-ids))
    vec))

;; ============================================================
;; CLI parsing
;; ============================================================

(def ^:private default-batch-size 15)

(defn- die! [msg]
  (binding [*out* *err*] (println msg))
  (System/exit 2))

(defn- usage []
  (println "Batch article research: local heuristics + Perplexity web search.")
  (println "")
  (println "Usage:")
  (println "  bb articles-research [dev|test|prod] [options]")
  (println "  bb scripts/bb/articles/articles_research.clj dev --skip-research --pretty")
  (println "")
  (println "Options:")
  (println "  --skip-research       Only run local heuristics (no Perplexity API)")
  (println "  --merge               Merge new articles/mappings INTO existing tmp/ files instead of overwriting")
  (println "  --batch-size N        Aliases per Perplexity batch (default: 15)")
  (println "  --supplier PATTERN    Only process aliases from matching suppliers")
  (println "  --output-prefix PFX   Prefix output files: tmp/{pfx}-articles-suggested.edn etc.")
  (println "  --limit N             Max alias groups to process")
  (println "  --pretty              Pretty-print output")
  (println "  --help                Show this help")
  (println "")
  (println "Output files (in tmp/):")
  (println "  articles-suggested.edn    → create_articles.clj --articles-file")
  (println "  mappings-suggested.edn    → map_aliases.clj --mappings-file")
  (println "  noise-candidates.edn      → review + delete_unmapped_aliases.clj")
  (println "  research-summary.edn      → overview stats")
  (println "  needs-research.edn        → (--skip-research only) items needing web search"))

(defn- parse-args [args]
  (loop [args (seq args)
         opts {:skip-research? false
               :merge? false
               :batch-size default-batch-size
               :supplier-filter nil
               :output-prefix nil
               :limit nil
               :pretty? false}]
    (if-not args
      opts
      (let [[a b & more] args]
        (cond
          (or (= a "--help") (= a "-h")) (do (usage) (System/exit 0))
          (= a "--skip-research") (recur (next args) (assoc opts :skip-research? true))
          (= a "--merge")         (recur (next args) (assoc opts :merge? true))
          (= a "--pretty")        (recur (next args) (assoc opts :pretty? true))
          (= a "--batch-size")    (recur more (assoc opts :batch-size (or (db/parse-long b) default-batch-size)))
          (= a "--supplier")      (recur more (assoc opts :supplier-filter b))
          (= a "--output-prefix") (recur more (assoc opts :output-prefix b))
          (= a "--limit")         (recur more (assoc opts :limit (db/parse-long b)))
          :else (die! (str "Unknown arg: " a)))))))

;; ============================================================
;; DB queries
;; ============================================================

(defn- fetch-unmapped-aliases [db]
  (db/query db
    (str "SELECT aa.id, aa.raw_label, aa.raw_label_normalized, aa.unit,\n"
      "       aa.supplier_id, s.display_name AS supplier\n"
      "FROM article_aliases aa\n"
      "LEFT JOIN suppliers s ON aa.supplier_id = s.id\n"
      "WHERE aa.article_id IS NULL AND " db/not-settled-noise-sql "\n"
      "ORDER BY s.display_name NULLS LAST, aa.raw_label")))

(defn- fetch-category-names [db]
  (->> (db/query db "SELECT name FROM categories ORDER BY name")
    (mapv :name)))

(defn- fetch-existing-article-keys
  [db]
  (->> (db/query db "SELECT normalized_key, unit FROM articles")
    (map (fn [{:keys [normalized_key unit]}]
           [normalized_key (normalize-unit unit)]))
    set))

(defn- fetch-subcategory-map
  "Fetch existing subcategories grouped by category name.
  Returns {\"Category\" [\"Subcat1\" \"Subcat2\" ...]}."
  [db]
  (->> (db/query db
         (str "SELECT c.name AS category_name, sc.name AS subcategory_name\n"
           "FROM subcategories sc\n"
           "JOIN categories c ON sc.category_id = c.id\n"
           "ORDER BY c.name, sc.name"))
    (group-by :category_name)
    (reduce-kv (fn [m k v] (assoc m k (mapv :subcategory_name v))) {})))

;; ============================================================
;; Main
;; ============================================================

(defn- log [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn -main [& args]
  (let [{:keys [profile args]} (db/parse-profile args)
        opts (parse-args args)
        {:keys [skip-research? merge? batch-size supplier-filter output-prefix limit pretty?]} opts

        config (db/read-config profile)
        db (:database config)

        ;; File prefix for output files
        file-prefix (if output-prefix (str output-prefix "-") "")

        ;; 1. Fetch data from DB
        _ (log "Fetching unmapped aliases...")
        raw-aliases (fetch-unmapped-aliases db)
        _ (log "  " (count raw-aliases) "unmapped aliases")

        db-category-names (fetch-category-names db)
        subcategory-map (fetch-subcategory-map db)
        existing-keys (fetch-existing-article-keys db)
        all-known-subcats (set (mapcat val subcategory-map))

        ;; 2. Group by raw_label_normalized (dedup across suppliers)
        alias-groups (group-aliases raw-aliases)
        _ (log "  " (count alias-groups) "unique alias groups (after cross-supplier dedup)")

        ;; Apply supplier filter
        alias-groups (if supplier-filter
                       (filterv (fn [{:keys [suppliers]}]
                                  (some #(re-find (re-pattern (str "(?i)" supplier-filter)) (or % ""))
                                    suppliers))
                         alias-groups)
                       alias-groups)
        _ (when supplier-filter
            (log "  " (count alias-groups) "groups after supplier filter"))

        ;; Apply limit
        alias-groups (if limit (vec (take limit alias-groups)) alias-groups)

        ;; 3. Detect OCR noise
        noise-groups (filterv (fn [{:keys [raw-label raw-label-normalized]}]
                                (noise/ocr-noise-reason {:raw_label raw-label
                                                         :raw_label_normalized raw-label-normalized}))
                       alias-groups)
        clean-groups (vec (remove (set noise-groups) alias-groups))
        _ (log "  " (count noise-groups) "noise candidates")

        ;; 4. Match against existing articles
        already-matched (filterv (fn [{:keys [raw-label unit]}]
                                   (contains? existing-keys (article-key+unit raw-label unit)))
                          clean-groups)
        remaining (vec (remove (set already-matched) clean-groups))
        _ (when (seq already-matched)
            (log "  " (count already-matched) "match existing articles"))

        ;; 5. Local heuristic resolution
        locally-resolved (atom [])
        needs-research (atom [])
        _ (doseq [group remaining]
            (if-let [resolution (heuristics/resolve-locally (:raw-label group) (first (:suppliers group)))]
              (swap! locally-resolved conj (merge group resolution))
              (swap! needs-research conj group)))
        _ (log "  " (count @locally-resolved) "resolved locally")
        _ (log "  " (count @needs-research) "need web research")

        ;; 6. Perplexity batch research
        researched (if (or skip-research? (empty? @needs-research))
                     (do
                       (when (and skip-research? (seq @needs-research))
                         (log "  Skipping research (--skip-research). See tmp/needs-research.edn"))
                       [])
                     (let [batches (partition-all batch-size @needs-research)
                           total (count batches)]
                       (log "Running Perplexity research (" total "batches of up to" batch-size ")...")
                       (->> batches
                         (map-indexed
                           (fn [idx batch]
                             (log "  Batch" (str (inc idx) "/" total) "...")
                             (try
                               (let [result (perplexity/research-batch! (vec batch) db-category-names subcategory-map)]
                                 (when (< (inc idx) total)
                                   (Thread/sleep 1000))
                                 result)
                               (catch Exception e
                                 (log "  ERROR in batch" (str (inc idx) ":") (ex-message e))
                                 (mapv #(assoc % :resolution :failed :confidence :low) batch)))))
                         (apply concat)
                         vec)))

        ;; 7. Combine all resolved items
        all-resolved (vec (concat @locally-resolved researched))
        successful (filterv #(not= :failed (:resolution %)) all-resolved)
        failed (filterv #(= :failed (:resolution %)) all-resolved)

        ;; 7b. Learning: append new brand discoveries from Perplexity to brand-rules.edn
        learned-count (if (seq researched)
                        (output/learn-brand-rules! researched)
                        0)
        _ (when (pos? learned-count)
            (log "  " learned-count "new brand rules learned and saved to taxonomy"))

        ;; 8. Post-process: sanitize manufacturers, translate categories, validate subcategories
        successful (mapv (fn [s]
                           (-> s
                             (update :manufacturer-name validation/sanitize-manufacturer)
                             (update :category-name validation/translate-category db-category-names)
                             (update :category-name validation/find-best-category db-category-names)
                             (#(update % :subcategory-name validation/sanitize-subcategory (:category-name %) subcategory-map))))
                     successful)

        ;; 8b. Validate subcategories — warn about unknown ones
        unknown-subcats (->> successful
                          (keep (fn [{:keys [subcategory-name category-name]}]
                                  (when (and subcategory-name
                                          (not (contains? all-known-subcats subcategory-name))
                                          (not= subcategory-name "Opste"))
                                    {:subcategory subcategory-name :category category-name})))
                          distinct
                          vec)
        _ (when (seq unknown-subcats)
            (log "  " (count unknown-subcats) "new subcategories will be created"))

        ;; 9. Build output — deduplicate articles by normalized key + unit
        seen-keys (atom #{})
        articles-edn (reduce (fn [acc s]
                               (let [k (article-key+unit (:canonical-name s) (:unit s))]
                                 (if (contains? @seen-keys k)
                                   acc
                                   (do (swap! seen-keys conj k)
                                     (conj acc (output/suggestion->article-edn s))))))
                       []
                       successful)

        ;; 10. Build mappings (for all successful + existing matches)
        mappings-from-new (->> successful (mapcat output/suggestion->mapping-entries) vec)
        mappings-from-existing (->> already-matched
                                 (mapcat (fn [{:keys [alias-ids raw-label unit]}]
                                           (let [k (db/normalize-key raw-label)
                                                 unit* (normalize-unit unit)]
                                             (mapv (fn [aid]
                                                     {:alias-id (str aid)
                                                      :article-key k
                                                      :unit unit*})
                                               alias-ids))))
                                 vec)
        all-mappings (vec (concat mappings-from-new mappings-from-existing))

        ;; 11. Build noise list
        noise-edn (->> noise-groups
                    (mapv (fn [{:keys [alias-ids raw-label raw-label-normalized]}]
                            {:raw-label raw-label
                             :alias-ids (mapv str alias-ids)
                             :alias-count (count alias-ids)
                             :reason (noise/ocr-noise-reason {:raw_label raw-label
                                                              :raw_label_normalized raw-label-normalized})})))

        ;; 12. Near-duplicate detection
        duplicates (validation/detect-near-duplicates articles-edn)
        _ (when (seq duplicates)
            (log "  WARNING:" (count duplicates) "potential near-duplicate groups detected"))

        summary (cond-> {:generated-at           (str (java.time.Instant/now))
                         :profile                (name profile)
                         :total-aliases           (count raw-aliases)
                         :unique-groups           (count alias-groups)
                         :noise-candidates        (count noise-groups)
                         :noise-alias-count       (reduce + (map (comp count :alias-ids) noise-groups))
                         :existing-matches        (count already-matched)
                         :locally-resolved        (count @locally-resolved)
                         :perplexity-researched   (count researched)
                         :research-failed         (count failed)
                         :still-needs-research    (if skip-research? (count @needs-research) 0)
                         :articles-suggested      (count articles-edn)
                         :mappings-suggested      (count all-mappings)
                         :near-duplicates         (count duplicates)
                         :new-subcategories       (count unknown-subcats)
                         :brand-rules-learned     learned-count
                         :skip-research?          skip-research?}
                  (seq unknown-subcats)
                  (assoc :new-subcategory-list (mapv :subcategory unknown-subcats)))]

    ;; Write output files
    (.mkdirs (io/file "tmp"))
    (let [f (fn [name] (str "tmp/" file-prefix name))
          read-existing-edn (fn [path]
                              (let [file (io/file path)]
                                (when (and merge? (.exists file))
                                  (try (edn/read-string (slurp file))
                                    (catch Exception _ nil)))))
          ;; Merge articles: keep existing entries, append only new key+unit pairs
          existing-articles (or (read-existing-edn (f "articles-suggested.edn")) [])
          existing-article-keys (set (map #(article-key+unit (:canonical-name %) (:unit %)) existing-articles))
          new-articles (filterv #(not (contains? existing-article-keys (article-key+unit (:canonical-name %) (:unit %)))) articles-edn)
          final-articles (vec (concat existing-articles new-articles))
          ;; Merge mappings: keep existing entries, append only new alias-ids
          existing-mappings (or (read-existing-edn (f "mappings-suggested.edn")) [])
          existing-alias-ids (set (map :alias-id existing-mappings))
          new-mappings (filterv #(not (contains? existing-alias-ids (:alias-id %))) all-mappings)
          final-mappings (vec (concat existing-mappings new-mappings))]
      (when merge?
        (log "  [--merge] articles: kept" (count existing-articles) "+ added" (count new-articles))
        (log "  [--merge] mappings: kept" (count existing-mappings) "+ added" (count new-mappings)))
      (spit (f "articles-suggested.edn")  (with-out-str (pprint/pprint final-articles)))
      (spit (f "mappings-suggested.edn")  (with-out-str (pprint/pprint final-mappings)))
      (spit (f "noise-candidates.edn")    (with-out-str (pprint/pprint noise-edn)))
      (spit (f "research-summary.edn")    (with-out-str (pprint/pprint summary)))

      ;; Write near-duplicate warnings
      (when (seq duplicates)
        (spit (f "duplicate-warnings.edn") (with-out-str (pprint/pprint duplicates))))

      ;; Write needs-research list when skipping research
      (when (and skip-research? (seq @needs-research))
        (spit (f "needs-research.edn")
          (with-out-str
            (pprint/pprint
              (mapv (fn [{:keys [raw-label suppliers alias-ids]}]
                      {:raw-label raw-label
                       :suppliers suppliers
                       :alias-count (count alias-ids)})
                @needs-research)))))

      (log "")
      (log "Output written to tmp/")
      (log (str "  " file-prefix "articles-suggested.edn  (" (count final-articles) " articles)"))
      (log (str "  " file-prefix "mappings-suggested.edn  (" (count final-mappings) " mappings)"))
      (log (str "  " file-prefix "noise-candidates.edn    (" (count noise-edn) " noise entries)"))
      (when (seq duplicates)
        (log (str "  " file-prefix "duplicate-warnings.edn  (" (count duplicates) " groups)")))
      (when (and skip-research? (seq @needs-research))
        (log (str "  " file-prefix "needs-research.edn      (" (count @needs-research) " items)"))))

    ;; Print summary to stdout
    (if pretty?
      (db/pprint-edn summary)
      (db/prn-edn summary))))

(apply -main *command-line-args*)
