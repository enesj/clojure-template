#!/usr/bin/env bb

;; Enhanced Clojure project creation script with proper EDN parsing
;; This replaces the bash script with a more robust Babashka implementation

(ns create-new-app
  (:require
    [babashka.fs :as fs]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.string :as str]))

;; Configuration manifest for file updates
(def config-manifest
  {:replacements
   [{:file "config/base.edn"
     :type :text
     ;; Order matters: the test name must be replaced first because it
     ;; contains the dev name as a prefix. DB names are hardcoded in
     ;; :dbname and the :jdbc-url strings, so a plain text rewrite is
     ;; used instead of an EDN path update.
     :patterns [{:pattern #"multi_tenant_pos_test"
                 :replacement "{{test-db-name}}"}
                {:pattern #"multi_tenant_pos"
                 :replacement "{{db-name}}"}]}
    {:file "docker-compose.yml"
     :type :text
     ;; Test name first: it contains the dev name as a prefix.
     :patterns [{:pattern #"multi_tenant_pos_test"
                 :replacement "{{test-db-name}}"}
                {:pattern #"multi_tenant_pos"
                 :replacement "{{db-name}}"}]}
    {:file "package.json"
     :type :json
     :updates [{:path ["name"] :key :package-name}]}
    {:file "resources/public/index.html"
     :type :text
     :patterns [{:pattern #"<title>.*?</title>"
                 :replacement "<title>{{title}}</title>"}]}
    {:file "src/app/backend/db/init.clj"
     :type :text
     :patterns [{:pattern #"\"bookkeeping\""
                 :replacement "\"{{db-name}}\""}]}]})

(defn expand-home [path]
  (if (str/starts-with? path "~")
    (str (System/getProperty "user.home") (subs path 1))
    path))

