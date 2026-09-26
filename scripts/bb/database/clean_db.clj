#!/usr/bin/env clj

(ns clean-db
  (:require
    [aero.core :as aero]
    [clojure.java.shell :as shell]
    [clojure.string :as str]))

(defn- psql-superuser
  "Choose the PostgreSQL role to use for DB-level operations (drop/create).

  Defaults to the DB user from config (works with the official Postgres Docker
  image where POSTGRES_USER is the superuser). Can be overridden via env var
  DB_SUPERUSER for local installs." 
  [db-config]
  (or (not-empty (System/getenv "DB_SUPERUSER"))
      (:user db-config)
      "postgres"))

(defn- sh-psql
  "Run psql with optional PGPASSWORD injected from db-config." 
  [db-config & args]
  (let [pwd (:password db-config)
        cmd (cond-> (vec args)
              (not (str/blank? pwd)) (into [:env {"PGPASSWORD" pwd}]))]
    (apply shell/sh cmd)))

(defn get-db-config [profile]
  (let [config (aero/read-config "config/base.edn" {:profile profile})
        db-config (:database config)]
    {:host (:host db-config)
     :port (:port db-config)
     :dbname (:dbname db-config)
     :user (:user db-config)
     :password (:password db-config)}))

(defn file-exists? [filepath]
  (.exists (java.io.File. filepath)))

(defn confirm-clean-database [env]
  (println (str "⚠️  DANGER: This will COMPLETELY DROP the " (str/upper-case env) " database!"))
  (println (str "🗑️  ALL DATA in " (str/upper-case env) " database will be PERMANENTLY LOST!"))
  (println)
  (print "Type 'DROP DATABASE' to confirm this dangerous operation: ")
  (flush)
  (let [response (read-line)]
    (= "DROP DATABASE" (str/trim response))))

(defn drop-database [env db-config]
  (println (str "🗑️  Dropping " (str/upper-case env) " database: " (:dbname db-config)))

  (let [superuser (psql-superuser db-config)]

    ;; First terminate all connections to the target database (using superuser)
    (let [terminate-cmd ["/opt/homebrew/bin/psql"
                         "-h" (:host db-config)
                         "-p" (str (:port db-config))
                         "-U" superuser
                         "-d" "postgres"
                         "-c" (str "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" (:dbname db-config) "' AND pid <> pg_backend_pid();")]]

      (println "🔌 Terminating existing connections...")
      (apply sh-psql db-config terminate-cmd))

    ;; Drop the database (using superuser)
    (let [drop-cmd ["/opt/homebrew/bin/psql"
                    "-h" (:host db-config)
                    "-p" (str (:port db-config))
                    "-U" superuser
                    "-d" "postgres"
                    "-c" (str "DROP DATABASE IF EXISTS \"" (:dbname db-config) "\";")]]

      (println (str "🗑️  Executing: DROP DATABASE " (:dbname db-config)))
      (let [result (apply sh-psql db-config drop-cmd)]
        (if (= 0 (:exit result))
          (println "✅ Database dropped successfully")
          (do
            (println "❌ Failed to drop database!")
            (when-not (str/blank? (:err result))
              (println "Error:" (:err result)))
            (throw (Exception. "Database drop failed"))))))))

(defn create-database [env db-config]
  (println (str "🏗️  Creating new " (str/upper-case env) " database: " (:dbname db-config)))

  (let [superuser (psql-superuser db-config)
        create-cmd ["/opt/homebrew/bin/psql"
                    "-h" (:host db-config)
                    "-p" (str (:port db-config))
                    "-U" superuser
                    "-d" "postgres"
                    "-c" (str "CREATE DATABASE \"" (:dbname db-config) "\" OWNER \"" (:user db-config) "\";")]]

    (println (str "🏗️  Executing: CREATE DATABASE " (:dbname db-config)))
    (let [result (apply sh-psql db-config create-cmd)]
      (if (= 0 (:exit result))
        (println "✅ Database created successfully")
        (do
          (println "❌ Failed to create database!")
          (when-not (str/blank? (:err result))
            (println "Error:" (:err result)))
          (throw (Exception. "Database creation failed")))))))

(defn database-exists? [db-config]
  (let [superuser (psql-superuser db-config)
        check-cmd ["/opt/homebrew/bin/psql"
                   "-h" (:host db-config)
                   "-p" (str (:port db-config))
                   "-U" superuser
                   "-d" "postgres"
                   "-tA"
                   "-c" (str "SELECT 1 FROM pg_database WHERE datname = '" (:dbname db-config) "';")]]
    (= "1\n"
      (let [result (apply sh-psql db-config check-cmd)]
        (when (= 0 (:exit result))
          (:out result))))))

(defn ensure-database
  "Non-destructive: create the database only when it does not exist yet.
   Used by create-new-app so a freshly scaffolded project gets empty databases
   without ever dropping anything."
  [env]
  (let [db-config (get-db-config (keyword env))]
    (println (str "🔎 Checking " (str/upper-case env) " database: " (:dbname db-config)
                  " on " (:host db-config) ":" (:port db-config)))
    (if (database-exists? db-config)
      (println (str "✅ Database " (:dbname db-config) " already exists — leaving it untouched"))
      (create-database env db-config))))

(defn clean-database [env]
  (let [db-config (get-db-config (keyword env))]

    (println (str "🔄 Starting CLEAN of " (str/upper-case env) " database..."))
    (println (str "🎯 Target: " (:dbname db-config) " on " (:host db-config) ":" (:port db-config)))
    (println)

    (try
      ;; Step 1: Drop existing database
      (drop-database env db-config)

      ;; Step 2: Create new empty database
      (create-database env db-config)

      (println)
      (println "🎉 DATABASE CLEAN COMPLETED SUCCESSFULLY!")
      (println (str "✅ " (str/upper-case env) " database has been dropped and recreated as empty"))

      (catch Exception e
        (println (str "❌ Database clean failed: " (.getMessage e)))
        (System/exit 1)))))

(defn -main [& args]
  (let [args (vec args)]
    (cond
      ;; Non-destructive ensure mode: create the DB only when missing.
      (= args ["dev" "--ensure"]) (ensure-database "dev")
      (= args ["test" "--ensure"]) (ensure-database "test")

    (= 1 (count args))
    (let [[env] args]
      (if (#{"dev" "test"} env)
        (if (confirm-clean-database env)
          (try
            (clean-database env)
            (catch Exception e
              (println (str "❌ Error during database clean: " (.getMessage e)))
              (when-let [cause (.getCause e)]
                (println (str "   Caused by: " (.getMessage cause))))
              (System/exit 1)))
          (println "❌ Database clean cancelled by user"))
        (do
          (println (str "❌ Invalid environment: " env))
          (println "Usage: clj -M scripts/bb/database/clean_db.clj [dev|test] [--ensure]")
          (System/exit 1))))

    :else
    (do
      (println "Usage: clj -M scripts/bb/database/clean_db.clj [dev|test] [--ensure]")
      (println "⚠️  WARNING: Without --ensure this COMPLETELY DROPS and recreates the target database!")
      (println)
      (println "  dev         Clean development database (destructive)")
      (println "  test        Clean test database (destructive)")
      (println "  dev --ensure   Create development database only if missing (safe)")
      (println "  test --ensure  Create test database only if missing (safe)")
      (println)
      (println "Examples:")
      (println "  clj -M scripts/bb/database/clean_db.clj dev")
      (println "  clj -M scripts/bb/database/clean_db.clj test")
      (println "  clj -M scripts/bb/database/clean_db.clj dev --ensure")
      (System/exit 1)))))

(apply -main *command-line-args*)
