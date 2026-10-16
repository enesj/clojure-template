(ns app.domain.backend.expenses.integrations.claude.http
  "HTTP utilities and retry logic for the Anthropic Messages API.
  Never logs the API key or request bodies."
  (:require
    [app.admin.backend.services.admin.audit :as audit]
    [clj-http.client :as http]
    [clojure.string :as str]
    [taoensso.timbre :as log]))

(def ^:private anthropic-version "2023-06-01")
(def ^:private max-retry-after-seconds 60)

(defn safe-body-snippet
  "Whitespace-collapsed, length-limited copy of a response body for logs and errors."
  [s]
  (when (string? s)
    (let [s (str/replace s #"\s+" " ")]
      (if (> (count s) 1000)
        (str (subs s 0 1000) "…")
        s))))

(defn messages-url
  "POST /v1/messages URL for the configured base URL."
  [{:keys [base-url]}]
  (str (str/replace (or base-url "https://api.anthropic.com") #"/+\z" "") "/v1/messages"))

(defn- response->ex
  [{:keys [status body headers reason-phrase]}]
  (ex-info "Claude request failed"
    {:type :claude/http-error
     :status status
     :body-snippet (safe-body-snippet body)
     :request-id (get headers "request-id")
     :reason-phrase reason-phrase}))

(defn- retryable?
  [{:keys [status exception]}]
  (or (some? exception)
    (contains? #{408 409 429} status)
    (and (integer? status) (<= 500 status 599)))) ; includes 529 overloaded

(defn http-post!
  "Low-level HTTP POST. Separated for stubbing in tests."
  [url opts]
  (http/post url opts))

(defn- retry-after-ms
  "Sleep for a `retry-after` header (seconds, capped), else linear backoff."
  [resp retry-sleep-ms attempt]
  (let [ra (some-> (get-in resp [:headers "retry-after"]) str str/trim)
        secs (when (and ra (re-matches #"\d+" ra))
               (min (Long/parseLong ra) max-retry-after-seconds))]
    (if secs
      (* 1000 secs)
      (* retry-sleep-ms (inc attempt)))))

(defn- sleep! [ms]
  (when (pos? ms) (Thread/sleep (long ms))))

(defn- audit-failure!
  [{:keys [db]} url severity m]
  (when db
    (audit/log-api-failure! db
      (merge {:api-name :anthropic :operation "article-resolution"
              :request-url url :severity severity}
        m))))

(defn request-with-retries!
  "POST `request-opts` (:body, optional extra :headers) to `url` with auth headers and retries.
  Returns the 2xx response map. Throws ex-info with :type
  :claude/missing-api-key | :claude/exception | :claude/http-error."
  [{:keys [api-key workspace-id conn-timeout-ms socket-timeout-ms max-retries retry-sleep-ms] :as cfg}
   url
   request-opts]
  (when-not (seq api-key)
    (throw (ex-info "Missing Anthropic API key (set env var ANTHROPIC_API_KEY)"
             {:type :claude/missing-api-key})))
  (let [opts  (-> {:conn-timeout conn-timeout-ms
                   :socket-timeout socket-timeout-ms
                   :throw-exceptions false
                   :as :text}
                (merge (dissoc request-opts :headers))
                (assoc :headers (merge (cond-> {"x-api-key" api-key
                                                "anthropic-version" anthropic-version
                                                "content-type" "application/json"}
                                         (seq workspace-id) (assoc "anthropic-workspace-id" workspace-id))
                                  (:headers request-opts))))
        call! (fn [] (http-post! url opts))]
    (loop [attempt 0]
      (let [resp (try (call!) (catch Exception e {:exception e}))]
        (cond
          (some? (:exception resp))
          (let [e (:exception resp)]
            (if (< attempt (or max-retries 0))
              (do (log/warn "Claude request exception; retrying"
                    {:attempt (inc attempt) :max-retries max-retries
                     :exception-class (some-> e class .getName) :message (.getMessage e)})
                  (sleep! (retry-after-ms resp retry-sleep-ms attempt))
                  (recur (inc attempt)))
              (do (audit-failure! cfg url :error
                    {:error-message (or (.getMessage e) "Unknown exception")
                     :error-type (some-> e class .getName)})
                  (throw (ex-info "Claude request failed"
                           {:type :claude/exception :url url
                            :exception-class (some-> e class .getName)
                            :exception-message (.getMessage e)}
                           e)))))

          (and (integer? (:status resp)) (<= 200 (:status resp) 299))
          resp

          (and (retryable? resp) (< attempt (or max-retries 0)))
          (do (log/warn "Claude non-2xx; retrying"
                {:status (:status resp) :attempt (inc attempt) :max-retries max-retries})
              (sleep! (retry-after-ms resp retry-sleep-ms attempt))
              (recur (inc attempt)))

          :else
          (do (audit-failure! cfg url :error
                {:http-status (:status resp)
                 :error-message (or (safe-body-snippet (:body resp)) "Non-2xx response")
                 :error-type "http-error"})
              (throw (response->ex resp))))))))
