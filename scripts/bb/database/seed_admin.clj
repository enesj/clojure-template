#!/usr/bin/env clj

(ns scripts.bb.database.seed-admin
  (:require
    [aero.core :as aero]
    [app.admin.backend.services.admin.auth :as admin-auth]
    [app.shared.type-conversion :as tc]
    [app.template.backend.migrations.simple-repl :as mig]
    [clojure.string :as str]
    [honey.sql :as hsql]
    [next.jdbc :as jdbc]))

(def default-admin
  {:email "admin@example.com"
   :full-name "System Administrator"
   :password "admin123"
   :role "owner"
   :status "active"})

(defn- prod-db-url
  "The explicit production database URL, or nil. Never printed."
  []
  (or (not-empty (System/getenv "DATABASE_URL"))
    (not-empty (System/getenv "DATABASE_PUBLIC_URL"))))

(defn- describe-db-url
  "Credential-free host:port/dbname description of a database URL."
  [url]
  (let [clean (str/replace url #"^[a-z]+://" "")]
    (if-let [[_ _host-info host port db] (re-find #"^([^/@]+@)?([^:/]+)(?::(\d+))?/([^?]+)" clean)]
      (str host ":" (or port "5432") "/" db)
      "<redacted>")))

(defn- db-target-description
  "Human-readable DB target for output; never includes credentials."
  [profile]
  (if (= :prod profile)
    (describe-db-url (prod-db-url))
    (let [{:keys [host port dbname]} (:database (aero/read-config "config/base.edn" {:profile profile}))]
      (str host ":" port "/" dbname))))

(defn- datasource-for-profile
  [profile]
  (jdbc/get-datasource {:jdbcUrl (mig/get-jdbc-url profile)}))

(defn- confirm-prod!
  [target]
  (println "⚠️  This will create or reset the owner admin on the PRODUCTION database:")
  (println (str "   " target))
  (println)
  (print "Type 'SEED ADMIN PROD' to confirm: ")
  (flush)
  (= "SEED ADMIN PROD" (some-> (read-line) str/trim)))

(defn seed-admin!
  "Ensure the default admin exists with the documented dev password.

   Writes through the application's own admin-auth path so the row carries
   the encrypted-at-rest email storage fields (email_lookup_hash,
   email_ciphertext, email_key_version) that login lookups require.
   Legacy rows created by older versions of this script keep working: the
   upsert matches by blind lookup hash, not the plaintext column."
  [db {:keys [email full-name password role status]}]
  (let [password-hash (admin-auth/hash-password password)
        existing (admin-auth/find-admin-by-email db email)]
    (if existing
      (do (jdbc/execute-one! db
            (hsql/format {:update :admins
                          :set {:password_hash password-hash
                                :full_name full-name
                                :role (tc/cast-for-database :admin-role role)
                                :status (tc/cast-for-database :admin-status status)
                                :updated_at [:now]}
                          :where [:= :id (:id existing)]}))
          {:action :updated :id (:id existing)})
      (do (admin-auth/create-admin! db {:email email
                                        :password password
                                        :full_name full-name
                                        :role role})
          {:action :created :id (:id (admin-auth/find-admin-by-email db email))}))))

(defn -main [& args]
  (let [[env & flags] args
        profile (keyword (or env "dev"))
        yes? (some #{"--yes"} flags)
        admin (cond-> default-admin
                (not-empty (System/getenv "ADMIN_EMAIL"))
                (assoc :email (str/trim (System/getenv "ADMIN_EMAIL")))
                (not-empty (System/getenv "ADMIN_PASSWORD"))
                (assoc :password (System/getenv "ADMIN_PASSWORD")))]
    (when-not (#{:dev :test :prod} profile)
      (println "❌ Invalid environment. Use: dev, test, or prod")
      (System/exit 1))
    (when (and (= :prod profile) (not (prod-db-url)))
      (println "❌ prod requires DATABASE_URL (or DATABASE_PUBLIC_URL) to be set — refusing to guess the target database")
      (System/exit 1))
    (when (and (= :prod profile) (= (:password default-admin) (:password admin)))
      (println "❌ prod requires ADMIN_PASSWORD to be set (refusing to seed the default dev password into production)")
      (System/exit 1))
    (try
      (let [target (db-target-description profile)]
        (when (and (= :prod profile) (not yes?))
          (when-not (confirm-prod! target)
            (println "❌ Cancelled.")
            (System/exit 1)))
        (let [db (datasource-for-profile profile)
              {:keys [action id]} (seed-admin! db admin)]
          (println (str "✅ Admin user " (name action) ":"))
          (println "   Email:    " (:email admin))
          (println "   Password:" (if (= (:password default-admin) (:password admin))
                                    (:password admin)
                                    "(provided via ADMIN_PASSWORD)"))
          (println "   Role:     " (:role admin))
          (println "   Status:   " (:status admin))
          (println "   ID:       " id)
          (println "   DB:       " target)))
      (catch Exception e
        (println "❌ Failed to seed admin:" (.getMessage e))
        (System/exit 1)))))

(apply -main *command-line-args*)
