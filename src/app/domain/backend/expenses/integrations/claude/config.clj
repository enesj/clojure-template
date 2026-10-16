(ns app.domain.backend.expenses.integrations.claude.config
  "Claude (Anthropic Messages API) configuration for the article resolver."
  (:require
    [clojure.string :as str]))

(def ^:private default-base-url "https://api.anthropic.com")
(def ^:private default-model "claude-opus-5-5")
(def ^:private default-effort "medium")

(defn build-config
  "Claude API config from app config + env. Env wins: ANTHROPIC_API_KEY, ANTHROPIC_MODEL, ANTHROPIC_EFFORT,
  ANTHROPIC_WORKSPACE_ID. :workspace-id is only needed for API keys that are not scoped to a workspace
  (sent as the anthropic-workspace-id header).

  App config keys (optional): {:anthropic {:api-key :base-url :model :effort :max-tokens :fallbacks?
                                            :conn-timeout-ms :socket-timeout-ms :max-retries :retry-sleep-ms}}
  The API key can also live under [:secrets :anthropic :api-key]. Never log the returned map's :api-key."
  ([app-config]
   (build-config app-config nil))
  ([app-config {:keys [getenv] :or {getenv (fn [k] (System/getenv k))}}]
   (let [cfg     (or (:anthropic app-config) {})
         getenv* (fn [k] (some-> (getenv k) str/trim not-empty))]
     {:api-key           (or (getenv* "ANTHROPIC_API_KEY") (:api-key cfg)
                           (get-in app-config [:secrets :anthropic :api-key]))
      :workspace-id      (or (getenv* "ANTHROPIC_WORKSPACE_ID") (:workspace-id cfg)
                           (get-in app-config [:secrets :anthropic :workspace-id]))
      :base-url          (or (:base-url cfg) default-base-url)
      :model             (or (getenv* "ANTHROPIC_MODEL") (:model cfg) default-model)
      :effort            (or (getenv* "ANTHROPIC_EFFORT") (:effort cfg) default-effort)
      :max-tokens        (or (:max-tokens cfg) 16000)
      :fallbacks?        (if (contains? cfg :fallbacks?) (:fallbacks? cfg) true)
      :conn-timeout-ms   (or (:conn-timeout-ms cfg) 5000)
      :socket-timeout-ms (or (:socket-timeout-ms cfg) 300000)
      :max-retries       (or (:max-retries cfg) 3)
      :retry-sleep-ms    (or (:retry-sleep-ms cfg) 1000)})))
