(ns app.domain.backend.expenses.services.article-resolution.taxonomy
  "Read-only taxonomy loader for the article resolver.
  Files live in resources/articles/taxonomy/ and are written only by the bb skills
  (learning loop); the backend never writes them. The cache reloads when a file's
  lastModified changed, so skill edits reach a running system without a restart."
  (:require
    [app.domain.backend.expenses.services.article-resolution.heuristics :as heuristics]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [taoensso.timbre :as log]))

(def ^:private files
  ["brand-rules.edn" "brand-parent-mappings.edn" "self-named-brands.edn"
   "keyword-category-hints.edn" "meat-words.edn" "supplier-hints.edn"
   "seed-subcategories.edn"])

(defn- load-edn
  [filename]
  (some-> (io/resource (str "articles/taxonomy/" filename)) slurp edn/read-string))

(defn- safe-pattern
  "Compile a regex string; nil (and a warning) when invalid."
  [pattern]
  (try
    (re-pattern pattern)
    (catch Exception e
      (log/warn "Skipping invalid taxonomy pattern" {:pattern pattern :error (.getMessage e)})
      nil)))

(defn- load-taxonomy
  []
  {:brand-rules            (->> (or (load-edn "brand-rules.edn") [])
                             (remove heuristics/bad-brand-rule?)
                             (keep (fn [[pattern mfr cat subcat]]
                                     (when-let [re (safe-pattern pattern)]
                                       {:re re :manufacturer mfr :category cat :subcategory subcat})))
                             vec)
   :brand-parent-mappings  (or (load-edn "brand-parent-mappings.edn") {})
   :self-named-brands      (vec (sort (or (load-edn "self-named-brands.edn") [])))
   :keyword-category-hints (or (load-edn "keyword-category-hints.edn") {})
   :meat-words             (set (or (load-edn "meat-words.edn") []))
   :supplier-hints         (->> (or (load-edn "supplier-hints.edn") [])
                             (keep (fn [[pattern category]]
                                     (when-let [re (safe-pattern pattern)]
                                       {:re re :category category})))
                             vec)
   :seed-subcategories     (or (load-edn "seed-subcategories.edn") {})})

(defn- files-stamp
  "Max lastModified of the taxonomy files; 0 when not on the file system (e.g. inside a jar)."
  []
  (reduce max 0
    (for [f files]
      (try (or (some-> (io/resource (str "articles/taxonomy/" f)) io/file .lastModified) 0)
           (catch Exception _ 0)))))

(defonce ^:private cache (atom nil)) ; {:stamp long :value map}

(defn current
  "Read-only taxonomy map. Reloads when a file changed since the last call. Never writes files."
  []
  (let [stamp  (files-stamp)
        cached @cache]
    (if (and cached (= stamp (:stamp cached)))
      (:value cached)
      (:value (reset! cache {:stamp stamp :value (load-taxonomy)})))))

(comment
  (keys (current))
  (count (:brand-rules (current)))
  :rcf)
