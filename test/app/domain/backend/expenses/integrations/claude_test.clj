(ns app.domain.backend.expenses.integrations.claude-test
  (:require
    [app.domain.backend.expenses.integrations.claude :as claude]
    [app.domain.backend.expenses.integrations.claude.config :as claude-cfg]
    [app.domain.backend.expenses.integrations.claude.http :as claude-http]
    [cheshire.core :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:private cfg
  {:api-key "test-key" :base-url "https://api.anthropic.com" :model "claude-opus-5-5"
   :effort "medium" :max-tokens 16000 :fallbacks? true
   :conn-timeout-ms 1000 :socket-timeout-ms 1000 :max-retries 3 :retry-sleep-ms 0})

(def ^:private prompt {:system "sys" :user "[]" :schema {:type "object"}})

(defn- ok-response
  ([content] (ok-response content "end_turn"))
  ([content stop-reason]
   {:status 200
    :body (json/generate-string {:id "msg_1" :model "claude-opus-5-5" :stop_reason stop-reason
                                 :content content
                                 :usage {:input_tokens 10 :output_tokens 5}})}))

(defn- capture
  "Run create-message! with a stubbed http-post!; returns {:result r :calls [[url opts] ...]}."
  [cfg* responses]
  (let [calls (atom [])
        rs    (atom responses)]
    (with-redefs [claude-http/http-post! (fn [url opts]
                                           (swap! calls conj [url opts])
                                           (let [r (first @rs)]
                                             (swap! rs rest)
                                             r))]
      {:result (claude/create-message! cfg* prompt) :calls @calls})))

(defn- body-of [[_ opts]] (json/parse-string (:body opts) true))

(deftest request-shape-test
  (let [{:keys [calls]} (capture cfg [(ok-response [{:type "text" :text "{}"}])])
        [url opts :as call] (first calls)
        body (body-of call)]
    (is (= "https://api.anthropic.com/v1/messages" url))
    (is (= "test-key" (get-in opts [:headers "x-api-key"])))
    (is (= "2023-06-01" (get-in opts [:headers "anthropic-version"])))
    (is (= {:type "ephemeral"} (get-in body [:system 0 :cache_control])))
    (is (= "medium" (get-in body [:output_config :effort])))
    (is (= "json_schema" (get-in body [:output_config :format :type])))
    (is (not-any? #(contains? body %) [:temperature :top_p :top_k :thinking :tool_choice]))))

(deftest system-blocks-each-cached-test
  (let [body (claude/build-request cfg (assoc prompt :system ["stable" "taxonomy"]))]
    (is (= ["stable" "taxonomy"] (mapv :text (:system body))))
    (is (every? #(= {:type "ephemeral"} (:cache_control %)) (:system body)))))

(deftest fallbacks-test
  (doseq [m ["claude-opus-5-5" "claude-sonnet-5-5"]]
    (let [{:keys [calls]} (capture (assoc cfg :model m) [(ok-response [{:type "text" :text "{}"}])])]
      (is (= "default" (:fallbacks (body-of (first calls)))) m)
      (is (= "server-side-fallback-2026-07-01" (get-in (first calls) [1 :headers "anthropic-beta"])) m)))
  (testing "absent for haiku and when disabled"
    (doseq [c [(assoc cfg :model "claude-haiku-5-5") (assoc cfg :fallbacks? false)]]
      (let [{:keys [calls]} (capture c [(ok-response [{:type "text" :text "{}"}])])]
        (is (not (contains? (body-of (first calls)) :fallbacks)))
        (is (nil? (get-in (first calls) [1 :headers "anthropic-beta"])))))))

(deftest text-extraction-test
  (testing "thinking block before text"
    (is (= "{\"items\":[]}"
          (:text (:result (capture cfg [(ok-response [{:type "thinking" :thinking ""}
                                                       {:type "text" :text "{\"items\":[]}"}])]))))))
  (testing "fallback block before text"
    (let [{:keys [result]} (capture cfg [(ok-response [{:type "fallback" :from {:model "a"} :to {:model "b"}}
                                                        {:type "text" :text "{\"ok\":true}"}])])]
      (is (= "{\"ok\":true}" (:text result)))
      (is (= "end_turn" (:stop-reason result)))
      (is (= 10 (get-in result [:usage :input_tokens]))))))

(deftest stop-reason-test
  (testing "refusal"
    (let [e (try (capture cfg [(ok-response [] "refusal")]) (catch clojure.lang.ExceptionInfo e e))]
      (is (= :claude/refusal (:type (ex-data e))))))
  (testing "max_tokens"
    (let [e (try (capture cfg [(ok-response [{:type "text" :text "{"}] "max_tokens")])
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (= :claude/max-tokens (:type (ex-data e)))))))

(deftest retry-test
  (testing "529 then 200 succeeds with two calls"
    (let [{:keys [result calls]} (capture cfg [{:status 529 :body "overloaded"}
                                               (ok-response [{:type "text" :text "{}"}])])]
      (is (= 2 (count calls)))
      (is (= "{}" (:text result)))))
  (testing "non-retryable 400 fails immediately"
    (let [calls (atom 0)]
      (with-redefs [claude-http/http-post! (fn [_ _] (swap! calls inc) {:status 400 :body "bad"})]
        (let [e (try (claude/create-message! cfg prompt) (catch clojure.lang.ExceptionInfo e e))]
          (is (= :claude/http-error (:type (ex-data e))))
          (is (= 400 (:status (ex-data e))))
          (is (= 1 @calls)))))))

(deftest missing-api-key-test
  (let [called (atom false)]
    (with-redefs [claude-http/http-post! (fn [_ _] (reset! called true) nil)]
      (let [e (try (claude/create-message! (assoc cfg :api-key "  ") prompt)
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (= :claude/missing-api-key (:type (ex-data e))))
        (is (false? @called))))))

(deftest build-config-test
  (let [c (claude-cfg/build-config {:anthropic {:model "claude-sonnet-5-5" :effort "low"}
                                    :secrets {:anthropic {:api-key "from-secrets"}}}
            {:getenv {"ANTHROPIC_EFFORT" "high"}} )]
    (is (= "high" (:effort c)) "env wins"))
  (let [c (claude-cfg/build-config {:anthropic {:model "claude-sonnet-5-5"}
                                    :secrets {:anthropic {:api-key "from-secrets"}}}
            {:getenv (constantly nil)})]
    (is (= "claude-sonnet-5-5" (:model c)))
    (is (= "from-secrets" (:api-key c)))
    (is (true? (:fallbacks? c)))))

(deftest workspace-id-header-test
  (testing "sent only when configured"
    (let [{:keys [calls]} (capture (assoc cfg :workspace-id "wrkspc_123") [(ok-response [{:type "text" :text "{}"}])])]
      (is (= "wrkspc_123" (get-in (first calls) [1 :headers "anthropic-workspace-id"]))))
    (let [{:keys [calls]} (capture cfg [(ok-response [{:type "text" :text "{}"}])])]
      (is (not (contains? (get-in (first calls) [1 :headers]) "anthropic-workspace-id"))))))
