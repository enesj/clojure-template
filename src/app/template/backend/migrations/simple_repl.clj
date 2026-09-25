(ns app.template.backend.migrations.simple-repl
  "REPL-friendly utilities for database migrations (schema + extended).

   What this namespace provides:
   - Config-aware JDBC URL helpers
   - Schema migration from hierarchical models (template/shared/domain)
   - Extended migrations from EDN (functions, triggers, policies, views)
   - Status/list/explain commands
   - Duplicate-number checks and migration summaries
   - One-time DB→EDN sync helpers for existing DBs
   - Safe regeneration helpers for resolving duplicate numbers

   Usage (quick):
   (require '[app.migrations.simple-repl :as mig])
   (mig/make-all-migrations!)
   (mig/migrate!)
   (mig/status)

   See the comment block at the bottom for scenarios."
  (:require
    [aero.core :as aero]
    [app.shared.frontend-config.discovery :as fc-discovery]
    [app.shared.frontend-config.schema :as fc-schema]
    [app.shared.frontend-config.sync :as fc-sync]
    [app.shared.frontend-config.validation :as fc-validation]
    [app.template.backend.migrations.alignment.report :as alignment]
    [app.template.backend.migrations.hierarchical-models :as hierarchical-models]
    [app.template.backend.utils.model-customizations :as model-cust]
    [automigrate.core :as am]
    [automigrate.generation.extended :as gen-ext]
    [automigrate.util.db :as db-util]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.pprint :as pprint]
    [clojure.string :as str]))

