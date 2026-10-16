(ns app.domain.backend.expenses.integrations.typesafe
  "TypeSafe System One (Jev) client: POST /v1/systemone with typed questions, typed answers back.
  Network calls are stubbed in tests through `http-post!`. Never logs the API key or request bodies."
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http]
    [clojure.string :as str]
    [taoensso.timbre :as log]))

(defn build-config
  "TypeSafe config from app config + env. Env wins: TYPESAFE_API_KEY.
  App config keys (optional): {:typesafe {:api-key :base-url :model :conn-timeout-ms :socket-timeout-ms
                                           :max-retries :retry-sleep-ms}}; key also at [:secrets :typesafe :api-key]."
  ([app-config] (build-config app-config nil))
  ([app-config {:keys [getenv] :or {getenv (fn [k] (System/getenv k))}}]
   (let [cfg (or (:typesafe app-config) {})]
     {:api-key           (or (some-> (getenv "TYPESAFE_API_KEY") str/trim not-empty) (:api-key cfg)
                           (get-in app-config [:secrets :typesafe :api-key]))
      :base-url          (or (:base-url cfg) "https://api.typesafe.ai")
      :model             (or (:model cfg) "jev-latest")
      :conn-timeout-ms   (or (:conn-timeout-ms cfg) 5000)
      :socket-timeout-ms (or (:socket-timeout-ms cfg) 30000)
      :max-retries       (or (:max-retries cfg) 2)
      :retry-sleep-ms    (or (:retry-sleep-ms cfg) 500)})))

(defn http-post!
  "Low-level HTTP POST. Separated for stubbing in tests."
  [url opts]
  (http/post url opts))

(defn- retryable? [{:keys [status exception]}]
  (or (some? exception) (contains? #{408 409 429} status) (and (integer? status) (<= 500 status 599))))

(defn evaluate!
  "Evaluate `state` against `questions` ({id question}). Returns {:model :answers :usage}.
  Throws ex-info :typesafe/missing-api-key | :typesafe/http-error."
  [{:keys [api-key base-url model conn-timeout-ms socket-timeout-ms max-retries retry-sleep-ms]} state questions]
  (when (str/blank? api-key)
    (throw (ex-info "Missing TypeSafe API key (set TYPESAFE_API_KEY)" {:type :typesafe/missing-api-key})))
  (let [url  (str (str/replace base-url #"/+\z" "") "/v1/systemone")
        opts {:headers {"Authorization" (str "Bearer " api-key) "Content-Type" "application/json"}
              :body (json/generate-string {:state state :model model :questions questions})
              :conn-timeout conn-timeout-ms :socket-timeout socket-timeout-ms
              :throw-exceptions false :as :text}]
    (loop [attempt 0]
      (let [resp (try (http-post! url opts) (catch Exception e {:exception e}))]
        (cond
          (and (integer? (:status resp)) (<= 200 (:status resp) 299))
          (let [body (json/parse-string (:body resp) true)]
            {:model (:model body) :answers (:answers body) :usage (:usage body)})

          (and (retryable? resp) (< attempt max-retries))
          (do (log/warn "TypeSafe request failed; retrying" {:status (:status resp) :attempt (inc attempt)})
              (when (pos? retry-sleep-ms) (Thread/sleep (long (* retry-sleep-ms (inc attempt)))))
              (recur (inc attempt)))

          :else
          (throw (ex-info "TypeSafe request failed"
                   {:type :typesafe/http-error :status (:status resp)
                    :body-snippet (some-> (:body resp) str (subs 0 (min 300 (count (str (:body resp))))))
                    :exception-message (some-> (:exception resp) ex-message)})))))))