(defn to-title-case [s]
  (str/join " " (map str/capitalize (str/split s #"[\s-_]+"))))

(defn to-snake-case [s]
  (-> s
    (str/replace #"[^\w\s-]" "")
    (str/replace #"[\s-]+" "_")
    str/lower-case))

(defn load-config-file [config-path]
  (try
    (let [expanded-path (expand-home config-path)]
      (when (fs/exists? expanded-path)
        (edn/read-string (slurp expanded-path))))
    (catch Exception e
      (println "Error reading config file:" (.getMessage e))
      nil)))

(defn apply-edn-update [content path value]
  (let [data (edn/read-string content)]
    (pr-str (assoc-in data path value))))

(defn apply-json-update [content path value]
  (let [json-data (json/parse-string content true)]
    (json/generate-string (assoc-in json-data path value) {:pretty true})))

(defn apply-text-patterns [content patterns config]
  (reduce (fn [text {:keys [pattern replacement]}]
            (let [expanded-replacement (reduce (fn [s [k v]]
                                                 (str/replace s (str "{{" (name k) "}}") v))
                                         replacement
                                         config)]
              (str/replace text pattern expanded-replacement)))
    content
    patterns))

(defn update-file [{:keys [file type updates patterns]} target-dir config]
  (let [file-path (str target-dir "/" file)]
    (when (fs/exists? file-path)
      (try
        (let [content (slurp file-path)
              updated-content
              (case type
                :edn (reduce (fn [c {:keys [path key transform]}]
                               (if transform
                                 (let [data (edn/read-string c)
                                       current-val (get-in data path)
                                       new-val (transform current-val (get config key))]
                                   (pr-str (assoc-in data path new-val)))
                                 (apply-edn-update c path (get config key))))
                       content
                       updates)
                :json (reduce (fn [c {:keys [path key]}]
                                (apply-json-update c path (get config key)))
                        content
                        updates)
                :text (apply-text-patterns content patterns config)
                content)]
          (spit file-path updated-content)
          (println "✓ Updated" file))
        (catch Exception e
          (println "✗ Error updating" file ":" (.getMessage e)))))))

(defn copy-template-files [source target exclude-patterns]
  (doseq [file (file-seq (io/file source))
          :when (and (.isFile file)
                  (not (some #(re-find % (.getPath file)) exclude-patterns)))]
    (let [relative-path (str/replace (.getPath file) (str source "/") "")
          target-file (io/file target relative-path)]
      (io/make-parents target-file)
      (io/copy file target-file))))

(defn generate-readme [config target-dir]
  (let [readme-content (str "# " (:title config) "\n\n"
                         "A Clojure/ClojureScript application based on the hosting template.\n\n"
                         "## Getting Started\n\n"
                         "### Prerequisites\n"
                         "- Java 11+\n"
                         "- Node.js 20+\n"
                         "- PostgreSQL 12+\n"
                         "- Clojure CLI tools\n\n"
                         "### Database Setup\n"
                         "Databases, migrations, and seed data are set up automatically during\n"
                         "project creation (skip with `--skip-db-setup`). To run the same steps\n"
                         "manually:\n"
                         "```bash\n"
                         "bb clean-db --dev --ensure      # create dev DB if missing (safe)\n"
                         "bb clean-db --test --ensure     # create test DB if missing (safe)\n"
                         "bb check-migrations dev         # applies pending migrations\n"
                         "bb check-migrations test\n"
                         "bb seed-admin test              # dev admin is auto-seeded on app start\n"
                         "bb seed-categories dev && bb seed-categories test\n"
                         "bb seed-geo-reference dev && bb seed-geo-reference test\n"
                         "```\n"
                         "Update `.secrets.edn` if your local PostgreSQL credentials differ.\n\n"
                         "### Development\n"
                         "```bash\n"
                         "# Install dependencies\n"
                         "npm install\n\n"
                         "# Run database migrations via REPL\n"
                         "# (require '[app.template.backend.migrations.simple-repl :as mig])\n"
                         "# (mig/migrate!)\n\n"
                         "# Start the application\n"
                         "./scripts/run-app.sh\n\n"
                         "# In another terminal, start CSS compilation\n"
                         "npm run develop\n"
                         "```\n\n"
                         "### Testing\n"
                         "```bash\n"
                         "# Run backend tests\n"
                         "clj -M:test\n\n"
                         "# Run frontend tests\n"
                         "./cli-tools/test_scripts/full_workflow.sh\n"
                         "```\n")]
    (spit (str target-dir "/README.md") readme-content)
    (println "✓ Generated README.md")))

;; Validation functions (Improvement #2)
(defn validate-edn-file [file-path]
  (try
    (edn/read-string (slurp file-path))
    {:valid true :file file-path}
    (catch Exception e
      {:valid false :file file-path :error (.getMessage e)})))

(defn validate-project [target-dir]
  (println "\n=== Validating generated project ===")
  (let [required-files ["deps.edn" "package.json" "shadow-cljs.edn"
                        "resources/db/models.edn" "config/base.edn"]
        edn-files (filter #(str/ends-with? % ".edn") required-files)
        validation-errors (atom [])]

    ;; Check required files exist
    (doseq [file required-files]
      (let [file-path (str target-dir "/" file)]
        (if (fs/exists? file-path)
          (println "✓" file "exists")
          (do
            (println "✗" file "missing")
            (swap! validation-errors conj {:type :missing-file :file file})))))

    ;; Validate EDN files
    (doseq [file edn-files]
      (let [file-path (str target-dir "/" file)
            result (validate-edn-file file-path)]
        (if (:valid result)
          (println "✓" file "is valid EDN")
          (do
            (println "✗" file "has invalid EDN:" (:error result))
            (swap! validation-errors conj result)))))

    ;; Check directory structure
    (let [required-dirs ["src/app/admin" "src/app/template" "src/app/domain" "src/app/shared"
                         "src/app/frontend" "resources/public" "scripts" "test"]]
      (doseq [dir required-dirs]
        (let [dir-path (str target-dir "/" dir)]
          (if (fs/directory? dir-path)
            (println "✓ Directory" dir "exists")
            (do
              (println "✗ Directory" dir "missing")
              (swap! validation-errors conj {:type :missing-dir :dir dir}))))))

    ;; Summary
    (let [errors @validation-errors]
      (if (empty? errors)
        (do
          (println "\n✅ Project validation passed!")
          true)
        (do
          (println (str "\n❌ Project validation failed with " (count errors) " errors"))
          false)))))

;; ============================================================================
;; Database setup (runbook automation)
;; ============================================================================

(defn- run-in-project
  "Run a command inside the generated project directory; print output live.
   Returns true on exit code 0."
  [target-dir & cmd]
  (println "\n---" (str/join " " cmd) "---")
  (let [proc (-> (ProcessBuilder. ^java.util.List (vec cmd))
               (.directory (io/file target-dir))
               (.redirectOutput java.lang.ProcessBuilder$Redirect/INHERIT)
               (.redirectError java.lang.ProcessBuilder$Redirect/INHERIT)
               .start)]
    (= 0 (.waitFor proc))))

(defn- secrets-present?
  "The generated project's config/secrets.edn is copied from the template's
   config/.secrets.edn. Without it, DB connections will fail with a missing
   #ref resolution."
  [target-dir]
  (fs/exists? (str target-dir "/config/.secrets.edn")))

(defn setup-databases
  "Run the fresh-DB runbook inside the generated project:
   ensure databases exist -> migrate dev+test -> seed admin/categories/geo.
   Skipped with --skip-db-setup; also skipped (with instructions) when secrets
   are missing."
  [target-dir config]
  (if (some #{"--skip-db-setup"} *command-line-args*)
    (println "\n⏭️  Skipping database setup (--skip-db-setup)")
    (if-not (secrets-present? target-dir)
      (println "\n⏭️  Skipping database setup: config/.secrets.edn not found in the new project."
               "\n   Fill it in, then run:"
               "\n     bb clean-db --dev --ensure && bb clean-db --test --ensure"
               "\n     bb migrate-and-sync-frontend-config   (or mig/migrate! via REPL for dev+test)"
               "\n     bb seed-admin test && bb seed-categories dev && bb seed-categories test"
               "\n     bb seed-geo-reference dev && bb seed-geo-reference test")
      (let [steps [;; Ensure both databases exist (create-if-missing, never drops)
                   ["bb" "clean-db" "--dev" "--ensure"]
                   ["bb" "clean-db" "--test" "--ensure"]
                   ;; Apply all migrations + bootstrap frontend configs (both envs)
                   ["bb" "migrate-and-sync-frontend-config"]
                   ["bb" "migrate-and-sync-frontend-config" "--profile" "test"]
                   ;; Verify alignment
                   ["bb" "check-migrations" "dev"]
                   ["bb" "check-migrations" "test"]]
            ;; Seed data: dev admin is auto-created by app bootstrap, so seed it
            ;; explicitly only for test; categories and geo for both.
            seed-steps [["bb" "seed-admin" "test"]
                        ["bb" "seed-categories" "dev"]
                        ["bb" "seed-categories" "test"]
                        ["bb" "seed-geo-reference" "dev"]
                        ["bb" "seed-geo-reference" "test"]]
            all-steps (concat steps seed-steps)
            failed (atom nil)]
        (println "\n=== Setting up databases (dev + test) ===")
        (doseq [cmd all-steps
                :while (nil? @failed)]
          (when-not (apply run-in-project target-dir cmd)
            (reset! failed cmd)))
        (if @failed
          (do
            (println "\n❌ Database setup failed at:" (str/join " " @failed))
            (println "   Fix the issue inside" target-dir "and re-run the remaining runbook steps")
            false)
          (do
            (println "\n✅ Databases ready: migrations applied and seed data loaded (dev + test)")
            (println "   Dev admin (auto-seeded on first app start): admin@example.com / admin123")
            true))))))

(defn create-project [project-name config]
  (let [target-dir (or (:target-dir config) project-name)
        source-dir (System/getProperty "user.dir")
        exclude-patterns [#"\.git" #"node_modules" #"target" #"tmp" #"\.cpcache" #"out" #"resources/public/assets/js" #"create[-_]new[-_]app" #"new-app-config\.edn" #"backups" #"^\.env$" #"\.env$"]]

    (println "\n=== Creating new project:" project-name "===")
    (println "Configuration:")
    (doseq [[k v] config]
      (println (str "  " (name k) ": " v)))

    ;; Create target directory
    (fs/create-dirs target-dir)

    ;; Copy template files
    (println "\nCopying template files...")
    (copy-template-files source-dir target-dir exclude-patterns)

    ;; Update files based on manifest
    (println "\nApplying customizations...")
    (doseq [replacement (:replacements config-manifest)]
      (update-file replacement target-dir config))

    ;; Generate README
    (generate-readme config target-dir)

    ;; Create .gitignore if missing
    (let [gitignore-path (str target-dir "/.gitignore")]
      (when-not (fs/exists? gitignore-path)
        (spit gitignore-path (str/join "\n" ["node_modules/" "target/" ".cpcache/"
                                             "out/" ".nrepl-port" ".lsp/" ".clj-kondo/"
                                             "resources/public/assets/js/"]))
        (println "✓ Created .gitignore")))

    ;; Initialize git repository
    (when-not (fs/exists? (str target-dir "/.git"))
      (shell/sh "git" "init" :dir target-dir)
      (println "✓ Initialized git repository"))

    ;; Validate the generated project
    (validate-project target-dir)

    ;; Ensure databases, apply migrations, and load seed data (dev + test)
    (setup-databases target-dir config)

    (println "\n✨ Project created successfully at:" target-dir)
    (println "\nNext steps:")
    (println "  cd" target-dir)
    (println "  npm install")
    (println "  ./scripts/run-app.sh")))

(defn prompt-for-value [prompt current-value]
  (print (str prompt " [" current-value "]: "))
  (flush)
  (let [input (read-line)]
    (if (str/blank? input)
      current-value
      input)))


(defn interactive-config [initial-config]
  (println "\n=== Interactive Configuration ===")
  (let [title (prompt-for-value "Project title" (:title initial-config))
        db-name (prompt-for-value "Database name" (:db-name initial-config))
        package-name (prompt-for-value "Package name" (:package-name initial-config))
        target-dir (prompt-for-value "Target directory" (:target-dir initial-config))]
    {:title title
     :db-name db-name
     :test-db-name (str db-name "_test")
     :package-name package-name
     :target-dir target-dir}))

(defn -main [& args]
  (if (empty? args)
    (do
      (println "Usage: bb create_new_app.clj <project-name> [options]")
      (println "\nOptions:")
      (println "  --config <file>     Load configuration from EDN file")
      (println "  --title <title>     Project title for HTML")
      (println "  --db-name <name>    Database name")
      (println "  --package-name <name> NPM package name")
      (println "  --target-dir <dir>  Target directory (default: project-name)")
      (println "  --skip-db-setup     Skip database creation, migrations, and seeding")
      (System/exit 1))

    (let [project-name (first args)
          ;; Parse command line arguments: "--flag value" pairs and
          ;; standalone boolean flags like --skip-db-setup.
          parsed-args (loop [as (rest args)
                             m {}]
                        (if (empty? as)
                          m
                          (let [a (first as)
                                v (second as)]
                            (if (and v (not (str/starts-with? v "--")))
                              (recur (nnext as) (assoc m a v))
                              (recur (rest as) (assoc m a true))))))
          config-file (get parsed-args "--config")

          ;; Build initial config
          base-config {:title (to-title-case project-name)
                       :db-name (to-snake-case project-name)
                       :test-db-name (str (to-snake-case project-name) "_test")
                       :package-name (str/lower-case project-name)
                       :target-dir project-name}

          ;; Load from config file if specified
          file-config (when config-file
                        (load-config-file config-file))

          ;; Merge configurations (CLI args override file config)
          initial-config (merge base-config
                           file-config
                           (when (get parsed-args "--title")
                             {:title (get parsed-args "--title")})
                           (when (get parsed-args "--db-name")
                             {:db-name (get parsed-args "--db-name")
                              :test-db-name (str (get parsed-args "--db-name") "_test")})
                           (when (get parsed-args "--package-name")
                             {:package-name (get parsed-args "--package-name")})
                           (when (get parsed-args "--target-dir")
                             {:target-dir (get parsed-args "--target-dir")}))

          ;; Check for auto-config file
          auto-config-exists? (and (not config-file)
                                (fs/exists? "new-app-config.edn"))

          final-config (if auto-config-exists?
                         (do
                           (println "\nFound new-app-config.edn")
                           (println "Current configuration:")
                           (doseq [[k v] initial-config]
                             (println (str "  " (name k) ": " v)))
                           (print "\nUse this configuration? (y/n/m for modify): ")
                           (flush)
                           (let [choice (read-line)]
                             (case (str/lower-case (or choice "y"))
                               "y" initial-config
                               "m" (interactive-config initial-config)
                               "n" (interactive-config base-config)
                               initial-config)))
                         initial-config)]

      (create-project project-name final-config))))

;; For JSON parsing support
 ;; Execute main when run as script
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
