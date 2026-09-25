(ns automigrate.generation.extended
  "Extended migration generation for functions, triggers, policies, views"
  (:require
    [automigrate.db.introspection :as db-intro]
    [automigrate.files.management :as files]
    [automigrate.generation.components :as components]
    [automigrate.util.file :as file-util]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str]))

(def ^:private FORWARD-MIGRATION-DELIMITER "-- FORWARD")
(def ^:private BACKWARD-MIGRATION-DELIMITER "-- BACKWARD")

(defn- migration-type->label
  [migration-type]
  (case migration-type
    :fn "function"
    :trg "trigger"
    :pol "policy"
    :view "view"
    (name migration-type)))

(defn- migration-type->extension
  [migration-type]
  (case migration-type
    :fn "fn"
    :trg "trg"
    :pol "pol"
    :view "view"
    (name migration-type)))

(defn- parse-migration-content
  [migration-file-path]
  (let [content (slurp migration-file-path)
        [forward backward] (str/split content #"-- BACKWARD" 2)
        forward-sql (some-> forward
                      (str/replace #"-- FORWARD" "")
                      str/trim
                      not-empty)
        backward-sql (some-> backward str/trim not-empty)]
    {:forward-sql forward-sql
     :backward-sql backward-sql}))

;; Re-export component generation functions
(def generate-function-migrations components/generate-function-migrations)
(def generate-trigger-migrations components/generate-trigger-migrations)
(def generate-policy-migrations components/generate-policy-migrations)
(def generate-view-migrations components/generate-view-migrations)

(defn generate-extended-migrations-from-edn!
  "Generate migration files from hierarchical EDN files in db folder"
  [{:keys [resources-dir migrations-dir]}]
  (let [db-dir (file-util/join-path resources-dir "db")
        migrations-full-dir (file-util/join-path resources-dir migrations-dir)
        existing-migrations (files/migrations-list migrations-dir)

        ;; Check if hstore extension migration already exists
        hstore-migration-exists? (some #(str/includes? % "enable_hstore_extension") existing-migrations)

        ;; Find the next available migration number based on all existing files
        existing-numbers (->> existing-migrations
                           (map files/get-migration-number)
                           (into #{}))

        start-number (if (empty? existing-numbers)
                       2
                       (inc (apply max existing-numbers)))

        ;; Create hstore migration if it doesn't exist, using the next available number
        hstore-migration-number (when-not hstore-migration-exists?
                                  (let [migration-number start-number
                                        file-name (format "%04d_enable_hstore_extension.sql" migration-number)
                                        file-path (file-util/join-path migrations-full-dir file-name)
                                        content "-- FORWARD\nCREATE EXTENSION IF NOT EXISTS hstore;\n\n-- BACKWARD\nDROP EXTENSION IF EXISTS hstore;"]
                                    (spit file-path content)
                                    (println (str "Created hstore extension migration: " file-name))
                                    migration-number))

        ;; Read hierarchical EDN files
        functions-all (files/read-hierarchical-edn db-dir "functions.edn")
        triggers-all (files/read-hierarchical-edn db-dir "triggers.edn")
        policies-all (files/read-hierarchical-edn db-dir "policies.edn")
        views-all (files/read-hierarchical-edn db-dir "views.edn")

        ;; Get existing migrations again to include the new hstore one
        all-migrations (files/migrations-list migrations-dir)

          ;; Find orphaned migrations
        orphaned-functions (files/find-orphaned-migrations all-migrations functions-all :fn)
        orphaned-triggers (files/find-orphaned-migrations all-migrations triggers-all :trg)
        orphaned-policies (files/find-orphaned-migrations all-migrations policies-all :pol)
        orphaned-views (files/find-orphaned-migrations all-migrations views-all :view)

          ;; Filter new EDN items
        functions (files/filter-new-edn-items functions-all :fn all-migrations)
        triggers (files/filter-new-edn-items triggers-all :trg all-migrations)
        policies (files/filter-new-edn-items policies-all :pol all-migrations)
        views (files/filter-new-edn-items views-all :view all-migrations)

        ;; After possibly creating hstore, continue from the highest current number
        creation-start-number (if hstore-migration-number
                                (inc hstore-migration-number)
                                start-number)]

      ;; Log what we're skipping and what we're dropping
    (let [skipped-functions (- (count functions-all) (count functions))
          skipped-triggers (- (count triggers-all) (count triggers))
          skipped-policies (- (count policies-all) (count policies))
          skipped-views (- (count views-all) (count views))]
      (when (> skipped-functions 0)
        (println (format "Skipping %d function(s) - migrations already exist" skipped-functions)))
      (when (> skipped-triggers 0)
        (println (format "Skipping %d trigger(s) - migrations already exist" skipped-triggers)))
      (when (> skipped-policies 0)
        (println (format "Skipping %d policy(s) - migrations already exist" skipped-policies)))
      (when (> skipped-views 0)
        (println (format "Skipping %d view(s) - migrations already exist" skipped-views))))

      ;; Log orphaned migrations that will get DROP migrations
    (let [total-orphans (+ (count orphaned-functions) (count orphaned-triggers)
                          (count orphaned-policies) (count orphaned-views))]
      (when (> total-orphans 0)
        (println (format "Found %d orphaned migration(s) - will create DROP migrations" total-orphans))
        (when (seq orphaned-functions)
          (println (format "  %d orphaned function(s): %s" (count orphaned-functions)
                     (str/join ", " orphaned-functions))))
        (when (seq orphaned-triggers)
          (println (format "  %d orphaned trigger(s): %s" (count orphaned-triggers)
                     (str/join ", " orphaned-triggers))))
        (when (seq orphaned-policies)
          (println (format "  %d orphaned policy(s): %s" (count orphaned-policies)
                     (str/join ", " orphaned-policies))))
        (when (seq orphaned-views)
          (println (format "  %d orphaned view(s): %s" (count orphaned-views)
                     (str/join ", " orphaned-views))))))

      ;; Create DROP migrations for orphaned migrations first
    (let [drop-migrations-created (atom [])
          create-drop-migration (fn [migration-file migration-number]
                                  (let [migration-type (files/get-migration-type migration-file)
                                        type-label (migration-type->label migration-type)
                                        type-ext (migration-type->extension migration-type)
                                        name (files/get-migration-name migration-file)
                                        source-file-path (file-util/join-path migrations-full-dir migration-file)
                                        {:keys [forward-sql backward-sql]} (parse-migration-content source-file-path)
                                        file-name (format "%04d_drop_%s_%s.%s"
                                                    migration-number type-label name type-ext)
                                        file-path (file-util/join-path migrations-full-dir file-name)
                                        drop-sql (or backward-sql
                                                   (format "-- Cannot drop %s %s automatically; source migration missing backward SQL"
                                                     type-label name))
                                        recreate-sql (or forward-sql
                                                       (format "-- Cannot recreate %s %s automatically; source migration missing forward SQL"
                                                         type-label name))
                                        content (format "%s\n%s\n\n%s\n%s"
                                                  FORWARD-MIGRATION-DELIMITER
                                                  drop-sql
                                                  BACKWARD-MIGRATION-DELIMITER
                                                  recreate-sql)]
                                    (spit file-path content)
                                    (println (str "Created DROP migration: " file-name))
                                    file-name))]
      (doseq [[idx migration-file] (map-indexed vector (concat orphaned-functions orphaned-triggers orphaned-policies orphaned-views))]
        (let [migration-number (+ creation-start-number idx)
              drop-file (create-drop-migration migration-file migration-number)]
          (swap! drop-migrations-created conj drop-file)))

        ;; Now create regular migrations for new EDN items
      (let [create-start (+ creation-start-number (count @drop-migrations-created))]

          ;; Generate function migrations from EDN (only new ones)
        (doseq [[idx [func-name func-def]] (map-indexed vector functions)]
          (let [number (+ create-start idx)
                sanitized-name (when func-name (name func-name))
                file-name (format "%04d_function_%s.fn" number sanitized-name)
                file-path (file-util/join-path migrations-full-dir file-name)
                content (format "%s\n%s\n\n%s\n%s"
                          FORWARD-MIGRATION-DELIMITER
                          (:up func-def)
                          BACKWARD-MIGRATION-DELIMITER
                          (:down func-def))]
            (spit file-path content)
            (println (str "Created function migration: " file-name))))

          ;; Generate trigger migrations from EDN (only new ones)
        (let [trigger-start (+ create-start (count functions))]
          (doseq [[idx [trig-name trig-def]] (map-indexed vector triggers)]
            (let [number (+ trigger-start idx)
                  sanitized-name (when trig-name (name trig-name))
                  file-name (format "%04d_trigger_%s.trg" number sanitized-name)
                  file-path (file-util/join-path migrations-full-dir file-name)
                  content (format "%s\n%s\n\n%s\n%s"
                            FORWARD-MIGRATION-DELIMITER
                            (:up trig-def)
                            BACKWARD-MIGRATION-DELIMITER
                            (:down trig-def))]
              (spit file-path content)
              (println (str "Created trigger migration: " file-name)))))

          ;; Generate policy migrations from EDN (only new ones)
        (let [policy-start (+ create-start (count functions) (count triggers))]
          (doseq [[idx [pol-name pol-def]] (map-indexed vector policies)]
            (let [number (+ policy-start idx)
                  sanitized-name (when pol-name (name pol-name))
                  file-name (format "%04d_policy_%s.pol" number sanitized-name)
                  file-path (file-util/join-path migrations-full-dir file-name)
                  content (format "%s\n%s\n\n%s\n%s"
                            FORWARD-MIGRATION-DELIMITER
                            (:up pol-def)
                            BACKWARD-MIGRATION-DELIMITER
                            (:down pol-def))]
              (spit file-path content)
              (println (str "Created policy migration: " file-name)))))

          ;; Generate view migrations from EDN (only new ones)
        (let [view-start (+ create-start (count functions) (count triggers) (count policies))]
          (doseq [[idx [view-name view-def]] (map-indexed vector views)]
            (let [number (+ view-start idx)
                  sanitized-name (when view-name (name view-name))
                  file-name (format "%04d_view_%s.view" number sanitized-name)
                  file-path (file-util/join-path migrations-full-dir file-name)
                  content (format "%s\n%s\n\n%s\n%s"
                            FORWARD-MIGRATION-DELIMITER
                            (:up view-def)
                            BACKWARD-MIGRATION-DELIMITER
                            (:down view-def))]
              (spit file-path content)
              (println (str "Created view migration: " file-name))))))

        ;; Return enhanced summary
      (let [total-new (+ (count functions) (count triggers) (count policies) (count views))
            total-all (+ (count functions-all) (count triggers-all) (count policies-all) (count views-all))
            total-drops (count @drop-migrations-created)]
        (when (> total-all total-new)
          (println (format "\nSkipped %d existing migration(s), created %d new migration(s)"
                     (- total-all total-new) total-new)))
        (when (> total-drops 0)
          (println (format "Created %d DROP migration(s) for deleted EDN files" total-drops)))
        {:functions (count functions)
         :triggers (count triggers)
         :policies (count policies)
         :views (count views)
         :total total-new
         :skipped (- total-all total-new)
         :drop-migrations-created total-drops
         :orphaned-functions (count orphaned-functions)
         :orphaned-triggers (count orphaned-triggers)
         :orphaned-policies (count orphaned-policies)
         :orphaned-views (count orphaned-views)}))))

(defn generate-extended-migrations-from-db!
  "Generate migration files for functions, triggers, policies, and views from database"
  [{:keys [db migrations-dir resources-dir]}]
  (let [functions (db-intro/get-db-functions db)
        triggers (db-intro/get-db-triggers db)
        policies (db-intro/get-db-policies db)
        views (db-intro/get-db-views db)

        existing-numbers (->> (files/migrations-list migrations-dir)
                           (map files/get-migration-number)
                           (into #{}))

        ;; Find the highest number to start from
        start-number (if (empty? existing-numbers)
                       1
                       (inc (apply max existing-numbers)))]

    ;; Generate function migrations
    (doseq [[idx {:keys [function_name definition]}] (map-indexed vector functions)]
      (let [number (+ start-number idx)
            sanitized-name (-> function_name
                             (str/lower-case)
                             (str/replace #"[^a-z0-9]+" "_"))
            file-name (format "%04d_function_%s.fn" number sanitized-name)
            file-path (file-util/join-path resources-dir migrations-dir file-name)
            drop-sql (db-intro/format-function-drop function_name definition)
            content (format "%s\n%s\n\n%s\n%s"
                      FORWARD-MIGRATION-DELIMITER
                      definition
                      BACKWARD-MIGRATION-DELIMITER
                      drop-sql)]
        (spit file-path content)
        (println (str "Created function migration: " file-name))))

    ;; Generate trigger migrations
    (let [trigger-start (+ start-number (count functions))]
      (doseq [[idx {:keys [trigger_name table_name definition]}] (map-indexed vector triggers)]
        (let [number (+ trigger-start idx)
              sanitized-name (-> trigger_name
                               (str/lower-case)
                               (str/replace #"[^a-z0-9]+" "_"))
              file-name (format "%04d_trigger_%s.trg" number sanitized-name)
              file-path (file-util/join-path resources-dir migrations-dir file-name)
              drop-sql (format "DROP TRIGGER IF EXISTS %s ON %s;" trigger_name table_name)
              content (format "%s\n%s\n\n%s\n%s"
                        FORWARD-MIGRATION-DELIMITER
                        definition
                        BACKWARD-MIGRATION-DELIMITER
                        drop-sql)]
          (spit file-path content)
          (println (str "Created trigger migration: " file-name)))))

    ;; Generate policy migrations
    (let [policy-start (+ start-number (count functions) (count triggers))]
      (doseq [[idx {:keys [policy_name table_name command permissive
                           using_expression with_check_expression roles]}] (map-indexed vector policies)]
        (let [number (+ policy-start idx)
              sanitized-name (-> policy_name
                               (str/lower-case)
                               (str/replace #"[^a-z0-9]+" "_"))
              file-name (format "%04d_policy_%s.pol" number sanitized-name)
              file-path (file-util/join-path resources-dir migrations-dir file-name)
              cmd-sql (db-intro/policy-command->sql command)
              permissive-sql (if permissive "PERMISSIVE" "RESTRICTIVE")
              roles-sql (if (str/blank? roles) "PUBLIC" roles)
              using-sql (when using_expression (format "\n  USING (%s)" using_expression))
              check-sql (when with_check_expression (format "\n  WITH CHECK (%s)" with_check_expression))
              create-sql (format "CREATE POLICY %s ON %s\n  AS %s\n  FOR %s\n  TO %s%s%s;"
                           policy_name table_name permissive-sql cmd-sql roles-sql
                           (or using-sql "") (or check-sql ""))
              drop-sql (format "DROP POLICY IF EXISTS %s ON %s;" policy_name table_name)
              content (format "%s\n%s\n\n%s\n%s"
                        FORWARD-MIGRATION-DELIMITER
                        create-sql
                        BACKWARD-MIGRATION-DELIMITER
                        drop-sql)]
          (spit file-path content)
          (println (str "Created policy migration: " file-name)))))

    ;; Generate view migrations
    (let [view-start (+ start-number (count functions) (count triggers) (count policies))]
      (doseq [[idx {:keys [view_name definition]}] (map-indexed vector views)]
        (let [number (+ view-start idx)
              sanitized-name (-> view_name
                               (str/lower-case)
                               (str/replace #"[^a-z0-9]+" "_"))
              file-name (format "%04d_view_%s.view" number sanitized-name)
              file-path (file-util/join-path resources-dir migrations-dir file-name)
              create-sql (format "CREATE OR REPLACE VIEW %s AS\n%s" view_name definition)
              drop-sql (format "DROP VIEW IF EXISTS %s;" view_name)
              content (format "%s\n%s\n\n%s\n%s"
                        FORWARD-MIGRATION-DELIMITER
                        create-sql
                        BACKWARD-MIGRATION-DELIMITER
                        drop-sql)]
          (spit file-path content)
          (println (str "Created view migration: " file-name)))))

    ;; Return summary
    {:functions (count functions)
     :triggers (count triggers)
     :policies (count policies)
     :views (count views)
     :total (+ (count functions) (count triggers) (count policies) (count views))}))

(defn sync-db-to-edn-files!
  "One-time sync from database to EDN files. This extracts functions, triggers,
   policies and views from the database and saves them as EDN files in the
   appropriate directories. Use this to capture current DB state as source files."
  [{:keys [db resources-dir]
    :or {resources-dir "resources"}}]
  (let [;; Ensure directories exist
        _ (doseq [dir ["db/functions" "db/triggers" "db/policies" "db/views"]]
            (let [full-path (file-util/join-path resources-dir dir)]
              (io/make-parents (str full-path "/dummy"))))

        ;; Extract and save functions
        functions (db-intro/get-db-functions db)
        _ (doseq [{:keys [function_name definition]} functions]
            (let [drop-sql (db-intro/format-function-drop function_name definition)
                  edn-content {:name function_name
                               :up definition
                               :down drop-sql}
                  file-path (file-util/join-path resources-dir "db/functions"
                              (str function_name ".edn"))]
              (spit file-path (with-out-str (pprint/pprint edn-content)))
              (println (str "Created function EDN: " file-path))))

        ;; Extract and save triggers
        triggers (db-intro/get-db-triggers db)
        _ (doseq [{:keys [trigger_name table_name definition]} triggers]
            (let [drop-sql (format "DROP TRIGGER IF EXISTS %s ON %s;" trigger_name table_name)
                  edn-content {:name trigger_name
                               :table table_name
                               :up definition
                               :down drop-sql}
                  file-path (file-util/join-path resources-dir "db/triggers"
                              (str trigger_name ".edn"))]
              (spit file-path (with-out-str (pprint/pprint edn-content)))
              (println (str "Created trigger EDN: " file-path))))

        ;; Extract and save policies
        policies (db-intro/get-db-policies db)
        _ (doseq [{:keys [policy_name table_name command permissive
                          using_expression with_check_expression roles]} policies]
            (let [cmd-sql (db-intro/policy-command->sql command)
                  permissive-sql (if permissive "PERMISSIVE" "RESTRICTIVE")
                  roles-sql (if (str/blank? roles) "PUBLIC" roles)
                  using-sql (when using_expression (format "\n  USING (%s)" using_expression))
                  check-sql (when with_check_expression (format "\n  WITH CHECK (%s)" with_check_expression))
                  create-sql (format "CREATE POLICY %s ON %s\n  AS %s\n  FOR %s\n  TO %s%s%s;"
                               policy_name table_name permissive-sql cmd-sql roles-sql
                               (or using-sql "") (or check-sql ""))
                  drop-sql (format "DROP POLICY IF EXISTS %s ON %s;" policy_name table_name)
                  edn-content {:name policy_name
                               :table table_name
                               :up create-sql
                               :down drop-sql}
                  file-path (file-util/join-path resources-dir "db/policies"
                              (str policy_name ".edn"))]
              (spit file-path (with-out-str (pprint/pprint edn-content)))
              (println (str "Created policy EDN: " file-path))))

        ;; Extract and save views
        views (db-intro/get-db-views db)
        _ (doseq [{:keys [view_name definition]} views]
            (let [create-sql (format "CREATE OR REPLACE VIEW %s AS\n%s" view_name definition)
                  drop-sql (format "DROP VIEW IF EXISTS %s;" view_name)
                  edn-content {:name view_name
                               :up create-sql
                               :down drop-sql}
                  file-path (file-util/join-path resources-dir "db/views"
                              (str view_name ".edn"))]
              (spit file-path (with-out-str (pprint/pprint edn-content)))
              (println (str "Created view EDN: " file-path))))]

    ;; Return summary
    {:functions (count functions)
     :triggers (count triggers)
     :policies (count policies)
     :views (count views)
     :total (+ (count functions) (count triggers) (count policies) (count views))}))
