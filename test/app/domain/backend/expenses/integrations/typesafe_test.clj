(ns app.domain.backend.expenses.integrations.typesafe-test
  (:require
    [app.domain.backend.expenses.integrations.typesafe :as typesafe]
    [cheshire.core :as json]
    [clojure.test :refer [deftest is]]))

(def ^:private cfg {:api-key "k" :base-url "https://api.typesafe.ai" :model "jev-latest"
                    :conn-timeout-ms 1 :socket-timeout-ms 1 :max-retries 2 :retry-sleep-ms 0})

(deftest evaluate-request-and-retry-test
  (let [calls (atom [])
        resps (atom [{:status 503 :body "busy"}
                     {:status 200 :body (json/generate-string {:model "jev-1.13.0"
                                                               :answers {:q {:type "noul" :noul 0.9}}
                                                               :usage {:input_tokens 5}})}])]
    (with-redefs [typesafe/http-post! (fn [url opts] (swap! calls conj [url opts])
                                        (let [r (first @resps)] (swap! resps rest) r))]
      (let [r (typesafe/evaluate! cfg {:label "x"} {:q {:type "noul" :instructions "?"}})
            [url opts] (first @calls)
            body (json/parse-string (:body opts) true)]
        (is (= 0.9 (get-in r [:answers :q :noul])))
        (is (= 2 (count @calls)))
        (is (= "https://api.typesafe.ai/v1/systemone" url))
        (is (= "Bearer k" (get-in opts [:headers "Authorization"])))
        (is (= "jev-latest" (:model body)))))))

(deftest missing-key-test
  (is (= :typesafe/missing-api-key
        (:type (ex-data (try (typesafe/evaluate! (assoc cfg :api-key nil) {} {}) (catch Exception e e)))))))
