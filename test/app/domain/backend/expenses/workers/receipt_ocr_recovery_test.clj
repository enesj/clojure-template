(ns app.domain.backend.expenses.workers.receipt-ocr-recovery-test
  (:require
    [app.domain.backend.expenses.services.receipts.status :as receipt-status]
    [app.domain.backend.expenses.workers.receipt-ocr.recovery :as recovery]
    [app.domain.backend.expenses.workers.receipt-ocr.ui-queue :as ui-queue]
    [app.domain.backend.registry :as registry]
    [clojure.test :refer [deftest is testing]]))

(defn- run-sweep
  "Runs a sweep with claimed rows; returns the result plus recorded side effects."
  [claimed opts]
  (let [enqueued (atom [])
        failed (atom [])]
    (with-redefs [recovery/claim-stale! (fn [_ _] claimed)
                  ui-queue/enqueue! (fn [_ _ ids o] (swap! enqueued conj [ids o]) nil)
                  receipt-status/mark-failed! (fn [_ id msg] (swap! failed conj [id msg]) nil)]
      {:result (recovery/recover-stale-receipts! :db {} opts)
       :enqueued @enqueued
       :failed @failed})))

(deftest recover-stale-receipts-does-nothing-when-nothing-stale
  (let [{:keys [result enqueued failed]} (run-sweep [] nil)]
    (is (= {:requeued [] :failed []} result))
    (is (empty? enqueued))
    (is (empty? failed))))

(deftest recover-stale-receipts-requeues-stale-receipts
  (let [{:keys [result enqueued failed]} (run-sweep [{:id :a :retry_count 0}
                                                     {:id :b :retry_count nil}
                                                     {:id :c :retry_count 3}]
                                         nil)]
    (is (= {:requeued [:a :b :c] :failed []} result))
    (is (= [[[:a :b :c] {:source :recovery}]] enqueued))
    (is (empty? failed))))

(deftest recover-stale-receipts-fails-receipts-over-retry-limit
  (let [{:keys [result enqueued failed]} (run-sweep [{:id :a :retry_count 1}
                                                     {:id :b :retry_count 10}]
                                         nil)]
    (is (= {:requeued [:a] :failed [:b]} result))
    (is (= [[[:a] {:source :recovery}]] enqueued))
    (is (= [:b] (mapv first failed)))
    (testing "custom max-retries"
      (is (= {:requeued [] :failed [:a :b]}
             (:result (run-sweep [{:id :a :retry_count 1} {:id :b :retry_count 2}]
                        {:max-retries 1})))))))

(deftest start-workers-closes-started-workers-and-survives-failures
  (let [closed (atom [])
        closeable (fn [k] (reify java.io.Closeable (close [_] (swap! closed conj k))))]
    (with-redefs [registry/enabled-domains [{:workers {:ok (fn [_ _] (closeable :ok))
                                                       :boom (fn [_ _] (throw (ex-info "boom" {})))}}
                                            {:workers {:other (fn [_ _] (closeable :other))}}
                                            {}]]
      (let [workers (registry/start-workers! :db {})]
        (is (empty? @closed))
        (.close ^java.io.Closeable workers)
        (is (= #{:ok :other} (set @closed)))))))
