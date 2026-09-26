#!/usr/bin/env clj

(ns email-legacy-backfill
  (:require
    [app.template.backend.migrations.simple-repl :as mig]
    [app.template.backend.security.email-legacy-backfill :as backfill]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [next.jdbc :as jdbc]))

(defn- usage
  ([] (usage nil))
  ([msg]
   (when msg
     (binding [*out* *err*]
       (println msg)
       (println)))
   (println "Backfill email_lookup_hash/email_ciphertext from the legacy plaintext email column.")
   (println)
   (println "Usage:")
   (println "  bb backfill-email-privacy [dev|test|prod] [options]")
   (println)
   (println "Options:")
   (println "  --apply           Perform writes. Default is dry-run.")
   (println "  --check-complete  Verify no rows are missing email storage fields; exits non-zero if incomplete.")
   (println "  --limit N         Process at most N candidates per table.")
   (println "  --yes             Skip confirmation for --apply.")
   (println "  --pretty          Pretty-print the EDN result.")
   (println "  --help            Show this help.")
   (println)
   (println "Safety:")
   (println "  - Dry-run is the default; the legacy email column is never modified or dropped.")
   (println "  - Storage fields are computed in application code with the email-privacy keys.")
   (println "  - dev/test profiles use the built-in dev keys; prod requires EMAIL_PRIVACY_* env keys.")
   (println "  - Safe to re-run: only rows with missing storage fields are processed.")))

(defn- parse-long!
  [label s]
  (try
    (Long/parseLong (str s))
    (catch Exception _
      (throw (ex-info (str "Invalid value for " label)
               {:label label
                :value s})))))

(defn- normalize-profile
  [s]
  (let [profile (keyword (str s))]
    (when-not (contains? #{:dev :test :prod} profile)
      (throw (ex-info "Profile must be one of dev, test, or prod"
               {:value s})))
    profile))

(defn- parse-args
  [args]
  (loop [args args
         parsed {:profile :dev
                 :apply? false
                 :check-complete? false
                 :yes? false
                 :pretty? false}]
    (let [[a b & more] args]
      (cond
        (nil? a)
        parsed

        (or (= a "--help") (= a "-h"))
        (do
          (usage)
          (System/exit 0))

        (contains? #{"dev" "test" "prod"} a)
        (recur (cons b more) (assoc parsed :profile (normalize-profile a)))

        (= a "--apply")
        (recur (cons b more) (assoc parsed :apply? true))

        (= a "--check-complete")
        (recur (cons b more) (assoc parsed :check-complete? true))

        (or (= a "--yes") (= a "--force"))
        (recur (cons b more) (assoc parsed :yes? true))

        (= a "--pretty")
        (recur (cons b more) (assoc parsed :pretty? true))

        (= a "--limit")
        (do
          (when-not b
            (throw (ex-info "Missing value for --limit" {})))
          (recur more (assoc parsed :limit (parse-long! "--limit" b))))

        :else
        (throw (ex-info (str "Unknown arg: " a) {:arg a}))))))

(defn- datasource-for-profile
  [profile]
  (jdbc/get-datasource {:jdbcUrl (mig/get-jdbc-url profile)}))

(defn- confirm!
  [{:keys [profile]}]
  (println "⚠️  This will write encrypted email storage fields on legacy rows.")
  (println (str "🎯 Target profile: " (name profile)))
  (println)
  (print "Type 'BACKFILL LEGACY EMAILS' to confirm: ")
  (flush)
  (= "BACKFILL LEGACY EMAILS" (str/trim (read-line))))

(defn- print-result!
  [{:keys [pretty?]} result]
  (if pretty?
    (pprint/pprint result)
    (prn result))
  (flush))

(defn -main
  [& args]
  (try
    (let [{:keys [profile apply? check-complete? yes? limit] :as opts} (parse-args args)
          _ (System/setProperty "app.environment" (name profile))
          db (datasource-for-profile profile)
          dry-run? (not apply?)]
      (if check-complete?
        (let [result (backfill/backfill-status db)]
          (println (str "Checking legacy-email backfill status"
                     " profile=" (name profile)))
          (print-result! opts result)
          (when-not (:complete? result)
            (System/exit 1)))
        (do
          (println (str "Running email-legacy-backfill"
                     " profile=" (name profile)
                     " dry-run=" dry-run?
                     " limit=" (or limit "none")))
          (when (and apply? (not (or yes? (confirm! opts))))
            (println "❌ Cancelled.")
            (System/exit 1))
          (let [result (backfill/backfill-legacy-emails!
                         db
                         (cond-> {:dry-run? dry-run?}
                           limit (assoc :limit limit)))]
            (print-result! opts result)))))
    (catch clojure.lang.ExceptionInfo e
      (binding [*out* *err*]
        (usage (or (.getMessage e) "email-legacy-backfill failed")))
      (System/exit 1))
    (catch Exception e
      (binding [*out* *err*]
        (println "email-legacy-backfill failed:")
        (println (.getMessage e)))
      (System/exit 1))))

(apply -main *command-line-args*)
