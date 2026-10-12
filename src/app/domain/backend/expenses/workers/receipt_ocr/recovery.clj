(ns app.domain.backend.expenses.workers.receipt-ocr.recovery
  "Recovery of receipts left in-flight (`parsing`/`extracting`) by a restart or crash.

  OCR tasks run in-process, so a system restart kills them without resetting the
  receipt status. A periodic sweep claims receipts that have not been touched for
  `stale-seconds` and re-queues them through the UI OCR queue."
  (:require
    [app.domain.backend.expenses.services.receipts.status :as receipt-status]
    [app.domain.backend.expenses.services.receipts.storage :as storage]
    [app.domain.backend.expenses.workers.receipt-ocr.ui-queue :as ui-queue]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Executors ThreadFactory TimeUnit]))

(def default-stale-seconds
  "A live OCR task finishes well within this window (parse + refine < ~2 min)."
  300)

(def default-max-retries
  "Receipts reprocessed at least this many times are failed instead of re-queued,
  so a receipt that keeps killing the process cannot loop forever."
  10)

(defn claim-stale!
  "Atomically claim in-flight receipts untouched for `stale-seconds` by bumping
  `updated_at`, so concurrent or repeated sweeps cannot pick them up twice.

  Receipts linked to an expense are never touched (they cannot be reparsed).
  Returns the claimed rows as `{:id .. :retry_count ..}`."
  [db stale-seconds]
  (jdbc/execute!
    db
    (sql/format
      {:update :receipts
       :set {:updated_at [:raw "NOW()"]}
       :where [:and
               [:in :status (mapv storage/receipt-status-cast ["parsing" "extracting"])]
               [:is :expense_id nil]
               [:< :updated_at [:raw (format "NOW() - INTERVAL '%d seconds'" (long stale-seconds))]]]
       :returning [:id :retry_count]})
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn recover-stale-receipts!
  "Re-queue (or fail, once `max-retries` is reached) receipts stuck in-flight.

  Returns `{:requeued [ids] :failed [ids]}`."
  ([db app-config]
   (recover-stale-receipts! db app-config nil))
  ([db app-config {:keys [stale-seconds max-retries]
                   :or {stale-seconds default-stale-seconds
                        max-retries default-max-retries}}]
   (let [claimed (claim-stale! db stale-seconds)
         exhausted? #(>= (long (or (:retry_count %) 0)) (long max-retries))
         failed-ids (mapv :id (filter exhausted? claimed))
         requeue-ids (mapv :id (remove exhausted? claimed))]
     (doseq [id failed-ids]
       (receipt-status/mark-failed! db id "Receipt processing was interrupted repeatedly"))
     (when (seq requeue-ids)
       (ui-queue/enqueue! db app-config requeue-ids {:source :recovery}))
     (when (seq claimed)
       (log/warn "Recovered receipts left in-flight"
         {:requeued-ids requeue-ids
          :failed-ids failed-ids
          :stale-seconds stale-seconds}))
     {:requeued requeue-ids
      :failed failed-ids})))

(def ^:private daemon-thread-factory
  (reify ThreadFactory
    (newThread [_ r]
      (doto (Thread. ^Runnable r "receipt-ocr-recovery")
        (.setDaemon true)))))

(defn start!
  "Start the periodic recovery sweep. Returns a Closeable that stops it."
  ([db app-config]
   (start! db app-config nil))
  ([db app-config {:keys [initial-delay-seconds interval-seconds]
                   :or {initial-delay-seconds 15
                        interval-seconds 60}
                   :as opts}]
   (let [executor (Executors/newSingleThreadScheduledExecutor daemon-thread-factory)
         sweep (fn []
                 (try
                   (recover-stale-receipts! db app-config (select-keys opts [:stale-seconds :max-retries]))
                   (catch Throwable t
                     (log/warn t "Receipt OCR recovery sweep failed"))))]
     (.scheduleWithFixedDelay executor ^Runnable sweep
       (long initial-delay-seconds) (long interval-seconds) TimeUnit/SECONDS)
     (log/info "Receipt OCR recovery sweep started"
       {:interval-seconds interval-seconds
        :stale-seconds (or (:stale-seconds opts) default-stale-seconds)})
     (reify java.io.Closeable
       (close [_]
         (.shutdownNow executor))))))

(comment
  (recover-stale-receipts! (:database @system.state/state) (:config @system.state/state))
  :rcf)
