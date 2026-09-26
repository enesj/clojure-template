(ns app.template.backend.routes.admin.utils
  "Shared admin route helpers (JSON/middleware); keep utilities reusable."
  (:require
    [app.shared.http :as shared-http]
    [app.shared.type-conversion :as type-conv]
    [cheshire.core :as json]
    [clojure.string :as str]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant LocalDate OffsetDateTime ZoneOffset]))

;; Response Generation Utilities

(defn json-response
  "Generate a JSON response with optional status"
  [data & {:keys [status] :or {status 200}}]
  (shared-http/json-string-response status data))

(defn error-response
  "Generate a JSON error response. Optionally include :details in the body so
  callers can surface context (e.g., suggestions) to the frontend."
  [message & {:keys [status details] :or {status 500}}]
  (if (some? details)
    (shared-http/error-string-response status message details)
    (shared-http/error-string-response status message)))

(defn success-response
  "Generate a JSON success response"
  [& [data]]
  (json-response (merge {:success true} (or data {}))))

;; Request Context Extraction

(defn extract-request-context
  "Extract common request context (IP, user agent, admin)"
  [request]
  {:ip-address (or (get-in request [:headers "x-forwarded-for"])
                 (get-in request [:headers "x-real-ip"])
                 (:remote-addr request))
   :user-agent (get-in request [:headers "user-agent"])
   :admin (:admin request)})

(defn get-admin-id
  "Extract admin ID from request"
  [request]
  (-> request :admin :id))

;; UUID Parsing & Validation

(defn parse-uuid-custom
  "Parse a string to UUID, returns nil if invalid"
  [s]
  (type-conv/try-parse-uuid s))

(defn extract-uuid-param
  "Extract and validate UUID from request parameters"
  [request param-key]
  (let [id-str (or (get-in request [:path-params param-key])
                 (get-in request [:params param-key]))]
    (parse-uuid-custom id-str)))

;; Parameter Extraction & Validation

(defn- get-param
  "Get a parameter from params map, trying both keyword and string keys."
  [params key]
  (or (get params key)
    (get params (if (keyword? key) (name key) (keyword key)))))

(defn parse-int-param
  "Parse integer parameter with default value.
   Handles both keyword and string keys in the params map."
  [params key default-val]
  (if-let [val (get-param params key)]
    (try
      (Integer/parseInt (str val))
      (catch NumberFormatException _
        default-val))
    default-val))

(defn parse-boolean-param
  "Parse boolean parameter.
   Handles both keyword and string keys in the params map."
  [params key]
  (when-let [val (get-param params key)]
    (Boolean/parseBoolean (str val))))

(defn extract-pagination-params
  "Extract pagination parameters from request"
  [params & {:keys [default-limit default-offset]
             :or {default-limit 50 default-offset 0}}]
  {:limit (parse-int-param params :limit default-limit)
   :offset (parse-int-param params :offset default-offset)})

(defn parse-instant-param
  "Lenient parse of a date/time string into java.time.Instant.
   Returns nil for nil, blank, or unparseable input."
  [raw]
  (when-not (str/blank? (str (or raw "")))
    (or (when (instance? Instant raw) raw)
      (try (Instant/parse (str raw)) (catch Exception _ nil))
      (try (-> (OffsetDateTime/parse (str raw)) .toInstant) (catch Exception _ nil))
      (try (-> (LocalDate/parse (str raw)) (.atStartOfDay ZoneOffset/UTC) .toInstant) (catch Exception _ nil)))))

