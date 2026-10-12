(ns app.domain.backend.expenses.workers.receipt-ocr-core-test
  (:require
    [app.domain.backend.expenses.integrations.cerebras :as cerebras]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-markdown :as receipt-md]
    [app.domain.backend.expenses.integrations.mistral-ocr :as mistral-ocr]
    [app.domain.backend.expenses.integrations.ocr-provider :as ocr-provider]
    [app.domain.backend.expenses.services.places-api :as places-api]
    [app.domain.backend.expenses.services.receipts.image-preprocess :as image-preprocess]
    [app.domain.backend.expenses.services.receipts.queries :as receipt-queries]
    [app.domain.backend.expenses.services.receipts.status :as receipt-status]
    [app.domain.backend.expenses.workers.receipt-ocr.common :as common]
    [app.domain.backend.expenses.workers.receipt-ocr.core :as core]
    [app.domain.backend.expenses.workers.receipt-ocr.extraction :as extraction]
    [app.domain.backend.expenses.workers.receipt-ocr.refine :as refine]
    [app.domain.backend.expenses.workers.receipt-ocr.runner :as runner]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]])
  (:import
    [java.util.concurrent CountDownLatch TimeUnit]))

(deftest process-extract-auto-retries-review-required-once
  (let [process-extract! #'runner/run-receipt!
        receipt-id (java.util.UUID/randomUUID)
        calls (atom {:claim 0 :ocr 0 :persist 0 :retry 0})]
    (with-redefs [receipt-status/claim-for-extracting! (fn [_db _rid _opts]
                                                         (swap! calls update :claim inc)
                                                         true)
                  common/read-receipt-bytes! (fn [_receipt _opts]
                                               {:bytes (.getBytes "x")})
                  image-preprocess/prepare-for-ocr (fn [{:keys [bytes content-type] :as _req}]
                                                     {:bytes bytes
                                                      :content-type content-type
                                                      :preprocessed? false})
                  mistral-ocr/ocr-extract! (fn [_cfg _req]
                                             (swap! calls update :ocr inc)
                                             {})
                  extraction/persist-extract-result! (fn [_db _rid _extract-result _opts]
                                                       (swap! calls update :persist inc)
                                                       (if (= 1 (:persist @calls))
                                                         {:receipt-id receipt-id :stage :extract :result :ok :status "review_required"}
                                                         {:receipt-id receipt-id :stage :extract :result :ok :status "extracted"}))
                  receipt-status/retry-extraction! (fn [_db _rid]
                                                     (swap! calls update :retry inc)
                                                     nil)]
      (let [res (process-extract! nil {:api-key "k"} {:id receipt-id :status "uploaded" :content_type "image/jpeg"} {:lease-seconds 900})]
        ;; Current implementation does not auto-retry review_required.
        (is (= "review_required" (:status res)))
        (is (= 1 (:claim @calls)))
        (is (= 1 (:ocr @calls)))
        (is (= 1 (:persist @calls)))
        (is (= 0 (:retry @calls)))))))

(deftest process-extract-preprocesses-image-before-mistral
  (let [process-extract! #'runner/run-receipt!
        receipt-id (java.util.UUID/randomUUID)
        seen (atom nil)
        prepared-bytes (.getBytes "prepared")]
    (with-redefs [receipt-status/claim-for-extracting! (fn [_db _rid _opts] true)
                  common/read-receipt-bytes! (fn [_receipt _opts]
                                               {:bytes (.getBytes "original")
                                                :path "/tmp/receipt-orig.heic"})
                  image-preprocess/prepare-for-ocr (fn [_req]
                                                     {:bytes prepared-bytes
                                                      :content-type "image/jpeg"
                                                      :preprocessed? true})
                  mistral-ocr/ocr-extract! (fn [_cfg req]
                                             (reset! seen req)
                                             {:raw {}
                                              :parsed-markdown ""})
                  extraction/persist-extract-result! (fn [_db _rid _extract-result _opts]
                                                       {:receipt-id receipt-id
                                                        :stage :extract
                                                        :result :ok
                                                        :status "extracted"})]
      (let [res (process-extract! nil
                  {:api-key "k" :auto-post-after-upload? false}
                  {:id receipt-id
                   :status "uploaded"
                   :content_type "image/heic"
                   :original_filename "r.heic"}
                  {:lease-seconds 900
                   :defer-refine? true})]
        (is (= "extracted" (:status res)))
        (is (= "image/jpeg" (:content-type @seen)))
        (is (= "prepared" (String. ^bytes (:bytes @seen))))))))

