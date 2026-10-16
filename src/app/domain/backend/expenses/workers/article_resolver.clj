(ns app.domain.backend.expenses.workers.article-resolver
  "Background worker: periodically resolves new unmapped article aliases to catalog articles.
  Dev-only by default (see :article-resolver in config/base.edn); not started under the :test profile."
  (:require
    [app.domain.backend.expenses.services.article-resolution.config :as config]
    [app.domain.backend.expenses.services.article-resolution.core :as core]
    [clojure.string :as str]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Executors ThreadFactory TimeUnit]))

(def ^:private initial-delay-seconds 30)

(def ^:private daemon-thread-factory
  (reify ThreadFactory
    (newThread [_ r]
      (doto (Thread. ^Runnable r "article-resolver")
        (.setDaemon true)))))

(def ^:private noop-closeable
  (reify java.io.Closeable
    (close [_] nil)))

(defn start!
  "Start the resolver sweep. Returns a java.io.Closeable that stops it. A no-op Closeable is
  returned (and the reason logged) when the resolver is disabled or has no API key."
  [db app-config]
  (let [cfg (config/build-config app-config)]
    (cond
      (not (:enabled? cfg))
      (do (log/info "Article resolver disabled (:article-resolver :enabled? false)")
          noop-closeable)

      (str/blank? (get-in cfg [:claude :api-key]))
      (do (log/info "Article resolver not started: no Anthropic API key (ANTHROPIC_API_KEY or [:secrets :anthropic :api-key])")
          noop-closeable)

      :else
      (let [executor (Executors/newSingleThreadScheduledExecutor daemon-thread-factory)
            backoff  (atom {:fails 0 :skip 0})
            sweep    (fn []
                       (try
                         (if (pos? (:skip @backoff))
                           (swap! backoff update :skip dec)
                           (let [summary (core/run-sweep! db app-config)]
                             (if (:config-error summary)
                               ;; Credentials/workspace rejected: back off exponentially (max 60 sweeps).
                               (let [{:keys [fails]} (swap! backoff (fn [{:keys [fails]}]
                                                                      {:fails (inc fails)
                                                                       :skip (min 60 (bit-shift-left 1 (inc fails)))}))]
                                 (log/warn "Article resolver: Claude rejected the credentials; backing off"
                                   {:consecutive-failures fails}))
                               (reset! backoff {:fails 0 :skip 0}))
                             (when (pos? (:claimed summary))
                               (log/info "Article resolver sweep" summary))))
                         (catch Throwable t
                           (log/warn t "Article resolver sweep failed"))))]
        (.scheduleWithFixedDelay executor ^Runnable sweep
          (long initial-delay-seconds) (long (:interval-seconds cfg)) TimeUnit/SECONDS)
        (log/info "Article resolver started"
          {:interval-seconds (:interval-seconds cfg)
           :apply-mode (:apply-mode cfg)
           :model (get-in cfg [:claude :model])})
        (reify java.io.Closeable
          (close [_]
            (.shutdownNow executor)))))))

(comment
  (def w (start! (:database @system.state/state) (:config @system.state/state)))
  (.close w)
  :rcf)
