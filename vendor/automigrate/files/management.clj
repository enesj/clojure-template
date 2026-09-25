(ns automigrate.files.management
  "File operations, migration file handling, and validation"
  (:require
   [automigrate.models :as models]
   [automigrate.util.file :as file-util]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [slingshot.slingshot :refer [throw+]]))

(defn get-migration-number
  [migration-name]
  (-> (str/split migration-name #"_")
    (first)
    (Integer/parseInt)))

(defn validate-migration-numbers
  [migrations]
  (let [duplicated-numbers (->> migrations
                             (map get-migration-number)
                             (frequencies)
                             (filter #(> (val %) 1))
                             (keys)
                             (set))]
    (when (seq duplicated-numbers)
      (throw+ {:type ::duplicated-migration-numbers
               :numbers duplicated-numbers
               :message (str "There are duplicated migration numbers: "
                          (str/join ", " duplicated-numbers)
                          ". Please resolve the conflict and try again.")}))
    migrations))

(defn migrations-list
  "Get migrations' files list."
  [migrations-dir]
  (->> migrations-dir
    (file-util/list-files)
    (mapv file-util/file-url->file-name)
    (sort)
    (validate-migration-numbers)))

(defn read-consolidated-edn
  "Read a single consolidated EDN file if it exists, return empty map if not"
  [file-path]
  (println "file-path: " file-path)
  (if (.exists (io/file file-path))
    (edn/read-string (slurp file-path))
    {}))

(defn read-hierarchical-edn
  "Read EDN files from hierarchical structure (template/domain/*/shared) and merge them"
  [base-path file-name]
  (let [template-path (str base-path "/template/" file-name)
        shared-path (str base-path "/shared/" file-name)

        ;; Dynamically discover all domain directories
        domain-base-path (str base-path "/domain")
        domain-dirs (when (.exists (io/file domain-base-path))
                      (let [files (.listFiles (io/file domain-base-path))]
                        (when files  ; Add null check to prevent getName() on null
                          (->> files
                            (filter identity)  ; Remove any null files first
                            (filter #(.isDirectory %))
                            (map #(when % (.getName %)))
                            (filter identity)))))

        ;; Read template and shared data
        template-data (read-consolidated-edn template-path)
        shared-data (read-consolidated-edn shared-path)

        ;; Read all domain data
        domain-data (reduce (fn [acc domain-name]
                              (let [domain-path (str domain-base-path "/" domain-name "/" file-name)
                                    data (read-consolidated-edn domain-path)]
                                (merge acc data)))
                      {}
                      domain-dirs)]

    (println "Domain directories found:" domain-dirs)
    (merge template-data domain-data shared-data)))

(defn read-models-hierarchical
  "Read models from hierarchical structure (template, domain, shared)"
  [base-path]
  (let [models-data (read-hierarchical-edn base-path "models.edn")]
    (models/->internal-models models-data)))

(defn validate-edn-syntax
  "Validate EDN file syntax"
  [filepath]
  (try
    (edn/read-string (slurp filepath))
    true
    (catch Exception e
      {:error (.getMessage e)
       :file filepath})))

(defn find-edn-files
  "Find all EDN files in directory structure"
  [base-dir]
  (->> (file-seq (io/file base-dir))
    (filter #(.isFile %))
    (filter #(str/ends-with? (.getName %) ".edn"))
    (map #(.getAbsolutePath %))))

(defn get-migration-type
  "Return migration type by migration file extension as a keyword.
  Supported: :edn, :sql, :fn, :trg, :pol, :view."
  [migration-name]
  (let [parts (str/split migration-name #"\.")
        ext (some-> parts last str/lower-case)]
    (case ext
      "edn" :edn
      "sql" :sql
      "fn"  :fn
      "trg" :trg
      "pol" :pol
      "view" :view
      nil)))

(defn get-migration-name
  "Extracts the base name of the migration (e.g., 'create_user') from a filename."
  [migration-filename]
  (let [match (re-find #"^\d+_(?:function|trigger|policy|view|drop_function|drop_trigger|drop_policy|drop_view)_(.+)\.(?:fn|trg|pol|view)$" migration-filename)]
    (when match
      (second match))))

(defn drop-migration-file?
  "Return true when the migration file is an auto-generated drop_* extended migration."
  [migration-filename]
  (boolean (re-find #"^\d+_drop_(?:function|trigger|policy|view)_" migration-filename)))

(defn- active-migrations-by-name
  "Return a name->source-migration map for currently active extended migrations.

  We walk migration history in order, adding normal create migrations and removing
  names when a generated drop_* migration appears. This reflects the net object
  state implied by migration history rather than just the presence of filenames."
  [existing-migrations type]
  (reduce (fn [active migration-file]
            (let [migration-name (get-migration-name migration-file)]
              (cond
                (not= type (get-migration-type migration-file))
                active

                (nil? migration-name)
                active

                (drop-migration-file? migration-file)
                (dissoc active migration-name)

                :else
                (assoc active migration-name migration-file))))
    {}
    existing-migrations))

(defn filter-new-edn-items
  "Filter out EDN items that already have migrations. Type must be one of
  :fn, :trg, :pol, :view."
  [edn-map type existing-migrations]
  (let [existing-names (-> existing-migrations
                         (active-migrations-by-name type)
                         (keys)
                         (set))]
    (reduce-kv (fn [m k v]
                 (if (and k (existing-names (name k)))
                   m
                   (assoc m k v)))
      {}
      edn-map)))

(defn find-orphaned-migrations
  "Find migrations that exist but their corresponding EDN definitions have been deleted.
  Type must be one of :fn, :trg, :pol, :view."
  [existing-migrations edn-map type]
  (let [current-names (set (map name (filter identity (keys edn-map))))
        migrations-for-type (vals (active-migrations-by-name existing-migrations type))]
    (filter #(not (contains? current-names (get-migration-name %)))
      migrations-for-type)))

(defn next-migration-number
  [file-names]
  ; migration numbers starting from 1
  (file-util/zfill (inc (count file-names))))

(defn get-migration-name-from-filename
  "Return migration name without file format."
  [file-name]
  (first (str/split file-name #"\.")))

(defn auto-migration?
  "Return true if migration has been created automatically false otherwise."
  [file-url]
  (let [ext ".edn"
        file-name (file-util/file-url->file-name file-url)]
    (str/ends-with? file-name ext)))
