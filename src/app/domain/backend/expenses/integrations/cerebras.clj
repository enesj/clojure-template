(ns app.domain.backend.expenses.integrations.cerebras
  "Cerebras integration (OpenAI-compatible).

  Used for post-processing receipt OCR markdown into structured data.

  Network calls must be stubbed in tests (see `http-post!`)."

  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [taoensso.timbre :as log]
    [app.domain.backend.expenses.integrations.cerebras.config :as config]
    [app.domain.backend.expenses.integrations.cerebras.http :as http]
    [app.domain.backend.expenses.integrations.cerebras.receipt-refine :as receipt-refine])
  (:import
    [java.time Instant]))

;; Re-export config
(def build-config config/build-config)

;; Re-export HTTP utilities (for test stubbing)

(defn- json-object-substring
  "Best-effort extraction of a JSON object substring from LLM output.

  Some OpenAI-compatible providers may place the final JSON into unexpected
  fields (e.g. `message.reasoning`) or wrap it with prose / code fences.

  Returns a substring containing a JSON object (from first `{` to last `}`)
  when possible, otherwise nil."
  [s]
  (let [s (some-> s str)
        trimmed (some-> s str/trim)]
    (when (seq trimmed)
      (let [start (.indexOf trimmed "{")
            end (.lastIndexOf trimmed "}")]
        (when (and (>= start 0) (> end start))
          (subs trimmed start (inc end)))))))

(defn refine-receipt-markdown!
  "Call Cerebras to refine OCR markdown into a structured receipt extraction.

  Optional opts:
  - :context (map) store/supplier fingerprint context, appended to the system message
    message in the prompt.

  Returns:
  {:provider cerebras
   :received-at <iso-string>
   :model <string>
   :response <parsed-json>
   :extraction-cents <map-with-string-keys>
   :extraction <app-shape-map-with-keyword-keys>}"
  ([cfg markdown]
   (refine-receipt-markdown! cfg markdown nil))
  ([cfg markdown {:keys [context] :as _opts}]
   (let [started (System/nanoTime)
         markdown (some-> markdown str)
         markdown (when (seq (some-> markdown str/trim)) markdown)]
     (when-not markdown
       (throw (ex-info "Missing markdown for refine" {:type :cerebras/missing-markdown})))
     (let [body (cond->
                  {:model (or (:model cfg) (config/default-model-name))
                   :messages (receipt-refine/build-chat-messages markdown context)
                   :temperature 0
                   :response_format {:type "json_schema"
                                     :json_schema {:name "ReceiptExtractionCentsV1"
                                                   :strict true
                                                   :schema receipt-refine/receipt-extraction-json-schema}}}
                  (number? (:refine-max-tokens cfg))
                  (assoc :max_tokens (long (:refine-max-tokens cfg))))
           resp (http/request-with-retries!
                  cfg
                  (http/chat-completions-url cfg)
                  {:content-type :json
                   :body (json/generate-string body)})
           resp-json (or (http/parse-json-body (:body resp))
                       (throw (http/response->ex "Cerebras returned non-JSON" resp)))
           duration-ms (/ (- (System/nanoTime) started) 1000000.0)
           finish-reason (get-in resp-json [:choices 0 :finish_reason])
           message (get-in resp-json [:choices 0 :message])
           content (or (get-in resp-json [:choices 0 :message :content])
                     (get-in resp-json [:choices 0 :text])
                     ;; Some providers (or models) may emit only `message.reasoning`.
                     ;; If it contains the JSON object, we can still parse it.
                     (get-in resp-json [:choices 0 :message :reasoning]))]
       (when-not (string? content)
         (throw (ex-info "Cerebras response missing content"
                  {:type :cerebras/missing-content
                   :status (:status resp)
                   :body-snippet (http/safe-body-snippet (:body resp))
                   :finish-reason finish-reason
                   :message-keys (when (map? message) (sort (keys message)))
                   :response resp-json})))
       (let [extraction-cents (try
                                (let [json-input (or (json-object-substring content) content)]
                                  (json/parse-string json-input false))
                                (catch Exception e
                                  (throw (ex-info "Failed to parse Cerebras JSON content"
                                           {:type :cerebras/invalid-json-content
                                            :finish-reason finish-reason
                                            :content-snippet (let [s (str/replace content #"\\s+" " ")]
                                                               (if (> (count s) 500) (str (subs s 0 500) "…") s))}
                                           e))))
             extraction (receipt-refine/llm-extraction->receipt-extraction extraction-cents)
             model (or (:model resp-json) (:model cfg) (config/default-model-name))]
         (log/info "Cerebras receipt refine complete" {:duration-ms duration-ms
                                                       :model model})
         {:provider "cerebras"
          :received-at (str (Instant/now))
          :model model
          :response resp-json
          :extraction-cents (receipt-refine/normalize-llm-extraction extraction-cents)
          :extraction extraction})))))