(deftest auto-post-extracted-results-finalizes-non-review-required-results-when-auto-post-disabled
  (let [receipt-id (java.util.UUID/randomUUID)
        calls (atom [])
        results [{:receipt-id receipt-id
                  :receipt {:id receipt-id}
                  :stage :extract
                  :result :ok
                  :review-required? false
                  :extract-result {:parsed-markdown ""
                                   :extraction {:items [{:raw_label "ITEM"}]}}
                  :auto-post-after-upload? false}]]
    (with-redefs [extraction/persist-extract-result!
                  (fn [_db rid extract-result opts]
                    (swap! calls conj {:receipt-id rid
                                       :extract-result extract-result
                                       :opts opts})
                    {:receipt-id rid
                     :stage :extract
                     :result :ok
                     :status "extracted"})]
      (let [res (#'refine/auto-post-extracted-results!
                 ::db
                 {:defer-refine? true
                  :auto-post-after-upload? false}
                 results)]
        (is (= 1 (count @calls)))
        (is (= receipt-id (:receipt-id (first @calls))))
        (is (true? (get-in (first @calls) [:opts :persist-item-aliases?])))
        (is (false? (get-in (first @calls) [:opts :auto-post-after-upload?])))
        (is (= "extracted" (:status (first res))))))))

(deftest refine-review-required-results-respects-concurrency-limit
  (let [limit 2
        opts {:cerebras-cfg {:refine-concurrency limit :refine-timeout-ms 5000}}
        started (CountDownLatch. limit)
        release (CountDownLatch. 1)
        active (atom 0)
        max-active (atom 0)
        results (vec (for [_ (range 5)]
                       {:receipt {:id (java.util.UUID/randomUUID)}
                        :review-required? true
                        :extract-result {}}))
        runner (future
                 (clojure.core/with-redefs-fn
                   {#'refine/maybe-refine-review-required
                    (fn [_db _receipt _extract-result persist-result _opts]
                      (let [n (swap! active inc)]
                        (swap! max-active max n)
                        (.countDown started)
                        (.await release 2 TimeUnit/SECONDS)
                        (swap! active dec)
                        (assoc persist-result :refined? true)))}
                   (fn []
                     (#'refine/refine-review-required-results! nil opts results))))]
    (is (.await started 1 TimeUnit/SECONDS))
    (is (<= @max-active limit))
    (.countDown release)
    (let [res @runner]
      (is (= (count results) (count res)))
      (is (every? :refined? res)))))

(deftest refine-review-required-results-keeps-processing-on-failure
  (let [opts {:cerebras-cfg {:refine-concurrency 3 :refine-timeout-ms 5000}}
        ok-id (java.util.UUID/randomUUID)
        bad-id (java.util.UUID/randomUUID)
        results [{:receipt {:id ok-id}
                  :review-required? true
                  :extract-result {}}
                 {:receipt {:id bad-id}
                  :review-required? true
                  :extract-result {}}]
        res (clojure.core/with-redefs-fn
              {#'refine/maybe-refine-review-required
               (fn [_db receipt _extract-result persist-result _opts]
                 (if (= bad-id (:id receipt))
                   (throw (ex-info "boom" {:receipt-id bad-id}))
                   (assoc persist-result :refined? true)))}
              (fn []
                (#'refine/refine-review-required-results! nil opts results)))]
    (is (true? (get-in res [0 :refined?])))
    (is (nil? (get-in res [1 :refined?])))))

(deftest maybe-refine-review-required-clears-refine-pending-when-skipped
  (let [receipt-id (java.util.UUID/randomUUID)
        cleared (atom [])
        res (clojure.core/with-redefs-fn
              {#'refine/maybe-refine-with-cerebras (fn [_db _receipt extract-result _opts]
                                                  ;; Skip/failed refine: no :llm_refine returned.
                                                     extract-result)
               #'receipt-status/clear-refine-pending! (fn [_db rid]
                                                        (swap! cleared conj rid)
                                                        {:id rid})}
              (fn []
                (#'refine/maybe-refine-review-required
                 ::db
                 {:id receipt-id}
                 {:parsed-markdown "x"}
                 {:receipt-id receipt-id :review-required? true}
                 {:clear-refine-pending? true})))]
    (is (= {:receipt-id receipt-id :review-required? true} res))
    (is (= [receipt-id] @cleared))))

(deftest maybe-refine-with-cerebras-preserves-provider-confidence
  (let [receipt-id (java.util.UUID/randomUUID)
        extract-result {:parsed-markdown "TOTAL: 20,13"
                        :raw {}
                        :extraction {:provider_confidence {:line_total_reliability 0.98
                                                           :selected_total_confidence 0.41}
                                     :items [{:raw_label "A"
                                              :qty 1.0
                                              :unit_price 20.10
                                              :line_total 20.10}]
                                     :totals {:total 20.13}}}
        res (clojure.core/with-redefs-fn
              {#'cerebras/refine-receipt-markdown!
               (fn [_cfg _markdown]
                 {:model "test-model"
                  :extraction {:merchant {:name "Store"}
                               :items [{:raw_label "A"
                                        :qty 1.0
                                        :unit_price 20.10
                                        :line_total 20.10}]
                               :totals {:total 20.13}}})
               #'receipt-queries/get-receipt-refine-context
               (fn [_db _rid]
                 nil)}
              (fn []
                (#'refine/maybe-refine-with-cerebras
                 ::db
                 {:id receipt-id
                  :subject_ref "receipt-subject"
                  :original_filename "IMG_4117.jpeg"}
                 extract-result
                 {:cerebras-cfg {:api-key "k"}
                  :force-refine? true})))]
    (is (= {:line_total_reliability 0.98
            :selected_total_confidence 0.41}
          (get-in res [:extraction :provider_confidence])))
    (is (= "test-model" (get-in res [:llm_refine :model])))))

(deftest maybe-refine-with-cerebras-keeps-ocr-merchant-address-when-llm-has-no-merchant
  (let [receipt-id (java.util.UUID/randomUUID)
        ocr-merchant {:store_name "Podružnica Sarajevo 1"
                      :address "ul. Put života bb, 71000 SARAJEVO"
                      :raw_address "Podružnica Sarajevo 1, ul. Put života bb, 71000 SARAJEVO"}
        extract-result {:parsed-markdown "TOTAL: 1,00"
                        :raw {}
                        :extraction {:merchant ocr-merchant
                                     :items [{:raw_label "A" :qty 1.0 :unit_price 1.00 :line_total 1.00}]
                                     :totals {:total 1.00}}}
        refine! (fn [llm-extraction]
                  (clojure.core/with-redefs-fn
                    {#'cerebras/refine-receipt-markdown!
                     (fn [_cfg _markdown]
                       {:model "test-model" :extraction llm-extraction})
                     #'receipt-queries/get-receipt-refine-context
                     (fn [_db _rid] nil)}
                    (fn []
                      (#'refine/maybe-refine-with-cerebras
                       ::db
                       {:id receipt-id :subject_ref "s" :original_filename "IMG_4046.jpeg"}
                       extract-result
                       {:cerebras-cfg {:api-key "k"} :force-refine? true}))))
        items [{:raw_label "A" :qty 1.0 :unit_price 1.00 :line_total 1.00}]]
    (testing "LLM returned no merchant"
      (is (= ocr-merchant
            (get-in (refine! {:items items :totals {:total 1.00}}) [:extraction :merchant]))))
    (testing "LLM merchant name is merged over the OCR address fields"
      (is (= (assoc ocr-merchant :name "Kid zone")
            (get-in (refine! {:merchant {:name "Kid zone"} :items items :totals {:total 1.00}})
              [:extraction :merchant]))))))

(deftest maybe-refine-with-cerebras-appends-raw-ocr-table-on-small-total-mismatch
  (let [receipt-id (java.util.UUID/randomUUID)
        captured-markdown (atom nil)
        extract-result {:parsed-markdown "KONZUM\n\nTOTAL: 15,50"
                        :raw {:items {:pages []}}
                        :extraction {:items [{:raw_label "SOK COCA COLA"
                                              :qty 1.0
                                              :unit_price 0.25
                                              :line_total 14.45}]
                                     :totals {:total 15.50}}}
        raw-table "| Label | Qty | Price | Total |\n| --- | --- | --- | --- |\n| SOK COCA COLA 0,25L | 1,000x | 1,30 | 1,30E |"
        _res (clojure.core/with-redefs-fn
               {#'cerebras/refine-receipt-markdown!
                (fn [_cfg markdown]
                  (reset! captured-markdown markdown)
                  {:model "test-model"
                   :extraction {:merchant {:name "Store"}
                                :items [{:raw_label "SOK COCA COLA 0,25L"
                                         :qty 1.0
                                         :unit_price 1.30
                                         :line_total 1.30}]
                                :totals {:total 15.50}}})
                #'receipt-queries/get-receipt-refine-context
                (fn [_db _rid]
                  nil)
                #'receipt-md/response->table-markdowns
                (fn [_raw]
                  [raw-table])}
               (fn []
                 (#'refine/maybe-refine-with-cerebras
                  ::db
                  {:id receipt-id
                   :subject_ref "receipt-subject"
                   :original_filename "IMG_4161.jpeg"}
                  extract-result
                  {:cerebras-cfg {:api-key "k"}
                   :force-refine? true})))]
    (is (string? @captured-markdown))
    (is (str/includes? @captured-markdown "Raw OCR table"))
    (is (str/includes? @captured-markdown "SOK COCA COLA 0,25L"))))

(deftest refine-review-required-results-times-out
  (let [opts {:cerebras-cfg {:refine-concurrency 1 :refine-timeout-ms 50}}
        results [{:receipt {:id (java.util.UUID/randomUUID)}
                  :review-required? true
                  :extract-result {}}]
        res (clojure.core/with-redefs-fn
              {#'refine/maybe-refine-review-required
               (fn [_db _receipt _extract-result persist-result _opts]
                 (try
                   (Thread/sleep 200)
                   (catch InterruptedException _))
                 (assoc persist-result :refined? true))}
              (fn []
                (#'refine/refine-review-required-results! nil opts results)))]
    (is (nil? (get-in res [0 :refined?])))))

(deftest refine-review-required-results-clears-refine-pending-on-timeout
  (let [receipt-id (java.util.UUID/randomUUID)
        cleared (atom [])
        opts {:cerebras-cfg {:refine-concurrency 1 :refine-timeout-ms 10}}
        results [{:receipt {:id receipt-id}
                  :review-required? true
                  :extract-result {}}]
        _res (clojure.core/with-redefs-fn
               {#'refine/maybe-refine-review-required
                (fn [_db _receipt _extract-result persist-result _opts]
                  (try
                    (Thread/sleep 200)
                    (catch InterruptedException _))
                  persist-result)
                #'receipt-status/clear-refine-pending!
                (fn [_db rid]
                  (swap! cleared conj rid)
                  {:id rid})}
               (fn []
                 (#'refine/refine-review-required-results! ::db opts results)))]
    (is (= [receipt-id] @cleared))))

(deftest refine-review-required-results-logs-post-process-when-no-eligible-refine
  (let [opts {:cerebras-cfg {:refine-concurrency 1 :refine-timeout-ms 1000}}
        results [{:receipt {:id (java.util.UUID/randomUUID)}
                  :review-required? true
                  :extract-result {}}]
        output (let [w (java.io.StringWriter.)]
                 (binding [*out* w
                           *err* w]
                   (clojure.core/with-redefs-fn
                     {#'refine/maybe-refine-review-required
                      (fn [_db _receipt _extract-result persist-result _opts]
                        persist-result)}
                     (fn []
                       (#'refine/refine-review-required-results! nil opts results)))
                   (str w)))]
    (is (str/includes? output "Review-required post-process starting (no eligible refine)"))
    (is (str/includes? output "Review-required post-process complete (no eligible refine)"))
    (is (not (str/includes? output "Cerebras parallel refine starting")))))

(deftest refine-review-required-results-logs-cerebras-when-eligible-refine
  (let [opts {:cerebras-cfg {:refine-concurrency 1 :refine-timeout-ms 1000}}
        results [{:receipt {:id (java.util.UUID/randomUUID)
                            :subject_ref "receipt-subject"}
                  :review-required? true
                  :extract-result {}}]
        output (let [w (java.io.StringWriter.)]
                 (binding [*out* w
                           *err* w]
                   (clojure.core/with-redefs-fn
                     {#'refine/receipt-eligible-for-refine?
                      (fn [_db _receipt _opts]
                        true)
                      #'refine/maybe-refine-review-required
                      (fn [_db _receipt _extract-result persist-result _opts]
                        (assoc persist-result :extract-result {:llm_refine {:model "test"}}))}
                     (fn []
                       (#'refine/refine-review-required-results! ::db opts results)))
                   (str w)))]
    (is (str/includes? output "Cerebras parallel refine starting"))
    (is (str/includes? output "Cerebras parallel refine complete"))
    (is (not (str/includes? output "Review-required post-process starting (no eligible refine)")))))

(deftest refine-review-required-results-logs-cerebras-when-force-refine-without-user
  (let [opts {:cerebras-cfg {:refine-concurrency 1 :refine-timeout-ms 1000}
              :force-refine? true}
        results [{:receipt {:id (java.util.UUID/randomUUID)}
                  :review-required? true
                  :extract-result {}}]
        output (let [w (java.io.StringWriter.)]
                 (binding [*out* w
                           *err* w]
                   (clojure.core/with-redefs-fn
                     {#'refine/receipt-eligible-for-refine?
                      (fn [_db _receipt refine-opts]
                        (true? (:force-refine? refine-opts)))
                      #'refine/maybe-refine-review-required
                      (fn [_db _receipt _extract-result persist-result _opts]
                        (assoc persist-result :extract-result {:llm_refine {:model "test"}}))}
                     (fn []
                       (#'refine/refine-review-required-results! ::db opts results)))
                   (str w)))]
    (is (str/includes? output "Cerebras parallel refine starting"))
    (is (str/includes? output "Cerebras parallel refine complete"))
    (is (not (str/includes? output "Review-required post-process starting (no eligible refine)")))))

(deftest process-receipts-by-ids-defaults-force-refine
  (let [receipt-id (java.util.UUID/randomUUID)
        captured-opts (atom nil)]
    (clojure.core/with-redefs-fn
      {#'ocr-provider/build-provider
       (fn [_app-config _context]
         {:provider :llamaparse
          :enabled? true
          :api-key "k"
          :auto-post-after-upload? false})
       #'cerebras/build-config
       (fn [_]
         {})
       #'places-api/build-config
       (fn [_]
         {})
       #'receipt-status/reset-for-ocr!
       (fn [_db _receipt-id]
         nil)
       #'receipt-queries/get-receipt
       (fn [_db _receipt-id]
         {:id receipt-id
          :status "uploaded"})
       #'runner/run-receipt!
       (fn [_db _ocr-cfg _receipt opts]
         (reset! captured-opts opts)
         {:receipt-id receipt-id
          :result :ok})}
      (fn []
        (core/process-receipts-by-ids! ::db {} [receipt-id])))
    (is (true? (:force-refine? @captured-opts)))))
