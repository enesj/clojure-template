(ns app.admin.backend.services.admin.auth
  "Admin auth orchestration; bridge auth service to routes."
  (:require
    [app.shared.type-conversion :as tc]
    [app.template.backend.security.email :as email-privacy]
    [app.template.backend.security.tokens :as token-security]
    [buddy.hashers :as hashers]
    [honey.sql :as hsql]
    [java-time.api :as time]
    [next.jdbc :as jdbc]
    ;; Extends next.jdbc SetParameter for java.time values (Instant etc.) so
    ;; standalone entry points (bb seed-admin, scripts) that call this
    ;; namespace without loading app core can still write timestamps.
    [next.jdbc.date-time]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log])
  (:import
    [java.util UUID]))

;; ============================================================================
;; Token and Password Management
;; ============================================================================

(defn generate-session-token
  "Generate a unique session token"
  []
  (str (UUID/randomUUID)))

(defn hash-session-token
  "Derive the deterministic storage value for an admin session bearer token.

  The raw token is returned only to the caller/browser. Database lookups store and
  compare this SHA-256 value so a DB dump does not contain directly reusable
  admin session bearer tokens. Nil remains nil so missing-token callers keep the
  existing no-match behavior."
  [token]
  (token-security/hash-token token))

(defn hash-password
  "Hash a password using bcrypt+sha512 for secure storage.
   Uses 12 iterations (2^12 rounds) for good security vs performance balance."
  [password]
  (hashers/derive password {:alg :bcrypt+sha512 :iterations 12}))

(defn verify-password
  "Verify a password against a stored bcrypt hash.
   Returns true if the password matches the stored hash, false otherwise."
  [password stored-hash]
  (hashers/check password stored-hash))

(defn verify-bcrypt-password
  "Verify password using bcrypt securely."
  [password password-hash]
  (try
    (hashers/check password password-hash)
    (catch Exception _ false)))

;; ============================================================================
;; Admin User Management
;; ============================================================================

(defn find-admin-by-email
  "Find an admin by email address using the blind lookup hash."
  [db email]
  (jdbc/execute-one! db
    (hsql/format {:select [:*]
                  :from [:admins]
                  :where (email-privacy/email-match-clause :email_lookup_hash :email email)
                  :limit 1})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn find-admin-by-id
  "Find an admin by id"
  [db admin-id]
  (jdbc/execute-one! db
    (hsql/format {:select [:*]
                  :from [:admins]
                  :where [:= :id admin-id]})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn create-admin!
  "Creates a new admin user with hashed password and encrypted email storage."
  [db {:keys [email password full_name role] :as _admin-data}]
  (let [admin-id (UUID/randomUUID)
        now (time/instant)]
    (jdbc/execute! db (hsql/format
                        {:insert-into :admins
                         :values [(email-privacy/merge-email-storage
                                    {:id admin-id
                                     :password_hash (hash-password password)
                                     :full_name full_name
                                     :role (tc/cast-for-database :admin-role (or role "admin"))
                                     :status (tc/cast-for-database :admin-status "active")
                                     :created_at now}
                                    email)]}))))

;; ============================================================================
;; Authentication
;; ============================================================================

(defn authenticate-admin
  "Authenticate an admin with email and password."
  [db email password]
  (let [normalized-email (email-privacy/normalize-email email)]
    (some-> (find-admin-by-email db normalized-email)
      (as-> admin
        (when (= (str (:status admin)) "active")
          (let [password-hash (:password_hash admin)]
            (if (verify-bcrypt-password password password-hash)
              (do
                (log/info "Admin authentication successful"
                  {:email-masked (email-privacy/mask-email normalized-email)
                   :admin-ref (email-privacy/admin-ref (:id admin))})
                (-> admin
                  (dissoc :password_hash :email_ciphertext :email_lookup_hash :email_key_version)
                  (assoc :email (email-privacy/resolve-email admin))))
              (do
                (log/warn "Admin authentication failed - invalid password"
                  {:email-masked (email-privacy/mask-email normalized-email)})
                nil))))))))

;; ============================================================================
;; Session Management (DB-backed)
;; ============================================================================

(defn create-admin-session!
  "Create a new admin session and persist it so sessions survive BE reloads."
  [db admin-id ip-address user-agent]
  (let [session-id (UUID/randomUUID)
        token (generate-session-token)
        token-storage (hash-session-token token)
        now (time/instant)
        expires-at (time/plus now (time/hours 8))]
    (jdbc/execute-one! db
      (hsql/format {:insert-into :admin_sessions
                    :values [{:id session-id
                              :admin_id admin-id
                              :token token-storage
                              :created_at now
                              :last_activity now
                              :expires_at expires-at
                              :ip_address ip-address
                              :user_agent user-agent}]})
      {:builder-fn rs/as-unqualified-lower-maps})
    {:id session-id
     :token token
     :expires_at expires-at}))

(defn get-admin-by-session-with-context
  "Get admin by session token. The legacy name is retained for callers."
  [db token]
  (let [now (time/instant)
        token-storage (hash-session-token token)
        row (jdbc/execute-one! db
              (hsql/format {:select [:a.*]
                            :from   [[:admin_sessions :s]]
                            :join   [[:admins :a] [:= :s.admin_id :a.id]]
                            :where  [:and
                                     [:= :s.token token-storage]
                                     [:> :s.expires_at now]]})
              {:builder-fn rs/as-unqualified-lower-maps})]
    (when row
      {:admin (-> row
                (dissoc :password_hash :email_ciphertext :email_lookup_hash :email_key_version)
                (assoc :email (email-privacy/resolve-email row)))})))

(defn update-session-activity!
  "Update last activity timestamp for a session"
  [db token]
  (jdbc/execute-one! db
    (hsql/format {:update :admin_sessions
                  :set {:last_activity (time/instant)}
                  :where [:= :token (hash-session-token token)]})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn invalidate-session!
  "Invalidate an admin session"
  [db token]
  (jdbc/execute-one! db
    (hsql/format {:delete-from :admin_sessions
                  :where [:= :token (hash-session-token token)]})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn invalidate-all-admin-sessions!
  "Invalidate all sessions for an admin"
  [db admin-id]
  (jdbc/execute-one! db
    (hsql/format {:delete-from :admin_sessions
                  :where [:= :admin_id admin-id]})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn count-active-sessions
  "Return number of non-expired admin sessions."
  [db]
  (try
    (let [now (time/instant)
          row (jdbc/execute-one! db
                (hsql/format {:select [[[:count :*] :count]]
                              :from [:admin_sessions]
                              :where [:> :expires_at now]})
                {:builder-fn rs/as-unqualified-lower-maps})]
      (or (:count row) 0))
    (catch Exception e
      (log/warn e "Failed to count active admin sessions")
      0)))