(defn get-jdbc-url
  "Return a JDBC URL suitable for migration tooling.

  Precedence:
  1) `DATABASE_URL` env var (verbatim)
  2) `:database :jdbc-url` from Aero config
  3) Construct from `:database :host/:port/:dbname`

  Notes:
  - Migration tooling expects credentials to be present in the JDBC URL.
    If the configured URL does not contain credentials, we will append
    `?user=...&password=...` using `:database :user` / `:database :password`.
  - Errors must never include plaintext secrets; ex-data is redacted."
  ([] (get-jdbc-url :dev))
  ([profile]
   (let [file (io/file "config/base.edn")
         cfg  (cond
                (.exists file) (aero/read-config file {:profile profile})
                (io/resource "config/base.edn") (aero/read-config (io/resource "config/base.edn") {:profile profile})
                (io/resource "base.edn") (aero/read-config (io/resource "base.edn") {:profile profile})
                :else nil)
         env-url (some-> (System/getenv "DATABASE_URL") str/trim not-empty
                   ;; PaaS providers use postgres:// or postgresql://; JDBC needs jdbc:postgresql://
                   (str/replace #"^postgresql://" "jdbc:postgresql://")
                   (str/replace #"^postgres://" "jdbc:postgresql://"))
         db-cfg  (:database cfg)
         redact-db-cfg (fn [m]
                         (when (map? m)
                           (-> m
                             (dissoc :password)
                             (update :jdbc-url (fn [s]
                                                 (when (string? s)
                                                   (-> s
                                                     (str/replace #"(?i)(password=)[^&]*" "$1REDACTED")
                                                     (str/replace #"(?i)(user=)[^&]*" "$1REDACTED")
                                                     (str/replace #"(?i)://([^:/?#]+):([^@/?#]+)@" "://REDACTED:REDACTED@"))))))))
         base-url (or env-url
                    (:jdbc-url db-cfg)
                    (when (and db-cfg (every? db-cfg [:host :port :dbname]))
                      (format "jdbc:postgresql://%s:%s/%s"
                        (:host db-cfg) (:port db-cfg) (:dbname db-cfg)))
                    (throw (ex-info "DATABASE_URL or database config (jdbc-url/host/port/dbname) is required"
                             {:database (redact-db-cfg db-cfg)})))
         has-credentials? (fn [^String s]
                            (boolean (re-find #"(?i)(password=|user=|://[^/]+:[^@]+@)" s)))]
     (cond
       ;; PaaS-style URLs usually already include credentials.
       (has-credentials? base-url)
       base-url

       :else
       (let [user (:user db-cfg)
             pwd (:password db-cfg)
             _ (when-not (some-> user str/trim not-empty)
                 (throw (ex-info "Database user missing in config; required to construct migration JDBC URL"
                          {:database (redact-db-cfg db-cfg)})))
             ;; password may be nil for local peer/trust setups, but in most cases it should be set.
             qs (str "?user=" user (when (some-> pwd str/trim not-empty)
                                     (str "&password=" pwd)))]
         (str base-url qs))))))

(def ^:private resources-dir "resources")
(def ^:private migrations-dir "db/migrations")
(def ^:private project-dir (System/getProperty "user.dir"))
(def ^:private db-dir (str project-dir "/resources/db"))
(def ^:private models-file (str db-dir "/models.edn"))

(defn make-extended-migrations!
  "Generate extended migration files (functions, triggers, policies, views) from EDN files."
  ([]
   (gen-ext/generate-extended-migrations-from-edn!
     {:resources-dir resources-dir
      :migrations-dir migrations-dir})
   (println "Extended migrations generated in resources/db/migrations")))

(defn sync-db-to-edn!
  "One-time DB→EDN sync. Extracts functions, triggers, policies, views from DB
   and writes EDN files under `resources/db/{functions,triggers,policies,views}`.
   Use to capture the current DB state into source-controlled EDN."
  ([] (sync-db-to-edn! :dev))
  ([profile]
   (let [jdbc-url (get-jdbc-url profile)
         db (db-util/db-conn jdbc-url)]
     (println "Syncing database objects to EDN files...")
     (let [summary (gen-ext/sync-db-to-edn-files! {:db db :resources-dir resources-dir})]
       (println (format (str "\nSynced to EDN files:\n"
                          "  Functions: %d\n  Triggers: %d\n  Policies: %d\n  Views: %d\n  Total: %d")
                  (:functions summary)
                  (:triggers summary)
                  (:policies summary)
                  (:views summary)
                  (:total summary)))))))

(defn merge-hierarchical-models!
  "Merge template/domain/shared models into consolidated `resources/db/models.edn`."
  []
  (let [_ (io/make-parents (str db-dir "/.keep"))
        merged-raw (hierarchical-models/read-hierarchical-edn db-dir "models.edn")
        ;; Strip admin configuration for clean separation
        merged (model-cust/strip-all-admin-config merged-raw)]
    (when (.exists (io/file models-file))
      (.delete (io/file models-file)))
    (with-open [w (io/writer models-file)]
      (binding [*print-length* nil *print-level* nil *out* w]
        (pprint/pprint merged)))
    (println (format "Consolidated models written to %s (%d models)"
               models-file (count merged)))
    merged))

(def ^:private sql-only-schema-actions
  "Schema actions already represented by explicit SQL migrations or redundant with
   column drops in the same generated migration.

   Automigrate reconstructs model state from EDN schema migrations only, so SQL-only
  migrations like 0048_audit_system_actor_type.sql and
  0071_expenses_is_posted_drop can be rediscovered forever.
   PostgreSQL also auto-drops indexes that depend on a dropped column; suppressing
   these exact redundant index-drop actions avoids a non-idempotent drop after the
   column action has already removed the index. Filtering only these exact actions
   prevents duplicate non-idempotent generated drops while preserving the canonical
   source model as the alignment target."
  #{{:action :drop-column
     :field-name :is-posted
     :model-name :expenses}
   {:action :alter-type
    :type-name :audit-actor-type
    :model-name :users
    :options {:choices ["user" "admin" "system"] :type :enum}
    :changes {:choices {:from ["user" "admin"]
                 :to ["user" "admin" "system"]}}}
   {:action :alter-column
    :field-name :actor-id
    :model-name :audit_logs
    :options {:type :uuid}
    :changes {:null {:from false :to :EMPTY}}}
    {:action :drop-index
     :index-name :idx-expenses-is-posted
     :model-name :expenses}
    {:action :drop-index
     :index-name :idx-expenses-user
     :model-name :expenses}
    {:action :drop-index
     :index-name :idx-receipts-user
     :model-name :receipts}
    {:action :drop-index
     :index-name :uniq-user-expense-settings-tenant-user
     :model-name :user_expense_settings}})

(def ^:private deferred-email-cutover-actions
  "Generated cutover actions from 0062 that must stay deferred until an
   application-level backfill exists for legacy plaintext email rows. Keeping
   these scoped to the historical file avoids suppressing a future intentional
   cutover migration after the backfill is implemented."
  #{{:action :alter-column
     :field-name :email-ciphertext
     :model-name :admins
     :options {:null false :type :text}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :alter-column
     :field-name :email-key-version
     :model-name :users
     :options {:null false :type [:varchar 32]}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :alter-column
     :field-name :email-lookup-hash
     :model-name :tenant_invitations
     :options {:null false :type [:varchar 64]}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :alter-column
     :field-name :email-ciphertext
     :model-name :tenant_invitations
     :options {:null false :type :text}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :alter-column
     :field-name :email-lookup-hash
     :model-name :admin_invitations
     :options {:null false :type [:varchar 64]}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :alter-column
     :field-name :email-lookup-hash
     :model-name :admins
     :options {:null false :type [:varchar 64]}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :alter-column
     :field-name :email-key-version
     :model-name :admin_invitations
     :options {:null false :type [:varchar 32]}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :alter-column
     :field-name :email-ciphertext
     :model-name :admin_invitations
     :options {:null false :type :text}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :drop-column
     :field-name :email
     :model-name :tenant_invitations}
    {:action :alter-column
     :field-name :email-key-version
     :model-name :tenant_invitations
     :options {:null false :type [:varchar 32]}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :drop-column
     :field-name :email
     :model-name :users}
    {:action :alter-column
     :field-name :email-key-version
     :model-name :admins
     :options {:null false :type [:varchar 32]}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :alter-column
     :field-name :email-lookup-hash
     :model-name :users
     :options {:null false :type [:varchar 64]}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :drop-column
     :field-name :email
     :model-name :admins}
    {:action :alter-column
     :field-name :email-ciphertext
     :model-name :users
     :options {:null false :type :text}
     :changes {:null {:from :EMPTY :to false}}}
    {:action :drop-column
     :field-name :email
     :model-name :admin_invitations}})

(def ^:private file-scoped-suppressed-schema-actions
  {"0062_schema.edn" deferred-email-cutover-actions})

(defn- schema-migration-files
  "Return the current generated schema migration files."
  []
  (->> (.listFiles (io/file resources-dir migrations-dir))
    (filter #(re-matches #"\d+_.*\.edn" (.getName %)))
    set))

(defn- suppressed-schema-actions-for-file
  [file]
  (into sql-only-schema-actions
    (get file-scoped-suppressed-schema-actions (.getName ^java.io.File file))))

(defn- sanitize-schema-migration-file!
  [file {:keys [preserve-empty?]
         :or {preserve-empty? false}}]
  (let [actions (edn/read-string (slurp file))
        suppressed-actions (suppressed-schema-actions-for-file file)
        sanitized-actions (remove suppressed-actions actions)
        removed-count (- (count actions) (count sanitized-actions))]
    (when (pos? removed-count)
      (cond
        (seq sanitized-actions)
        (spit file (with-out-str (pprint/pprint sanitized-actions)))

        preserve-empty?
        (spit file (with-out-str (pprint/pprint '())))

        :else
        (.delete file))
      (println (format "Filtered %d suppressed schema action(s) from %s"
                 removed-count
                 (.getPath file))))))

(defn- sanitize-schema-migration-files!
  [files opts]
  (doseq [file (sort-by #(.getName ^java.io.File %) files)]
    (sanitize-schema-migration-file! file opts)))

(defn- sanitize-generated-schema-migrations!
  "Remove exact SQL-only schema actions from newly generated EDN migrations."
  [before-files]
  (sanitize-schema-migration-files! (remove before-files (schema-migration-files))
    {:preserve-empty? false}))

(defn- sanitize-existing-schema-migrations!
  "Rewrite known-bad generated schema migrations through the same suppression
   rules used for new generations. Existing historical files are preserved as
   explicit no-op migrations when all actions are filtered so numbering remains
   stable across environments."
  []
  (sanitize-schema-migration-files! (schema-migration-files)
    {:preserve-empty? true}))

(defn make-schema-migration!
  "Generate schema migration from consolidated models with function-default preprocessing."
  [& {:keys [name]}]
  (let [models-file-path (str resources-dir "/db/models.edn")
        temp-models-file (str resources-dir "/db/models_processed.edn")
        preprocessor (requiring-resolve 'app.template.backend.migrations.function-defaults/load-and-preprocess-models)
        before-files (schema-migration-files)]
    (try
      (sanitize-existing-schema-migrations!)
      (let [processed (preprocessor models-file-path)]
        (spit temp-models-file (with-out-str (pprint/pprint processed))))
      (am/make {:models-file "db/models_processed.edn"
                :resources-dir resources-dir
                :name (or name "schema")})
      (sanitize-generated-schema-migrations! before-files)
      (finally
        (when (.exists (io/file temp-models-file))
          (.delete (io/file temp-models-file)))))))

(defn make-all-migrations!
  "End-to-end generation: merge models → schema migration → extended migrations."
  []
  (merge-hierarchical-models!)
  (make-schema-migration!)
  (Thread/sleep 300)
  (make-extended-migrations!))

(defn prune-migrations-to-base!
  "Dangerous: remove all migration files after base (0001_schema.edn and 0002_enable_hstore_extension.sql) to avoid
   duplicate numbers before regenerating extended migrations. Use only when you intend to fully regenerate.
   Returns list of removed files."
  []
  (let [dir (io/file (str resources-dir "/" migrations-dir))
        files (->> (.listFiles dir)
                (filter #(.isFile %))
                (map #(.getName %)))
        keep? (fn [n]
                (or (str/starts-with? n "0001_schema.edn")
                  (str/ends-with? n "_enable_hstore_extension.sql")))
        to-del (remove keep? files)]
    (doseq [f to-del]
      (io/delete-file (str resources-dir "/" migrations-dir "/" f) true))
    to-del))

(defn regenerate-extended-migrations-clean!
  "Prune extended migrations to base, then regenerate extended migrations from EDN."
  []
  (let [removed (prune-migrations-to-base!)]
    (when (seq removed)
      (println (format "Removed %d migration(s) beyond base." (count removed))))
    (make-extended-migrations!)))

(defn check-duplicate-migrations
  "Check for duplicate migration numbers and print them."
  []
  (let [migrations-dir "resources/db/migrations"
        files (.listFiles (io/file migrations-dir))
        file-names (map #(.getName %) files)
        numbers (keep #(when-let [match (re-find #"^(\d+)_" %)]
                         [(Integer/parseInt (second match)) %])
                  file-names)
        grouped (group-by first numbers)
        duplicates (filter #(> (count (val %)) 1) grouped)]
    (if (empty? duplicates)
      (println "No duplicate migration numbers found.")
      (do
        (println "Found duplicate migration numbers:")
        (doseq [[num files] duplicates]
          (println (format "Number %04d is used by:" num))
          (doseq [[_ filename] files]
            (println (str "  - " filename))))))))

(defn next-migration-number
  "Return the next migration number based on files in resources/db/migrations."
  []
  (let [files (map #(.getName %) (.listFiles (io/file (str resources-dir "/" migrations-dir))))
        numbers (keep #(when-let [m (re-find #"^(\d+)_" %)]
                         (Integer/parseInt (second m))) files)]
    (inc (or (apply max numbers) 0))))

(defn migration-summary
  "Print a summary of migrations by type."
  []
  (let [files (map #(.getName %) (.listFiles (io/file (str resources-dir "/" migrations-dir))))
        by-type (group-by #(cond
                             (str/ends-with? % ".edn") :schema
                             (str/ends-with? % ".sql") :sql
                             (str/ends-with? % ".fn") :function
                             (str/ends-with? % ".trg") :trigger
                             (str/ends-with? % ".pol") :policy
                             (str/ends-with? % ".view") :view
                             :else :other)
                  files)]
    (println "Migration Summary:")
    (doseq [[type files] (sort by-type)]
      (println (format "  %-10s %3d" (name type) (count files))))
    (println (format "  %-10s %3d" "TOTAL" (count files)))))

(defn alignment-report
  "Return an alignment report for the given profile (default :dev).

  This checks:
  - migration files vs automigrate_migrations
  - basic schema diff (hierarchical models.edn vs DB)
  - extended objects existence (functions/triggers/views/policies)

  Returns the report map (see `app.template.backend.migrations.alignment.report/report`)."
  ([] (alignment-report :dev))
  ([profile]
   (alignment/report {:jdbc-url (get-jdbc-url profile)})))

(defn check-migrations-alignment!
  "Print an alignment report and return it.

  Does not exit the process; callers can decide whether to throw/exit based on
  (alignment/diff? report)."
  ([] (check-migrations-alignment! :dev))
  ([profile]
   (let [r (alignment-report profile)]
     (alignment/print-report! r)
     r)))

(defn assert-migrations-aligned!
  "Throw if alignment checks find differences.

  Returns the report when aligned."
  ([] (assert-migrations-aligned! :dev))
  ([profile]
   (let [r (alignment-report profile)]
     (when (alignment/diff? r)
       (throw (ex-info "DB is not aligned with migrations/models" {:profile profile
                                                                   :report r})))
     r)))

(defn- frontend-config-label
  [{:keys [scope domain kind]}]
  (str (name scope)
    (when domain (str "/" domain))
    " "
    (name kind)
    ".edn"))

(defn- print-frontend-config-validation!
  ([results] (print-frontend-config-validation! results {:verbose? false}))
  ([results {:keys [verbose?]
             :or {verbose? false}}]
   (println "=== Frontend config validation (dry-run) ===")
   (let [invalid (filter (comp not :valid?) results)
         total (count results)
         invalid-count (count invalid)]
     (println (format "Checked %d config file(s). %s" total
                (if (zero? invalid-count)
                  "All valid ✅"
                  (format "%d invalid ❌" invalid-count))))
     (when (or verbose? (pos? invalid-count))
       (doseq [res (if verbose? results invalid)]
         (let [label (frontend-config-label res)
               path (:path res)]
           (if (:valid? res)
             (println "OK" label "valid" (str "(" path ")"))
             (do
               (println "ERROR" label "INVALID" (str "(" path ")"))
               (let [print-opts (if verbose?
                                  {:print-length nil :print-level nil}
                                  {:print-length 10 :print-level 6})]
                 (binding [*print-length* (:print-length print-opts)
                           *print-level* (:print-level print-opts)]
                   (when (seq (:errors res))
                     (println "  errors:" (pr-str (:errors res))))
                   (when (seq (:warnings res))
                     (println "  warnings:" (pr-str (:warnings res))))))))
           (let [semantic (:semantic res)
                 print-opts (if verbose?
                              {:print-length nil :print-level nil}
                              {:print-length 10 :print-level 6})]
             (binding [*print-length* (:print-length print-opts)
                       *print-level* (:print-level print-opts)]
               (when (seq (:unknown-entities semantic))
                 (println "  unknown entities:" (pr-str (:unknown-entities semantic))))
               (when (seq (:unknown-fields semantic))
                 (println "  unknown fields:" (pr-str (:unknown-fields semantic))))
               (when (seq (:missing-fields semantic))
                 (if verbose?
                   (println "  missing DB fields (info):" (pr-str (:missing-fields semantic)))
                   (println "  missing DB fields (info):" (format "%d entity(s)" (count (:missing-fields semantic))))))))))))))

(defn- print-frontend-config-sync!
  ([patches] (print-frontend-config-sync! patches {:verbose? false}))
  ([patches {:keys [verbose?]
             :or {verbose? false}}]
   (println "=== Frontend config sync plan (dry-run) ===")
   (let [changed (filter :has-changes? patches)
         changed-count (count changed)
         total (count patches)]
     (println (format "Checked %d config file(s). %s" total
                (if (zero? changed-count)
                  "No changes needed ✅"
                  (format "%d file(s) need updates ❌" changed-count))))
     (when (or verbose? (pos? changed-count))
       (doseq [patch (if verbose? patches changed)]
         (let [label (frontend-config-label patch)
               path (:path patch)]
           (if (:has-changes? patch)
             (println "ERROR" label (str "(" path ")"))
             (println "OK" label "no changes" (str "(" path ")")))
           (when (seq (:unknown-entities patch))
             (println "  unknown entities:" (pr-str (:unknown-entities patch))))
           (if-not verbose?
             (when (:has-changes? patch)
               (println "  changes in" (count (:summary patch)) "entity(s)"))
             (doseq [[entity info] (sort-by (comp str key) (:summary patch))]
               (println "  entity" (pr-str entity))
               (when (seq (:add-available info))
                 (println "    add to :available-columns:" (pr-str (:add-available info))))
               (when (seq (:removed info))
                 (println "    quarantine fields:" (pr-str (:removed info))))))))))))

(defn- run-frontend-config-dry-run!
  "Validate + plan-sync frontend config against the consolidated schema.

  Returns a summary map and prints a human-readable report.

  Options:
  - :verbose? (default false) Print every OK line; otherwise print only invalid/changed files."
  ([] (run-frontend-config-dry-run! {:verbose? false}))
  ([{:keys [verbose?]
     :or {verbose? false}}]
   (let [bundles (fc-discovery/config-bundles {})]
     (if (empty? bundles)
       (do
         (println "=== Frontend config validation (dry-run) ===")
         (println "WARNING: No frontend config bundles found. Skipping.")
         {:status :skipped})
       (let [schema-index (fc-schema/models-index "resources/db/models.edn")
             bundles* (fc-discovery/load-bundles bundles)
             allowlist (fc-schema/load-allowlist)
             results (fc-validation/validate-bundles bundles* schema-index allowlist)
             patches (fc-sync/plan-sync bundles* schema-index allowlist)
             invalid (filter (comp not :valid?) results)
             changes? (some :has-changes? patches)
             unknown-entities (->> patches (mapcat :unknown-entities) vec)]
         (print-frontend-config-validation! results {:verbose? verbose?})
         (print-frontend-config-sync! patches {:verbose? verbose?})
         {:status :ok
          :validation {:valid? (empty? invalid)
                       :invalid-count (count invalid)
                       :results results}
          :sync {:has-changes? (boolean changes?)
                 :unknown-entities unknown-entities
                 :patches patches}})))))

(defn- frontend-config-dry-run-summary
  "Convert the (potentially huge) frontend-config dry-run result into a small summary map.

  This is used by `migrate!` so `clojure -e '(mig/migrate!)'` doesn't dump megabytes of data."
  [frontend-dry]
  (let [{:keys [status]} frontend-dry]
    (if (not= :ok status)
      {:status status}
      (let [results (get-in frontend-dry [:validation :results])
            patches (get-in frontend-dry [:sync :patches])
            invalid (filter (comp not :valid?) results)
            changed (filter :has-changes? patches)]
        {:status :ok
         :validation {:valid? (true? (get-in frontend-dry [:validation :valid?]))
                      :invalid-count (int (or (get-in frontend-dry [:validation :invalid-count]) 0))
                      :invalid (mapv (fn [res]
                                       (let [semantic (:semantic res)
                                             unknown-fields (:unknown-fields semantic)
                                             missing-fields (:missing-fields semantic)
                                             warnings (:warnings res)
                                             errors (:errors res)]
                                         {:label (frontend-config-label res)
                                          :path (:path res)
                                          ;; Keep the summary small: counts + a small sample.
                                          :errors-count (count errors)
                                          :errors-sample (vec (take 3 errors))
                                          :warnings-count (count warnings)
                                          :warnings-sample (vec (take 1 warnings))
                                          :unknown-entities-count (count (:unknown-entities semantic))
                                          :unknown-entities-sample (vec (take 5 (:unknown-entities semantic)))
                                          :unknown-fields-entities (vec (take 25 (keys unknown-fields)))
                                          :missing-fields-entities (vec (take 25 (keys missing-fields)))}))
                                 invalid)}
         :sync {:has-changes? (boolean (get-in frontend-dry [:sync :has-changes?]))
                :unknown-entities (vec (or (get-in frontend-dry [:sync :unknown-entities]) []))
                :changes (mapv (fn [patch]
                                 {:label (frontend-config-label patch)
                                  :path (:path patch)
                                  :unknown-entities (:unknown-entities patch)
                                  :entities (vec (keys (:summary patch)))
                                  :entity-count (count (:summary patch))})
                           changed)}}))))

(defn- run-bb!
  [& args]
  (let [result (apply shell/sh "bb" args)
        command (str/join " " (cons "bb" args))]
    (when (seq (:out result))
      (print (:out result)))
    (when (seq (:err result))
      (binding [*out* *err*]
        (print (:err result))))
    (when-not (zero? (:exit result))
      (throw (ex-info "Command failed" {:command command
                                        :result result})))
    result))

(defn- run-frontend-config-apply!
  "Apply sync changes and validate frontend config using bb scripts.

  Returns a summary map and prints script output to the REPL."
  [frontend-config-args]
  (let [args (vec (or frontend-config-args []))]
    (println "=== Frontend config sync (apply) ===")
    (let [sync-res (apply run-bb! "sync-frontend-config" "--apply" args)]
      (println "=== Frontend config validation (post-apply) ===")
      (let [validate-res (apply run-bb! "validate-frontend-config" args)]
        {:status :ok
         :sync sync-res
         :validate validate-res}))))

(defn migrate!
  "Run all pending migrations for the given profile (default :dev).

  After a successful migration, this runs the alignment check as a double-check
  (migration files vs DB tracking, models vs DB schema, extended objects).

  It also runs frontend-config validation + sync planning (dry-run only) to
  surface UI/schema alignment issues alongside migration output.

  Options:
  - :verify? (default true)  Run alignment verification after migrating.
  - :print-report? (default false) Print the alignment report even when aligned.
  - :sync-frontend-config? (default false) Apply frontend-config sync and
    re-validate using bb tasks.
  - :frontend-config-args (default []) Extra args forwarded to the bb tasks
    (e.g., [--only expenses]).
  - :frontend-config-verbose? (default false) Print all frontend-config OK lines.
  - :return (default :summary) One of:
      - :summary (small map; avoids huge output in `clojure -e`)
      - :full (includes full alignment + frontend-config structures)

  Notes:
  - If verification finds differences, it prints the report and throws.
  - Use `check-migrations-alignment!` / `assert-migrations-aligned!` directly if
    you want to run verification without migrating.
  - Frontend-config checks do not modify the database."
  ([] (migrate! :dev))
  ([profile] (migrate! profile {:verify? true}))
  ([profile {:keys [verify? print-report? sync-frontend-config? frontend-config-args]
             :or {verify? true
                  print-report? false
                  sync-frontend-config? false
                  frontend-config-args []}
             :as opts}]
   ;; Refresh custom HoneySQL clauses in long-lived REPLs before enum/type DDL.
   (require 'automigrate.util.db :reload)
     (sanitize-existing-schema-migrations!)
     (let [migration-res (am/migrate {:jdbc-url (get-jdbc-url profile)})
         report (when verify?
                  (alignment-report profile))
         diff? (when verify?
                 (alignment/diff? report))]
     (when verify?
       (when diff?
         (alignment/print-report! report))
       (when (and (not diff?) print-report?)
         (alignment/print-report! report)))
     (let [frontend-config-verbose? (boolean (:frontend-config-verbose? opts))
           return-mode (or (:return opts) :summary)
           frontend-dry (run-frontend-config-dry-run! {:verbose? frontend-config-verbose?})
           frontend-base {:dry-run frontend-dry}]
       (when (and verify? diff?)
         (throw (ex-info "DB is not aligned after migrate!" {:profile profile
                                                             :report report
                                                             :frontend-config frontend-base})))
       (let [frontend-apply (when sync-frontend-config?
                              (run-frontend-config-apply! frontend-config-args))
             frontend-result (cond-> frontend-base
                               frontend-apply (assoc :apply frontend-apply))]
         (case return-mode
           :full {:migration migration-res
                  :alignment report
                  :frontend-config frontend-result}
           ;; default: return a small, readable summary (prevents gigantic `clojure -e` output)
           {:migration migration-res
            :alignment {:diff? (boolean diff?)}
            :frontend-config (frontend-config-dry-run-summary frontend-dry)}))))))

(defn migrate-full!
  "Run migrations and return full (potentially large) result map.

  Prefer `migrate!` for CLI/terminal use; use this when debugging interactively."
  ([] (migrate-full! :dev))
  ([profile] (migrate! profile {:return :full}))
  ([profile opts] (migrate! profile (assoc opts :return :full))))

(defn status
  "Show migration status for the given profile (default :dev)."
  ([] (status :dev))
  ([profile]
   (am/list {:jdbc-url (get-jdbc-url profile)})))

(defn migrate-to!
  "Migrate to specific version (use 0 to rollback all)."
  ([version] (migrate-to! :dev version))
  ([profile version]
   (am/migrate {:jdbc-url (get-jdbc-url profile)
                :number version})))

(defn explain
  "Show SQL for a specific migration number. direction: :forward or :backward"
  [number & {:keys [direction]
             :or {direction :forward}}]
  (am/explain {:number number :direction direction :format :sql}))

;; Convenience aliases
(def m! migrate!)
(def s status)
(def make! make-all-migrations!)

(comment
  ;; Optional helpers
  (require '[app.admin.backend.setup :as setup])

  ;; Scenarios
  ;; =========
  ;; 1) Fresh start with empty DB (dev)

  ;; - Clean DB externally (recommended):
  ;;     bb clean-db --dev
  ;; - Generate migrations from source:
  ;;     (make-all-migrations!)3
  ;; - Apply migrations:
  ;;     (migrate!)
  ;; - Verify:
  ;;     (status)
  (make-all-migrations!)
  (migrate!)

;; 2) Resolve duplicate migration numbers (regenerate extended only)

  ;; - Keep base schema; prune extended migrations:
  ;;     (regenerate-extended-migrations-clean!)
  ;; - Apply (if needed):
  ;;     (migrate!)
  ;; - Check:
  ;;     (check-duplicate-migrations)
  (regenerate-extended-migrations-clean!)

;; 3) Normal development loop

  ;; - Edit EDN under resources/db/{template,shared,domain}
  ;; - Generate:
  ;;     (make-all-migrations!)
  ;; - Apply:
  ;;     (migrate!)
  ;; - Inspect or explain:
  ;;     (status)
  ;;     (explain 42)
  (make-all-migrations!)
  (migrate!)

;;
  ;; 4) One-time capture of existing DB objects to EDN
  ;; - Useful when setting up an existing DB:
  ;;     (sync-db-to-edn!)
  ;; - Then commit EDN and generate extended migrations as needed.
  ;;
  ;; 5) Migrate to a specific version
  ;; - Roll back all:
  ;;     (migrate-to! 0)
  (migrate-to! 0)
  ;; - Migrate up to N (inclusive):
  ;;     (migrate-to! 10)
  (migrate-to! 10)

  ;; Quick setup - creates admin user
  (setup/setup-all!)

  ;; Or step by step:
  ;; Create admin user only
  (setup/setup-test-admin!))
  ;;
  ;; Notes
  ;; - RLS enablement: define `enable_rls_on_tenant_tables` in
  ;;   `resources/db/shared/policies.edn`. The extended generator creates a .pol
  ;;   migration to run the ALTER TABLE ... ENABLE/DISABLE statements.
  ;; - Avoid manual edits inside `resources/db/migrations/`. Use these utilities
  ;;   to regenerate when needed. Pruning is intentionally explicit.
