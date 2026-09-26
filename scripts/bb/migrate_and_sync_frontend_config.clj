#!/usr/bin/env bb

(ns migrate-and-sync-frontend-config
  "Run DB migrations, sync EDN bootstrap defaults with schema, then validate."
  (:require
    [babashka.process :as process]
    [clojure.string :as str]))

(defn- die!
  ([msg] (die! msg 2))
  ([msg code]
   (binding [*out* *err*]
     (println msg))
   (System/exit code)))

(defn- require-arg
  [flag value]
  (when (or (nil? value) (str/blank? value))
    (die! (str flag " requires a value"))))

(defn- safe-profile
  [s]
  (let [s (str/trim s)]
    (when-not (re-matches #"[A-Za-z0-9_-]+" s)
      (die! (str "Invalid profile: " s)))
    s))

(defn- parse-args
  [args]
  (loop [args args
         opts {:profile "dev"
               :pass-args []}]
    (if (empty? args)
      opts
      (let [[a & more] args]
        (case a
          "--profile" (let [[v & more2] more]
                        (require-arg "--profile" v)
                        (recur more2 (assoc opts :profile (safe-profile v))))
          "--only" (let [[v & more2] more]
                     (require-arg "--only" v)
                     (recur more2 (update opts :pass-args into ["--only" v])))
          "--skip" (let [[v & more2] more]
                     (require-arg "--skip" v)
                     (recur more2 (update opts :pass-args into ["--skip" v])))
          "--allowlist" (let [[v & more2] more]
                          (require-arg "--allowlist" v)
                          (recur more2 (update opts :pass-args into ["--allowlist" v])))
          "--schema" (let [[v & more2] more]
                       (require-arg "--schema" v)
                       (recur more2 (update opts :pass-args into ["--schema" v])))
          (die! (str "Unknown arg: " a)))))))

(defn- run-cmd!
  [& cmd]
  ;; apply splats cmd into separate process arguments; passing the
  ;; collection directly would stringify it into a single program name.
  (let [result (apply process/shell {:out :inherit :err :inherit} cmd)]
    (when-not (zero? (:exit result))
      (die! (str "Command failed: " (str/join " " cmd)) (:exit result)))))

(defn- migrate!
  [profile]
  (println "=== Running migrations ===")
  (let [form (format "(require '[app.template.backend.migrations.simple-repl :as mig]) (mig/migrate! :%s)" profile)]
    (run-cmd! "clj" "-M" "-e" form)))

(defn- sync-frontend-config!
  [pass-args]
  (println "=== Applying frontend config sync ===")
  (apply run-cmd! "bb" "sync-frontend-config" "--apply" pass-args))

(defn- validate-frontend-config!
  [pass-args]
  (println "=== Validating frontend config ===")
  (apply run-cmd! "bb" "validate-frontend-config" pass-args))

(defn -main
  [& args]
  (let [{:keys [profile pass-args]} (parse-args args)]
    (migrate! profile)
    (sync-frontend-config! pass-args)
    (validate-frontend-config! pass-args)
    (println "\nAll steps completed successfully.")))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
