(ns app.domain.backend.expenses.integrations.claude
  "Claude (Anthropic Messages API) integration for the article resolver.
  Raw HTTP on purpose: Clojure has no official SDK. Network calls are stubbed in
  tests through `claude.http/http-post!`.

  Never sent: temperature, top_p, top_k, thinking, budget_tokens, tool_choice, assistant prefill.
  The model always thinks adaptively; `output_config.effort` is the only control."
  (:require
    [app.domain.backend.expenses.integrations.claude.config :as config]
    [app.domain.backend.expenses.integrations.claude.http :as http]
    [cheshire.core :as json]
    [clojure.string :as str]))

;; Re-export config
(def build-config config/build-config)

(def ^:private fallback-models #{"claude-opus-5-5" "claude-sonnet-5-5"})
(def ^:private fallback-beta "server-side-fallback-2026-07-01")

(defn build-request
  "Pure: request body map for POST /v1/messages. `system` is a string or a vector of strings;
  every block gets a cache breakpoint (stable blocks first, so a change to a later block keeps
  the earlier ones cached). At most 4 breakpoints are allowed."
  [{:keys [model effort max-tokens fallbacks?]} {:keys [system user schema]}]
  (cond-> {:model model
           :max_tokens max-tokens
           :system (mapv (fn [text] {:type "text" :text text :cache_control {:type "ephemeral"}})
                     (if (string? system) [system] system))
           :messages [{:role "user" :content user}]
           :output_config {:effort effort
                           :format {:type "json_schema" :schema schema}}}
    (and fallbacks? (fallback-models model)) (assoc :fallbacks "default")))

(defn- response-text
  "Concatenate the text of all `text` blocks. Never `(first content)`: the first block is
  usually `thinking`, and `fallback` blocks can appear."
  [content]
  (->> content
    (filter #(= "text" (:type %)))
    (map :text)
    (apply str)))

(defn create-message!
  "Send one request. Returns {:id :model :stop-reason :text :usage :duration-ms}.
  :model may differ from the requested model when a server-side fallback served.
  Throws ex-info with :type :claude/missing-api-key | :claude/refusal | :claude/max-tokens |
  :claude/http-error | :claude/exception | :claude/bad-response."
  [cfg prompt]
  (when (str/blank? (:api-key cfg))
    (throw (ex-info "Missing Anthropic API key (set env var ANTHROPIC_API_KEY)"
             {:type :claude/missing-api-key})))
  (let [body    (build-request cfg prompt)
        started (System/nanoTime)
        resp    (http/request-with-retries!
                  cfg (http/messages-url cfg)
                  {:body (json/generate-string body)
                   :headers (when (:fallbacks body) {"anthropic-beta" fallback-beta})})
        ms      (long (/ (- (System/nanoTime) started) 1e6))
        parsed  (try (json/parse-string (:body resp) true)
                     (catch Exception _ nil))]
    (when-not (map? parsed)
      (throw (ex-info "Claude returned a non-JSON body"
               {:type :claude/bad-response :body-snippet (http/safe-body-snippet (:body resp))})))
    ;; Check stop_reason BEFORE reading content.
    (case (:stop_reason parsed)
      "refusal"    (throw (ex-info "Claude refused the request"
                            {:type :claude/refusal :stop-details (:stop_details parsed)
                             :id (:id parsed)}))
      "max_tokens" (throw (ex-info "Claude hit max_tokens"
                            {:type :claude/max-tokens :usage (:usage parsed) :id (:id parsed)}))
      nil)
    {:id (:id parsed)
     :model (:model parsed)
     :stop-reason (:stop_reason parsed)
     :text (response-text (:content parsed))
     :usage (:usage parsed)
     :duration-ms ms}))

(comment
  (require '[app.domain.backend.expenses.integrations.claude.config :as claude-cfg])
  (def cfg (claude-cfg/build-config (:config @system.state/state)))
  (select-keys (create-message! cfg {:system (apply str (repeat 300 "Reply with JSON. "))
                                     :user "Say ok"
                                     :schema {:type "object" :additionalProperties false
                                              :required ["ok"] :properties {:ok {:type "boolean"}}}})
    [:model :stop-reason :text :usage])
  ;; run twice: the second call should show (pos? (get-in r [:usage :cache_read_input_tokens]))
  :rcf)