(defn extract-date-range-params
  "Extract date range filter params for the given field names from a params map.
   Returns a map with non-nil parsed Instants keyed as `:<field>-from` / `:<field>-to`.

   Example:
     (extract-date-range-params params [:created-at :updated-at])
     => {:created-at-from #inst \"...\", :updated-at-to #inst \"...\"}"
  [params field-names]
  (reduce
    (fn [acc field-name]
      (let [field-str (name field-name)
            from-key (keyword (str field-str "-from"))
            to-key (keyword (str field-str "-to"))
            from-val (parse-instant-param (get-param params from-key))
            to-val (parse-instant-param (get-param params to-key))]
        (cond-> acc
          (some? from-val) (assoc from-key from-val)
          (some? to-val) (assoc to-key to-val))))
    {}
    field-names))

(defn- normalize-sort-field
  [field]
  (some-> field str str/trim not-empty (str/replace #"^:" "") (str/replace #"_" "-") keyword))

(defn- normalize-sort-direction
  [direction]
  (case (some-> direction str str/trim str/lower-case (str/replace #"^:" ""))
    "asc" :asc
    "desc" :desc
    nil))

(defn- parse-sort-entry
  [raw-entry]
  (let [[field direction] (some-> raw-entry str str/trim (str/split #":" 2))
        field* (normalize-sort-field field)
        direction* (normalize-sort-direction direction)]
    (when (and field* direction*)
      {:field field*
       :direction direction*})))

(defn extract-sort-params
  "Extract canonical sort parameters from request.

   Accepts the new `sort` query param (for example `created-at:desc,name:asc`)
   and also tolerates legacy `order-by` / `order-dir` inputs during the cutover.

   Returns:
   - :sorts     ordered sort entries
   - :order-by  first sort field (legacy convenience)
   - :order-dir first sort direction (legacy convenience)"
  [params]
  (let [sorts (or (some-> (get-param params :sort)
                    str
                    (str/split #",")
                    (->> (keep parse-sort-entry)
                      seq
                      vec))
                (let [order-by (normalize-sort-field (get-param params :order-by))
                      order-dir (normalize-sort-direction (get-param params :order-dir))]
                  (when (and order-by order-dir)
                    [{:field order-by :direction order-dir}])))
        primary-sort (first sorts)]
    (cond-> {}
      (seq sorts) (assoc :sorts sorts)
      (:field primary-sort) (assoc :order-by (:field primary-sort))
      (:direction primary-sort) (assoc :order-dir (:direction primary-sort)))))

;; Error Handling Middleware

(defn with-error-handling
  "Wrap handler with standardized error handling"
  [handler-fn error-message]
  (fn [request]
    (try
      (handler-fn request)
      (catch org.postgresql.util.PSQLException e
        (let [sql-state (.getSQLState e)]
          (if (= "23503" sql-state) ;; foreign_key_violation
            (do
              (log/warn "Cannot delete - has related records" {:message (.getMessage e)})
              (error-response "Cannot delete: record has related data. Remove related records first." :status 409))
            (do
              (log/error e error-message {:message (.getMessage e) :sql-state sql-state})
              (error-response error-message :status 500)))))
      (catch Exception e
        (let [data (ex-data e)
              status (:status data)]
          (log/error e error-message {:message (.getMessage e) :data data})
          (cond
            ;; Pass through explicit client errors (e.g. 404, 409) with their message.
            (and (integer? status) (<= 400 status 499))
            (error-response (.getMessage e) :status status)

            ;; If callers explicitly mark a server error, still avoid leaking details.
            (and (integer? status) (<= 500 status 599))
            (error-response error-message :status status)

            :else
            (error-response error-message :status 500)))))))

(defn with-validation-error-handling
  "Handle validation errors with detailed field information"
  [handler-fn error-message]
  (fn [request]
    (try
      (handler-fn request)
      (catch Exception e
        (let [data (ex-data e)]
          (cond
            (= :validation-error (:type data))
            (do
              (log/warn "Validation error:" data)
              (json-response {:error (.getMessage e)
                              :errors (:errors data)}
                :status 400))

            (= 400 (:status data))
            (do
              (log/warn "Validation error:" data)
              (json-response {:error (.getMessage e)
                              :field (:field data)
                              :allowed (:allowed data)
                              :value (:value data)}
                :status 400))

            :else
            (do
              (log/error e error-message)
              (error-response error-message :status 500))))))))

;; Common Request Patterns

(defn handle-uuid-request
  "Handle request that requires a valid UUID parameter"
  [request param-key handler-fn]
  (if-let [uuid (extract-uuid-param request param-key)]
    (handler-fn uuid request)
    (error-response (str "Invalid " (name param-key)) :status 400)))

(defn handle-uuid-body-request
  "Handle request with UUID parameter and request body"
  [request param-key handler-fn]
  (if-let [uuid (extract-uuid-param request param-key)]
    (let [body (:body request)
          context (extract-request-context request)]
      (handler-fn uuid body context request))
    (error-response (str "Invalid " (name param-key)) :status 400)))

;; Middleware Utilities

(defn wrap-json-body-parsing
  "Middleware to parse JSON body if needed"
  [handler]
  (when (nil? handler)
    (log/error "🚨 CRITICAL ERROR: wrap-json-body-parsing called with nil handler!")
    (throw (IllegalArgumentException. "Handler cannot be nil")))
  (fn [request]
    (let [content-type (get-in request [:headers "content-type"])
          body (:body request)]
      (if (and content-type
            (str/includes? content-type "application/json")
            body)
        (try
          (let [parsed-body (cond
                              ;; Body already parsed by Ring middleware
                              (map? body) body
                              ;; Raw body - need to parse
                              :else
                              (let [body-str (if (string? body) body (slurp body))]
                                (if (empty? body-str)
                                  {}
                                  (json/parse-string body-str true))))]
            (handler (assoc request :body parsed-body)))
          (catch Exception e
            (log/error e "Failed to parse JSON body")
            (error-response "Invalid JSON" :status 400)))
        (handler request)))))

;; Logging Utilities

(declare log-admin-action-with-context)

(defn log-admin-action
  "Log admin action to audit_logs table and application logs.
  Delegates to log-admin-action-with-context with nil ip-address and user-agent."
  [action admin-id entity-type entity-id details]
  (log-admin-action-with-context action admin-id entity-type entity-id details nil nil))

(defn get-client-ip
  "Extract client IP address from request, considering proxy headers"
  [request]
  (or (get-in request [:headers "x-forwarded-for"])
    (get-in request [:headers "x-real-ip"])
    (get-in request [:remote-addr])
    "unknown"))

(defn log-admin-action-with-context
  "Log admin action to audit_logs table with full request context"
  [action admin-id entity-type entity-id details ip-address user-agent]
  ;; Log to application logs for immediate debugging
  (log/info "Admin action with context:"
    {:action action
     :admin-id admin-id
     :entity-type entity-type
     :entity-id entity-id
     :details details
     :ip-address ip-address
     :user-agent user-agent})

  ;; Store in audit_logs table for compliance and persistence
  (try
    (when-let [db (requiring-resolve 'system.state/db)]
      (let [audit-service (requiring-resolve 'app.backend.services.admin/log-audit!)]
        (when (and @db audit-service)
          (audit-service @db
            {:admin_id admin-id
             :action action
             :entity-type (str entity-type)
             :entity-id (type-conv/try-parse-uuid entity-id)
             :changes details
             :ip-address ip-address
             :user-agent user-agent}))))
    (catch Exception e
      (log/warn "Failed to persist audit log to database:" (.getMessage e)))))
