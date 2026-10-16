(ns app.domain.backend.expenses.services.article-resolution.config
  "Configuration for the background article resolver (alias -> article)."
  (:require
    [app.domain.backend.expenses.integrations.claude.config :as claude-cfg]
    [app.domain.backend.expenses.integrations.typesafe :as typesafe]
    [clojure.string :as str]))

(defn build-config
  "Resolver config from app config (`:article-resolver`) + env.
  Env overrides: ARTICLE_RESOLVER_ENABLED (true/false), ARTICLE_RESOLVER_APPLY_MODE (dry-run|auto).
  `:apply-mode` is :dry-run (never mutates the catalog) or :auto."
  [app-config]
  (let [c           (or (:article-resolver app-config) {})
        env-enabled (some-> (System/getenv "ARTICLE_RESOLVER_ENABLED") str/trim str/lower-case not-empty)
        env-mode    (some-> (System/getenv "ARTICLE_RESOLVER_APPLY_MODE") str/trim not-empty keyword)]
    {:enabled?                 (if env-enabled (= "true" env-enabled) (true? (:enabled? c)))
     :apply-mode               (or env-mode (:apply-mode c) :dry-run)
     :interval-seconds         (or (:interval-seconds c) 60)
     :lookback-hours           (or (:lookback-hours c) 24)
     :claim-limit              (or (:claim-limit c) 200)
     :batch-size               (or (:batch-size c) 50)
     ;; 0 = call Claude on every sweep; >0 = wait until batch-size rows are pending or the oldest waited this long
     :settle-minutes           (or (:settle-minutes c) 0)
     :auto-apply-threshold     (or (:auto-apply-threshold c) 80)
     :noise-threshold          (or (:noise-threshold c) 90)
     :allow-new-subcategories? (get c :allow-new-subcategories? true)
     :allow-new-manufacturers? (get c :allow-new-manufacturers? true)
     :excluded-categories      (set (or (:excluded-categories c) ["Subscriptions" "Utilities"]))
     :stale-processing-seconds (or (:stale-processing-seconds c) 600)
     :max-attempts             (or (:max-attempts c) 3)
     :candidate-limit          (or (:candidate-limit c) 5)
     :claude                   (claude-cfg/build-config app-config)
     ;; Optional Jev (TypeSafe) second opinion on Claude's proposals; off unless enabled with a key.
     :verifier                 (merge {:enabled?                false
                                       :mode                    :full ; or :fill-category
                                       :min-category-confidence 0.7
                                       :min-same-product        0.5
                                       :min-manufacturer        0.5}
                                 (:verifier c)
                                 {:typesafe (typesafe/build-config app-config)})}))
