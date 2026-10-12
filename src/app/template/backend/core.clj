(ns app.template.backend.core
  "Backend entry point; adjust system startup wiring and lifecycle only."
  (:require
    [aero.core :as aero]
    [app.admin.backend.services.admin.auth :as admin-auth]
    [app.domain.backend.registry :as domain-registry]
    [app.template.backend.routes.admin.settings-bootstrap :as settings-bootstrap]
    [app.template.backend.security.email :as email-privacy]
    [app.template.backend.webserver :as webserver]
    [app.template.backend.utils.json-config :as json-config]
    [app.shared.model-naming :as model-naming]
    [app.template.di.config :as template-di]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :as tools-repl]
    [hikari-cp.core :as cp]
    [next.jdbc]
    [next.jdbc.date-time]
    [next.jdbc.prepare]
    [taoensso.timbre :as log])
  (:import
    [java.sql PreparedStatement Timestamp]
    [java.time Instant]))

;; Ensures that java.time.Instant objects are correctly handled by the JDBC driver
(extend-protocol next.jdbc.prepare/SettableParameter
  java.time.Instant
  (set-parameter [^Instant v ^PreparedStatement ps ^long i]
    (.setTimestamp ps i (Timestamp/from v))))

(next.jdbc.date-time/read-as-instant)

;; ============================================================================
;; Closeable Wrapper for Data Structures
;; ============================================================================

(defrecord CloseableData [data close-fn]
  java.io.Closeable
  (close [_]
    (when close-fn
      (try
        (close-fn)
        (catch Exception e
          (log/warn e "Error during CloseableData close")))))

  ;; Make it behave like the wrapped data for most operations
  clojure.lang.IDeref
  (deref [_] data))

(defn closeable-data
  "Wrap data in a closeable container"
  ([data]
   (CloseableData. data nil))
  ([data close-fn]
   (CloseableData. data close-fn)))

;; ============================================================================
;; Replacement Functions for Deleted Dependencies
;; ============================================================================

(defn- normalize-profile [p]
  (cond
    (keyword? p) p
    (string? p)
    (let [s (-> p str str/trim str/lower-case)]
      (case s
        ("dev") :dev
        ("test") :test
        ("prod" "production") :prod
        (keyword s)))
    :else nil))

(defn- resolve-profile [config-options]
  (or (normalize-profile (:profile config-options))
    (normalize-profile (System/getProperty "aero.profile"))
    (normalize-profile (System/getenv "AERO_PROFILE"))
    :dev))

(defn load-config
  "Load configuration using Aero.

  Profile resolution order:
  1) Explicit `:profile` in `config-options`
  2) JVM system property `aero.profile`
  3) Environment variable `AERO_PROFILE`
  4) Default `:dev`

  Notes:
  - Accepts `prod` and `production` as synonyms (normalized to `:prod`)."
  [config-options]
  (let [profile (resolve-profile config-options)]
    (closeable-data
      (try
        (let [config-resource (io/resource "base.edn")]
          (if config-resource
            (aero/read-config config-resource {:profile profile})
            ;; Fallback: try direct file path
            (let [config-file (io/file "config/base.edn")]
              (aero/read-config config-file {:profile profile}))))
        (catch Exception e
          (log/error e "Error loading config with Aero")
          (throw e))))))

(defn load-models-data
  "Load models data from resources and attach kebab-case metadata for runtime use."
  []
  (closeable-data
    (try
      (let [models-resource (io/resource "db/models.edn")
            raw-models (if models-resource
                         (-> models-resource slurp edn/read-string)
                         ;; Fallback: try direct file path
                         (let [models-file (io/file "resources/db/models.edn")]
                           (if (.exists models-file)
                             (-> models-file slurp edn/read-string)
                             (do
                               (log/error "Could not find models.edn file at db/template/models.edn!")
                               (throw (ex-info "Models data is required but not found" {}))))))]
        (model-naming/attach-app-models raw-models))
      (catch Exception e
        (log/error e "Error loading models data")
        (throw e)))))

