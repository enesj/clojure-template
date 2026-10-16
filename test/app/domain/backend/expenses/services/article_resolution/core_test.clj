(ns app.domain.backend.expenses.services.article-resolution.core-test
  "Offline tests: Claude, queue and catalog are stubbed."
  (:require
    [app.domain.backend.expenses.integrations.claude :as claude]
    [app.domain.backend.expenses.services.article-resolution.catalog :as catalog]
    [app.domain.backend.expenses.services.article-resolution.core :as core]
    [app.domain.backend.expenses.services.article-resolution.queue :as queue]
    [cheshire.core :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:private cat-id (random-uuid))

(def ^:private index
  {"Pakovana hrana i pića" {:id cat-id :subcategories [{:id (random-uuid) :name "Bezalkoholna pića"}]}})

(defn- app-config
  [mode]
  {:article-resolver {:apply-mode mode :batch-size 10 :max-attempts 3}
   :anthropic {:api-key "k"}})

(defn- row
  [label attempts]
  {:resolution_id (random-uuid) :attempts attempts :alias_id (random-uuid)
   :raw_label label :raw_label_normalized (clojure.string/lower-case label) :unit "kom"
   :alias_article_id nil :supplier "BINGO"})

(defn- good-item
  [id]
  {:id id :kind "article" :canonical_name (str "Proizvod " id) :category "Pakovana hrana i pića"
   :subcategory "Bezalkoholna pića" :manufacturer nil :existing_article_key nil
   :confidence 95 :reason "ok"})

(defn- response
  [items]
  {:id "msg_1" :model "claude-opus-5-5" :stop-reason "end_turn"
   :text (json/generate-string {:items items}) :usage {:input_tokens 1 :output_tokens 1} :duration-ms 5})

(defn- request-ids
  [prompt]
  (map #(get % "id") (json/parse-string (:user prompt))))

(defn- run
  "Run a sweep with everything stubbed. `claude-fn` receives the prompt. Returns
  {:summary :finalized [[ids opts]...] :claude-calls [n-groups ...] :applied n}."
  [mode rows claude-fn]
  (let [finalized (atom []) calls (atom []) applied (atom 0)]
    (with-redefs [queue/reclaim-stale!    (fn [& _] 0)
                  queue/enqueue-recent!   (fn [& _] 0)
                  queue/claim!            (fn [& _] (mapv :resolution_id rows))
                  queue/load-claimed      (fn [& _] rows)
                  queue/finalize!         (fn [_ ids o] (swap! finalized conj [ids o]))
                  queue/release-processing! (fn [& _] 0)
                  queue/record-call!      (fn [& _] (random-uuid))
                  catalog/category-index  (fn [& _] index)
                  catalog/article-candidates (fn [& _] [])
                  catalog/sibling-article-id (fn [& _] nil)
                  catalog/find-article    (fn [& _] nil)
                  core/apply-decision!    (fn [& _] (swap! applied inc) {:article-id (random-uuid)})
                  claude/create-message!  (fn [_ prompt]
                                            (swap! calls conj (count (request-ids prompt)))
                                            (claude-fn prompt))]
      (let [summary (core/run-sweep! :db (app-config mode))]
        {:summary summary :finalized @finalized :claude-calls @calls :applied @applied}))))

(defn- statuses [{:keys [finalized]}] (mapv (comp :status second) finalized))

(deftest max-tokens-splits-chunk-test
  (let [rows (mapv #(row (str "Proizvod " %) 1) (range 4))
        r    (run :dry-run rows
               (fn [prompt]
                 (let [ids (request-ids prompt)]
                   (if (= 4 (count ids))
                     (throw (ex-info "max" {:type :claude/max-tokens}))
                     (response (mapv good-item ids))))))]
    (is (= [4 2 2] (:claude-calls r)))
    (is (= 3 (get-in r [:summary :calls])))
    (is (= 4 (get-in r [:summary :needs-review])))
    (is (every? #{:needs_review} (statuses r)))))

(deftest missing-ids-resent-once-test
  (testing "partial response: missing groups are re-sent in a follow-up call"
    (let [r (run :dry-run [(row "Proizvod a" 1) (row "Proizvod b" 1) (row "Proizvod c" 1)]
              (fn [prompt] (response (if (= 3 (count (request-ids prompt)))
                                       [(good-item "g1")]
                                       (mapv good-item (request-ids prompt))))))]
      (is (= [3 2] (:claude-calls r)))
      (is (= 2 (get-in r [:summary :resent])))
      (is (= [:needs_review :needs_review :needs_review] (statuses r)))))
  (testing "the follow-up is not re-sent again: still missing -> back to pending"
    (let [r (run :dry-run [(row "Proizvod a" 1) (row "Proizvod b" 1)]
              (fn [prompt] (response (if (= 2 (count (request-ids prompt))) [(good-item "g1")] []))))]
      (is (= [2 1] (:claude-calls r)))
      (is (= #{:needs_review :pending} (set (statuses r))))
      (is (= 1 (get-in r [:summary :retried]))))))

(deftest missing-ids-retry-or-fail-test
  (testing "empty response is not re-sent (nothing parsed): back to pending"
    (let [r (run :dry-run [(row "Proizvod a" 1) (row "Proizvod b" 1)]
              (fn [_] (response [])))]
      (is (= [2] (:claude-calls r)))
      (is (= [:pending :pending] (statuses r)))))
  (testing "attempts exhausted: failed"
    (let [r (run :dry-run [(row "Proizvod a" 3)] (fn [_] (response [])))]
      (is (= [:failed] (statuses r)))
      (is (= 1 (get-in r [:summary :failed]))))))

(deftest settle-window-test
  (let [claimed (atom 0)
        sweep   (fn [ready opts]
                  (with-redefs [queue/reclaim-stale!  (fn [& _] 0)
                                queue/enqueue-recent! (fn [& _] 0)
                                queue/ready?          (fn [& _] ready)
                                queue/claim!          (fn [& _] (swap! claimed inc) [])]
                    (core/run-sweep! :db (assoc-in (app-config :auto) [:article-resolver :settle-minutes] 15) opts)))]
    (testing "not settled: nothing claimed"
      (is (true? (:settling (sweep false nil))))
      (is (zero? @claimed)))
    (testing "settled or forced: claims"
      (sweep true nil)
      (sweep false {:force? true})
      (is (= 2 @claimed)))))

(deftest refusal-retry-or-fail-test
  (let [r (run :dry-run [(row "Proizvod a" 1)]
            (fn [_] (throw (ex-info "refused" {:type :claude/refusal}))))]
    (is (= [:pending] (statuses r)))
    (is (re-find #"refused" (:error (second (first (:finalized r))))))))

(deftest unparseable-response-test
  (let [r (run :dry-run [(row "Proizvod a" 1)] (fn [_] (assoc (response []) :text "not json")))]
    (is (= [:pending] (statuses r)))))

(deftest dry-run-never-applies-test
  (let [rows (mapv #(row (str "Proizvod " %) 1) (range 3))
        r    (run :dry-run rows (fn [prompt] (response (mapv good-item (request-ids prompt)))))]
    (is (zero? (:applied r)))
    (is (every? #{:needs_review} (statuses r)))
    (is (every? #(true? (get-in (second %) [:proposal :auto_applicable])) (:finalized r)))))

(deftest auto-mode-applies-clean-groups-test
  (let [rows [(row "Proizvod a" 1) (row "Proizvod b" 1)]
        r    (run :auto rows
               (fn [prompt]
                 (response [(good-item "g1") (assoc (good-item "g2") :confidence 50)])))]
    (is (= 1 (:applied r)) "only the confident group is applied")
    (is (= [:needs_review] (statuses r)) "the low-confidence group goes to review (applied rows finalize inside apply-decision!)")
    (is (= "low-confidence" (:decision-reason (second (first (:finalized r))))))))

(deftest noise-never_calls_claude-test
  (let [r (run :auto [(row "----" 1)] (fn [_] (throw (ex-info "must not be called" {}))))]
    (is (empty? (:claude-calls r)))
    (is (= [:noise] (statuses r)))
    (is (= "noise" (:source (second (first (:finalized r)))))))
  (testing "dry-run stores the noise proposal for review"
    (let [r (run :dry-run [(row "----" 1)] (fn [_] (throw (ex-info "must not be called" {}))))]
      (is (empty? (:claude-calls r)))
      (is (= [:needs_review] (statuses r))))))

(deftest credentials-error-gives-attempt-back-test
  (let [released (atom [])
        rows     (mapv #(row (str "Proizvod " %) 1) (range 3))
        calls    (atom 0)]
    (with-redefs [queue/release-processing! (fn [_ ids error & [o]] (swap! released conj [ids error o]) 0)]
      (let [r (run :dry-run rows
                (fn [_] (swap! calls inc)
                  (throw (ex-info "Claude request failed" {:type :claude/http-error :status 401
                                                           :body-snippet "invalid x-api-key"}))))]
        (is (true? (get-in r [:summary :config-error])))
        (is (empty? (:finalized r)) "rows are released, not failed or retried through finalize!")
        (is (= 3 (get-in r [:summary :retried])))
        (is (= 1 @calls))
        (is (every? #(true? (:restore-attempt? (nth % 2))) (take 3 @released)))))))

(deftest workspace-400-is-a-credentials-error-test
  (let [r (run :dry-run [(row "Proizvod a" 1)]
            (fn [_] (throw (ex-info "x" {:type :claude/http-error :status 400
                                         :body-snippet "This API key is not scoped to a workspace"}))))]
    (is (true? (get-in r [:summary :config-error]))))
  (testing "other 400s are the rows' problem"
    (let [r (run :dry-run [(row "Proizvod a" 1)]
              (fn [_] (throw (ex-info "x" {:type :claude/http-error :status 400 :body-snippet "bad schema"}))))]
      (is (nil? (get-in r [:summary :config-error])))
      (is (= [:pending] (statuses r))))))

(deftest verifier-fills-null-category-test
  (let [captured (atom nil)
        cat-name "Pakovana hrana i pića"]
    (with-redefs [app.domain.backend.expenses.integrations.typesafe/evaluate!
                  (fn [_ _ _] {:answers {:category {:choice cat-name :confidence 0.95}
                                         :same-product {:noul 0.9}}})
                  queue/finalize! (fn [_ _ o] (reset! captured o))]
      (let [rows [(row "Proizvod a" 1)]
            app-config (-> (app-config :dry-run)
                         (assoc-in [:article-resolver :verifier :enabled?] true)
                         (assoc :typesafe {:api-key "k"}))]
        (with-redefs [queue/reclaim-stale! (fn [& _] 0) queue/enqueue-recent! (fn [& _] 0)
                      queue/claim! (fn [& _] (mapv :resolution_id rows)) queue/load-claimed (fn [& _] rows)
                      queue/release-processing! (fn [& _] 0) queue/record-call! (fn [& _] (random-uuid))
                      catalog/category-index (fn [& _] index) catalog/article-candidates (fn [& _] [])
                      catalog/sibling-article-id (fn [& _] nil) catalog/find-article (fn [& _] nil)
                      claude/create-message! (fn [_ _] (response [(assoc (good-item "g1") :category nil)]))]
          (core/run-sweep! :db app-config))))
    (is (= cat-name (get-in @captured [:proposal :category])))
    (is (true? (get-in @captured [:proposal :auto_applicable])))
    (is (true? (get-in @captured [:proposal :verifier :category_filled])))))
