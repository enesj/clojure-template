#!/usr/bin/env clj

(ns scripts.bb.database.seed-admin
  (:require
    [aero.core :as aero]
    [app.admin.backend.services.admin.auth :as admin-auth]
    [app.shared.type-conversion :as tc]
    [app.template.backend.migrations.simple-repl :as mig]
    [honey.sql :as hsql]
    [next.jdbc :as jdbc]))

(def default-admin
  {:email "admin@example.com"
   :full-name "System Administrator"
   :password "admin123"
   :role "owner"
   :status "active"})

(defn- db-target-description
  "Human-readable DB target for output; never includes credentials."
  [profile]
  (let [{:keys [host port dbname]} (:database (aero/read-config "config/base.edn" {:profile profile}))]
    (str host ":" port "/" dbname)))

(defn- datasource-for-profile
  [profile]
  (jdbc/get-datasource {:jdbcUrl (mig/get-jdbc-url profile)}))

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
  (let [env (or (first args) "dev")
        profile (keyword env)]
    (when-not (#{:dev :test} profile)
      (println "❌ Invalid environment. Use: dev or test")
      (System/exit 1))
    (try
      (let [db (datasource-for-profile profile)
            {:keys [action id]} (seed-admin! db default-admin)]
        (println (str "✅ Admin user " (name action) ":"))
        (println "   Email:    " (:email default-admin))
        (println "   Password:" (:password default-admin))
        (println "   Role:     " (:role default-admin))
        (println "   Status:   " (:status default-admin))
        (println "   ID:       " id)
        (println "   DB:       " (db-target-description profile)))
      (catch Exception e
        (println "❌ Failed to seed admin:" (.getMessage e))
        (System/exit 1)))))

(apply -main *command-line-args*)
