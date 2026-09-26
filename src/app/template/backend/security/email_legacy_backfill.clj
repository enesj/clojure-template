(ns app.template.backend.security.email-legacy-backfill
  "Application-level backfill of legacy plaintext emails.

   Rows created before the email-privacy cutover carry only the legacy
   `email` column. Auth and invitation lookups go through the blind
   `email_lookup_hash` index, so such rows are invisible to login flows
   (401 \"Invalid credentials\" despite a correct password).

   This backfill computes the canonical encrypted-at-rest storage fields
   with the application's own `email-privacy` helpers and writes them back,
   leaving the legacy column untouched until the deferred 0062 cutover
   drops it. Safe to re-run: only rows with missing storage fields are
   selected."

  (:require
    [app.template.backend.security.email :as email-privacy]
    [clojure.string :as str]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(def legacy-email-tables
  "Tables that carry the legacy `email` column plus the encrypted-at-rest
   triple (email_lookup_hash, email_ciphertext, email_key_version)."
  ["admins" "users" "admin_invitations" "tenant_invitations"])

(defn- qualifying-rows
  "Rows in `table` that still need email storage backfill."
  [ds table]
  (jdbc/execute! ds
    [(format "select id, email from %s
              where email is not null
                and (email_lookup_hash is null
                     or email_ciphertext is null
                     or email_key_version is null)"
             table)]
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn- backfill-row!
  "Write canonical email storage fields for one row. Returns
   :updated, :skipped (email not normalizable) or {:failed reason}."
  [ds table {:keys [id email]}]
  (if-let [{:keys [email_ciphertext email_lookup_hash email_key_version]}
           (email-privacy/email-storage email)]
    (try
      (jdbc/execute-one! ds
        [(format "update %s
                  set email_lookup_hash = ?, email_ciphertext = ?, email_key_version = ?
                  where id = ?"
                 table)
         email_lookup_hash email_ciphertext email_key_version id])
      :updated
      (catch Exception e
        {:failed (str "update failed: " (ex-message e))}))
    :skipped))

(defn- backfill-table
  [ds table {:keys [dry-run? limit]}]
  (loop [rows (cond->> (qualifying-rows ds table) limit (take limit))
         acc {:pending 0 :updated 0 :skipped 0 :failed 0 :examples []}]
    (if-let [row (first rows)]
      (let [result (if dry-run? :pending (backfill-row! ds table row))
            acc' (cond
                   (= :pending result)
                   (-> acc
                       (update :pending inc)
                       (update :examples #(take 5 (conj % {:id (str (:id row))
                                                           :email-masked (email-privacy/mask-email (:email row))}))))

                   (= :updated result) (update acc :updated inc)
                   (= :skipped result) (update acc :skipped inc)
                   (map? result) (-> acc
                                     (update :failed inc)
                                     (assoc :last-error (val (first result)))))]
        (recur (rest rows) acc'))
      acc)))

(defn backfill-legacy-emails!
  "Backfill email_lookup_hash/email_ciphertext/email_key_version from the
   legacy plaintext `email` column for all `legacy-email-tables`.

   Options:
   - :dry-run?  (default true) only report what would change
   - :limit     cap processed rows per table

   Returns a summary map; emails are masked in examples."
  [ds {:keys [dry-run? limit] :or {dry-run? true}}]
  (let [tables (mapv #(backfill-table ds % {:dry-run? dry-run? :limit limit})
                     legacy-email-tables)
        ;; Complete means nothing remains missing, regardless of what this
        ;; run processed (a successful apply should report :complete? true).
        remaining (reduce + (map #(count (qualifying-rows ds %))
                                 legacy-email-tables))]
    {:dry-run? dry-run?
     :complete? (zero? remaining)
     :tables (zipmap (map keyword legacy-email-tables) tables)}))

(defn backfill-status
  "Pending backfill counts per table. :complete? is true when no row in any
   table is missing its email storage fields."
  [ds]
  (let [tables (mapv #(let [rows (qualifying-rows ds %)]
                        {:table %
                         :pending (count rows)
                         :examples (->> rows
                                        (take 5)
                                        (mapv (fn [row]
                                                {:id (str (:id row))
                                                 :email-masked (email-privacy/mask-email (:email row))})))})
                     legacy-email-tables)
        total (reduce + (map :pending tables))]
    {:complete? (zero? total)
     :pending-total total
     :tables (into {} (map (juxt #(keyword (:table %)) #(dissoc % :table))) tables)}))
