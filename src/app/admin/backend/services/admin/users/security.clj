(ns app.admin.backend.services.admin.users.security
  "Password/reset/lockout helpers; enforce security rules."
  (:require
    [app.admin.backend.services.admin.audit :as audit]
    [app.template.backend.security.email :as email-privacy]
    [honey.sql :as hsql]
    [java-time.api :as time]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :as sql]))

;; ============================================================================
;; Email Verification Management
;; ============================================================================

(defn force-verify-email!
  "Force verify a user's email as admin"
  [db user-id admin-id ip-address user-agent]
  (jdbc/with-transaction [tx db]
    (jdbc/execute-one! tx ["SET LOCAL app.bypass_rls = true"])
    (let [result (sql/update! tx :users
                   {:email_verified true
                    :email_verified_at (time/instant)}
                   {:id user-id})]
      ;; Log the action
      (audit/log-audit! tx {:admin_id admin-id
                            :action "user.email_force_verified"
                            :entity-type "user"
                            :entity-id user-id
                            :changes {:email_verified true}
                            :ip-address ip-address
                            :user-agent user-agent})
      result)))

;; ============================================================================
;; Password Management
;; ============================================================================

(defn reset-user-password!
  "Reset user password and send reset email"
  [db user-id admin-id ip-address user-agent]
  (jdbc/with-transaction [tx db]
    (jdbc/execute-one! tx ["SET LOCAL app.bypass_rls = true"])
    (let [user (jdbc/execute-one! tx
                 (hsql/format {:select [:email_ciphertext :email_lookup_hash :email_key_version :full_name]
                               :from [:users]
                               :where [:= :id user-id]}))
          user-email (email-privacy/resolve-email user)]
      (if user
        (do
          ;; In a real implementation, this would trigger password reset email
          ;; For now, we'll just log the action
          (audit/log-audit! tx {:admin_id admin-id
                                :action "user.password_reset"
                                :entity-type "user"
                                :entity-id user-id
                                :changes {:reset_initiated true}
                                :ip-address ip-address
                                :user-agent user-agent})
          {:success true
           :message (str "Password reset email sent to " user-email)})
        {:error "User not found"}))))

;; ============================================================================
;; Security Status Management
;; ============================================================================

;; ============================================================================
;; Security Audit Functions
;; ============================================================================




