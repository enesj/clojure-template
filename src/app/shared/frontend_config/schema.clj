(ns app.shared.frontend-config.schema
  "DB schema index and allowlist handling utilities.

  Loads models.edn and provides normalized schema lookups,
  plus allowlist normalization for field validation."
  (:require
    [clojure.set :as set]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [app.shared.frontend-config.discovery :as discovery]))

(defn- read-models-edn-or-empty
  "Read an EDN file if it exists; otherwise return {}.

  We intentionally treat missing hierarchical source files as empty so callers
  can support partial setups (e.g., no domain models yet) while still allowing
  a single directory path to represent the canonical model inputs." 
  [path]
  (let [f (io/file path)]
    (if (and f (.exists f))
      (discovery/read-edn-file path)
      {})))

(defn- domain-subdirs
  "Return a seq of domain subdirectory names under `<base>/domain/`.

  Supports the optional layout:
  - resources/db/domain/<domain-name>/models.edn"
  [^java.io.File base-dir]
  (let [domain-dir (io/file base-dir "domain")
        files (when (and domain-dir (.exists domain-dir) (.isDirectory domain-dir))
                (.listFiles domain-dir))]
    (->> (or files [])
      (filter (fn [^java.io.File f]
                (and f (.isDirectory f))))
      (map #(.getName ^java.io.File %))
      (remove nil?)
      sort)))

(defn- read-hierarchical-models
  "Read models.edn from a hierarchical `resources/db/` directory structure.

  Sources (all optional):
  - <base>/template/models.edn
  - <base>/domain/models.edn
  - <base>/domain/*/models.edn
  - <base>/shared/models.edn

  Merge order matches the migration pipeline (template → domain → shared)."
  [^java.io.File base-dir]
  (let [template-path (str (io/file base-dir "template" "models.edn"))
        domain-direct-path (str (io/file base-dir "domain" "models.edn"))
        shared-path (str (io/file base-dir "shared" "models.edn"))
        template-data (read-models-edn-or-empty template-path)
        domain-direct-data (read-models-edn-or-empty domain-direct-path)
        domain-subdir-data (reduce
                             (fn [acc domain-name]
                               (let [p (str (io/file base-dir "domain" domain-name "models.edn"))]
                                 (merge acc (read-models-edn-or-empty p))))
                             {}
                             (domain-subdirs base-dir))
        domain-data (merge domain-direct-data domain-subdir-data)
        shared-data (read-models-edn-or-empty shared-path)]
    (merge template-data domain-data shared-data)))

(defn models-index
  "Load resources/db/models.edn and return a normalized schema index.

  `path` may be either:
  - a file path to a consolidated models.edn (e.g. resources/db/models.edn)
  - a directory path to a hierarchical models root (e.g. resources/db)

  Output:
  {:entities #{\"users\" ...}
   :entity->fields
   {\"users\" {:raw [\"id\" \"email\" ...]
             :canonical #{\"id\" \"email\" ...}
             :raw-by-canonical {\"id\" \"id\" ...}}}}"
  ([] (models-index "resources/db/models.edn"))
  ([path]
   (let [f (io/file path)
         data (if (and f (.exists f) (.isDirectory f))
                (read-hierarchical-models f)
                (discovery/read-edn-file path))
         entities (keys data)
         entity->fields
         (reduce
           (fn [acc entity]
             (let [raw-fields (->> (get-in data [entity :fields])
                                (map first)
                                (map name)
                                vec)
                   canonical (set (map discovery/normalize-field-id raw-fields))
                   raw-by-canonical (zipmap (map discovery/normalize-field-id raw-fields) raw-fields)]
               (assoc acc
                 (discovery/normalize-entity-id entity)
                 {:raw raw-fields
                  :canonical canonical
                  :raw-by-canonical raw-by-canonical})))
           {}
           entities)]
     {:entities (set (map discovery/normalize-entity-id entities))
      :entity->fields entity->fields})))

(def ^:private default-allowlist-path
  "config/frontend-config-allowlist.edn")

(defn load-allowlist
  "Read an allowlist EDN file.

  When `path` is nil, fall back to the repository default allowlist file if it
  exists. Returns nil when no allowlist file is available."
  ([] (load-allowlist nil))
  ([path]
   (cond
     path
     (discovery/read-edn-file path)

     :else
     (let [f (io/file default-allowlist-path)]
       (when (.exists f)
         (discovery/read-edn-file default-allowlist-path))))))

(defn- normalize-allowlist-fields
  [fields]
  (->> fields
    (map discovery/normalize-field-id)
    (remove nil?)
    set))

(defn normalize-allowlist
  "Normalize an EDN allowlist into a map of canonical entity -> set of canonical fields.

  Supported formats:
  - map of entity -> collection of fields
  - set of fields (global allowlist for all entities)

  Use :* or :all as a global allowlist key in the map format."
  [allowlist]
  (cond
    (nil? allowlist) {:* #{}}

    (set? allowlist)
    {:* (normalize-allowlist-fields allowlist)}

    (map? allowlist)
    (reduce-kv
      (fn [acc entity fields]
        (let [entity* (discovery/normalize-entity-id entity)
              key* (if (#{"*" "all"} entity*) :* entity*)
              fields-coll (cond
                            (nil? fields) []
                            (set? fields) fields
                            (sequential? fields) fields
                            :else [fields])
              fields* (normalize-allowlist-fields fields-coll)]
          (update acc key* (fnil set/union #{}) fields*)))
      {:* #{}}
      allowlist)

    :else
    (throw (ex-info "Allowlist must be a map or set" {:type (type allowlist)}))))

(defn known-entity?
  "Return true when `entity` is known either from the DB schema or the allowlist.

  Allowlist-backed entities support frontend-only synthetic views that do not
  map 1:1 to DB tables but still need schema-alignment validation."
  [schema-index allowlist entity]
  (let [entity* (discovery/normalize-entity-id entity)]
    (or (contains? (:entities schema-index) entity*)
      (contains? allowlist entity*))))

(defn allowed-fields
  [{:keys [schema-index entity computed allowlist include-computed?]}]
  (set/union
    (get-in schema-index [:entity->fields entity :canonical] #{})
    (when include-computed? (get computed entity #{}))
    (get allowlist :* #{})
    (get allowlist entity #{})))

(def ^:private internal-field-suffixes
  "Canonical (dash-separated) suffixes marking storage-internal columns:
   encrypted-email triple, privacy subject refs."
  #{"-ciphertext" "-lookup-hash" "-key-version" "-subject-ref"})

(def ^:private internal-field-names
  #{"password-hash" "subject-ref"})

(defn internal-field?
  "True when a field name (raw snake_case or canonical) denotes an internal
   column that must never be surfaced in frontend table-column configs:
   the encrypted-email storage triple, privacy subject refs, and password
   hashes. The sync uses this so newly added internal columns are not
   appended to :available-columns."
  [field]
  (let [s (discovery/normalize-field-id field)]
    (boolean
      (and s
        (or (contains? internal-field-names s)
          (some #(str/ends-with? s %) internal-field-suffixes))))))