(defn create-conn-pool
  "Create database connection pool.

  Security notes:
  - Credentials are embedded as JDBC URL query parameters (`?user=X&password=Y`)
    for reliable PostgreSQL JDBC driver compatibility.
  - Never include raw JDBC URLs in exception data unless sanitized.

  Supported sources:
  - `DATABASE_URL` env var (highest priority; commonly provided in PaaS)
  - `:database :jdbc-url` from app config
  - `:database :host/:port/:dbname` from app config"
  [config]
  (let [db-config        (:database config)
        hikari-defaults  (:hikari-cp config)
        db-name          (or (:dbname db-config)
                           (throw (ex-info "Database name missing in config" {:config-key [:database :dbname]})))
        system-user      (System/getProperty "user.name")
        sanitize-jdbc-url (fn [s]
                            (when (string? s)
                              (-> s
                                (str/replace #"(?i)(password=)[^&]*" "$1REDACTED")
                                (str/replace #"(?i)(user=)[^&]*" "$1REDACTED")
                                (str/replace #"(?i)://([^:/?#]+):([^@/?#]+)@" "://REDACTED:REDACTED@"))))
        env-raw          (some-> (System/getenv "DATABASE_URL") str/trim not-empty)
        ;; Normalize PaaS prefix (postgres:// or postgresql://) to jdbc:postgresql://
        env-norm         (some-> env-raw
                           (str/replace #"^postgresql://" "jdbc:postgresql://")
                           (str/replace #"^postgres://"   "jdbc:postgresql://"))
        ;; Extract credentials embedded in the URL authority (user:pass@host).
        ;; The PostgreSQL JDBC driver does not reliably parse user:pass@host in the URL
        ;; when credentials are also passed as properties — strip them from the URL
        ;; and always pass as explicit HikariCP username/password properties instead.
        env-user         (when env-norm (second (re-find #"jdbc:postgresql://([^:@]+):[^@]+@" env-norm)))
        env-pass         (when env-norm (second (re-find #"jdbc:postgresql://[^:@]+:([^@]+)@"  env-norm)))
        env-url          (some-> env-norm (str/replace #"(jdbc:postgresql://)[^@]+@" "$1"))
        base-jdbc-url    (or env-url
                           (:jdbc-url db-config)
                           (when (every? db-config [:host :port :dbname])
                             (format "jdbc:postgresql://%s:%s/%s"
                               (:host db-config) (:port db-config) db-name))
                           (throw (ex-info "Provide DATABASE_URL or :database jdbc-url/host/port/dbname"
                                    {:database (dissoc db-config :password)})))
        ;; When DATABASE_URL is the source, its embedded credentials take priority
        ;; (they are the actual credentials for the provisioned PaaS database).
        ;; For local dev, fall back to config values.
        effective-user   (if env-url
                           (or env-user (:user db-config))
                           (:user db-config))
        effective-pass   (if env-url
                           (or env-pass (:password db-config))
                           (:password db-config))
        ;; Embed credentials as JDBC URL query parameters — most reliable
        ;; method for the PostgreSQL JDBC driver in DriverManager mode
        ;; (bypasses HikariCP property-passing edge cases).
        ;; Skip if the URL already contains credentials (e.g. dev/test config jdbc-urls).
        jdbc-url-final   (if (and (or effective-user effective-pass)
                               (not (re-find #"[?&]user=" base-jdbc-url)))
                           (str base-jdbc-url "?"
                             (str/join "&"
                               (cond-> []
                                 effective-user (conj (str "user=" effective-user))
                                 effective-pass (conj (str "password=" effective-pass)))))
                           base-jdbc-url)
        hikari-config    (merge hikari-defaults
                           {:pool-name "db-pool"
                            :jdbc-url  jdbc-url-final})
        _                (log/info {:event  :db/connecting
                                    :jdbc-url (sanitize-jdbc-url jdbc-url-final)
                                    :user effective-user
                                    :source (if env-url :DATABASE_URL :config)})
        ;; Warn if the INPUT URL (before we add our params) already has embedded password
        _                (when (and (string? base-jdbc-url)
                                 (re-find #"(?i)password=" base-jdbc-url))
                           (log/warn {:event :db/jdbc-url-contains-password
                                      :hint "Prefer setting password via :database :password (or env vars) instead of embedding it in DATABASE_URL/:jdbc-url"}))
        ;; Validate that we're not using the system username as database name (common misconfig).
        _                (when (= db-name system-user)
                           (throw (ex-info "Configuration error: suspicious database name (looks like system username)"
                                    {:database-name db-name
                                     :jdbc-url      (sanitize-jdbc-url base-jdbc-url)
                                     :user          system-user})))]
    (cp/make-datasource hikari-config)))

(defn new-scheduler
  "Create a simple scheduler - replacement for helpers/new-scheduler"
  []
  (reify
    java.io.Closeable
    (close [_])
    Object
    (toString [_] "SimpleScheduler")))

(defn await-finish
  "Await scheduler finish - replacement for .await-finish"
  [_scheduler]
  (try
    ;; Block indefinitely until interrupted
    (loop []
      (Thread/sleep 1000)  ; Sleep for 1 second intervals
      (recur))
    (catch InterruptedException _
      (log/info "System shutdown requested"))))

(defn create-service-container
  "Create the complete service container with all dependencies wired.
   Now uses the DI container for proper lifecycle management."
  [database models-data config]
  (log/info "Creating service container using DI container...")
  ;; Use the new DI-based service container and wrap it for closeable compatibility.
  ;; Ensure the system actually stops services when the system shuts down.
  (let [services (template-di/create-service-container config database models-data nil)]
    (closeable-data services #(template-di/stop-services! services))))

(def ^:private default-dev-admin
  {:email "admin@example.com"
   :password "admin123"
   :full_name "System Administrator"
   :role "owner"})

(defn- ensure-default-dev-admin!
  "Best-effort dev-only admin seed.

  Ensures a default admin exists on a fresh dev database so `/admin/api/login`
  works out-of-the-box.

  Never overwrites existing admins or passwords."
  [db]
  (try
    (let [{:keys [email password full_name role]} default-dev-admin]
      (when-not (admin-auth/find-admin-by-email db email)
        (try
          (admin-auth/create-admin! db {:email email
                                        :password password
                                        :full_name full_name
                                        :role role})
          (log/info "Seeded default dev admin"
            {:email-masked (email-privacy/mask-email email)
             :role role})
          (catch Exception e
            (log/warn e "Failed to seed default dev admin with preferred role; retrying with role=admin"
              {:email-masked (email-privacy/mask-email email)})
            (try
              (admin-auth/create-admin! db {:email email
                                            :password password
                                            :full_name full_name
                                            :role "admin"})
              (log/info "Seeded default dev admin"
                {:email-masked (email-privacy/mask-email email)
                 :role "admin"})
              (catch Exception e2
                (log/warn e2 "Failed to seed default dev admin"
                  {:email-masked (email-privacy/mask-email email)})))))))
    (catch Exception e
      (log/warn e "Skipping default dev admin seed (DB not ready?)"))))

(defn- validate-config!
  "Fail fast if required secrets are absent.
  Called after load-config but before other resources are opened.
  Hard errors: database credentials + prod-like privacy keys.
  Warnings: optional integrations and local fallback privacy keys."
  [config profile]
  (let [db-url-override (some-> (System/getenv "DATABASE_URL") str/trim not-empty)
        encryption-key (some-> (System/getenv "EMAIL_PRIVACY_ENCRYPTION_KEY_B64") str/trim not-empty)
        lookup-key (some-> (System/getenv "EMAIL_PRIVACY_LOOKUP_KEY_B64") str/trim not-empty)
        subject-key (some-> (System/getenv "PRIVACY_SUBJECT_KEY_B64") str/trim not-empty)
        local-privacy-profile? (contains? #{:dev :local :test} profile)
        prod-like-profile? (not local-privacy-profile?)
        errors (cond-> []
                 (and (not db-url-override)
                   (nil? (get-in config [:database :password])))
                 (conj "[:database :password] — set DB_PASSWORD (prod) or DB_DEV_PASSWORD/DB_TEST_PASSWORD env var")

                 (and prod-like-profile? (nil? encryption-key))
                 (conj "EMAIL_PRIVACY_ENCRYPTION_KEY_B64 — required in prod-like profiles for encrypted-at-rest email storage")

                 (and prod-like-profile? (nil? lookup-key))
                 (conj "EMAIL_PRIVACY_LOOKUP_KEY_B64 — required in prod-like profiles for blind-index email lookup")

                 (and prod-like-profile? (nil? subject-key))
                 (conj "PRIVACY_SUBJECT_KEY_B64 — required in prod-like profiles for pseudonymous operational subject refs"))
        warnings (cond-> []
                   (and (= :prod profile)
                     (nil? (get-in config [:oauth :google :client-secret])))
                   (conj "[:oauth :google :client-secret] — Google OAuth disabled (set GOOGLE_OAUTH_CLIENT_SECRET to enable)")

                   (and (= :prod profile)
                     (nil? (get-in config [:email :smtp :pass]))
                     (nil? (get-in config [:email :postmark :api-key])))
                   (conj "[:email] — email sending disabled (set SMTP_PASS or POSTMARK_API_KEY to enable)")

                   (and local-privacy-profile?
                     (or (nil? encryption-key) (nil? lookup-key)))
                   (conj "[email-privacy] — using local fallback keys; set EMAIL_PRIVACY_ENCRYPTION_KEY_B64 and EMAIL_PRIVACY_LOOKUP_KEY_B64 for realistic local testing")

                   (and local-privacy-profile? (nil? subject-key))
                   (conj "[privacy-subject] — using a local fallback key; set PRIVACY_SUBJECT_KEY_B64 for realistic local testing"))]
    (when (seq warnings)
      (log/warn {:event :config/missing-optional-secrets
                 :profile profile
                 :warnings warnings}))
    (when (seq errors)
      (throw
        (ex-info
          (str "Missing required configuration secrets. "
            "See config/.secrets.edn.template or .env.example.\n"
            (str/join "\n" (map #(str "  " %) errors)))
          {:profile profile :missing errors})))))

(defn my-system [config-options]
  (let [profile (resolve-profile config-options)]
    (fn [do-with-state]
      (with-open [config (load-config config-options)]
        (validate-config! @config profile)
        (with-open [models-data       (load-models-data)
                    database          (let [db (create-conn-pool @config)]
                                        (when (= :dev profile)
                                          (ensure-default-dev-admin! db))
                                        (settings-bootstrap/bootstrap-runtime-configs! db)
                                        db)
                    service-container (try
                                        (create-service-container database @models-data @config)
                                        (catch Exception e
                                          (log/error e "Service container creation failed:" (.getMessage e))
                                          (throw e)))
                    webserver         (webserver/create-webserver
                                        (-> @config :webserver :host)
                                        (let [p (-> @config :webserver :port)]
                                          (cond-> p (string? p) Integer/parseInt))
                                        database
                                        @service-container)
                    domain-workers    (if (= :test profile)
                                        (reify java.io.Closeable (close [_]))
                                        (domain-registry/start-workers! database @config))]
          (log/info "🚀 Starting system - host:" (-> @config :webserver :host) ", port:" (-> @config :webserver :port) "- Auto-restart works!")
          (json-config/init!)
          (do-with-state {:config            @config
                          :database          database
                          :models-data       @models-data
                          :service-container @service-container
                          :ws                webserver}))))))

(def with-my-system
  (my-system {:interval "every now and then"}))

(def with-test-system
  "Test system using :test profile (port 8081, test database)"
  (my-system {:profile :test}))

(def scheduler (new-scheduler))

(defn await-scheduler [_state]
  (await-finish scheduler))

(def init (atom #(throw (ex-info "init not set" {}))))

^{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn main []
  (reset! init #(try
                  (with-my-system await-scheduler)
                  (catch Throwable t
                    (log/error t "Fatal startup error - process will exit")
                    (System/exit 1))))
  (future-call @init))

(comment
  (defn publishing-state [do-with-state target-atom]
    #(do (reset! target-atom %)
       (try (do-with-state %)
         (finally (reset! target-atom nil)))))

  (def state* (atom nil))

  @state*

  (reset! init #(with-my-system (-> await-scheduler
                                  (publishing-state state*))))
  ; Run
  (def instance (future-call @init))
  ; Continue using REPL and eventually interrupt
  (future-cancel instance)

  instance

  (future-cancelled? instance)

  (tools-repl/refresh)

  {})
