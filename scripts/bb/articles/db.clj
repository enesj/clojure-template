(ns articles.db
  "Shared helpers for the `scripts/bb/articles/*` Babashka scripts.

  IMPORTANT: These helpers are intentionally `psql`-based (shelling out) instead of
  using JDBC, because Babashka does not support all of the Java interop that
  `next.jdbc` transitively requires in this repo.

  All query helpers:
  - use `psql -X` to ignore user `~/.psqlrc` (determinism)
  - enable `ON_ERROR_STOP=1` to fail fast on SQL errors
  - return data via `row_to_json` + `json_agg` to avoid delimiter parsing issues."
  (:refer-clojure :exclude [parse-long])
  (:require
    [aero.core :as aero]
    [clojure.data.json :as json]
    [clojure.java.shell :as sh]
    [clojure.pprint :as pprint]
    [clojure.string :as str])
  (:import
    [java.net URI]
    [java.text Normalizer Normalizer$Form]
    [java.util UUID]))

(defn- parse-pg-url
  "Parse a postgres:// or postgresql:// URL into a psql-compatible connection map.

  Returns {:host :port :dbname :user :password}."
  [url]
  (when (some-> url str/trim not-empty)
    (let [normalized (-> url str/trim
                       (str/replace #"^postgres://" "postgresql://"))
          uri        (URI. normalized)
          user-info  (.getUserInfo uri)
          [pg-user pg-pass] (when user-info (str/split user-info #":" 2))
          pg-port    (.getPort uri)
          pg-path    (.getPath uri)]
      {:host     (.getHost uri)
       :port     (if (pos? pg-port) pg-port 5432)
       :dbname   (str/replace (or pg-path "/railway") #"^/" "")
       :user     pg-user
       :password pg-pass})))

(defn- prod-db-config
  "Return DB connection map for :prod profile, reading DATABASE_PUBLIC_URL then DATABASE_URL."
  []
  (let [url (or (System/getenv "DATABASE_PUBLIC_URL")
              (System/getenv "DATABASE_URL"))]
    (when-not (some-> url str/trim not-empty)
      (throw (ex-info "DATABASE_PUBLIC_URL or DATABASE_URL env var is required for the prod profile"
               {:hint "railway run bb <script> prod  OR  set DATABASE_PUBLIC_URL manually"})))
    (or (parse-pg-url url)
      (throw (ex-info "Failed to parse PostgreSQL URL" {:url url})))))

(defn read-config
  "Read config for a given Aero profile keyword.

  Supports :dev, :test (reads config/base.edn) and :prod (reads DATABASE_PUBLIC_URL / DATABASE_URL)."
  [profile]
  (if (= profile :prod)
    {:database (prod-db-config)}
    (aero/read-config "config/base.edn" {:profile profile})))

(defn db-config
  "Return the `:database` map (host/port/dbname/user/password) for a profile."
  [profile]
  (:database (read-config profile)))

(defn uuid
  "Generate a random UUID string (lowercase)."
  []
  (str (UUID/randomUUID)))

(defn parse-long
  [s]
  (try
    (Long/parseLong (str s))
    (catch Exception _ nil)))

(defn parse-profile
  "Parse the first CLI arg as a profile, supporting `dev|test|prod` and `--dev|--test|--prod`.

  Returns {:profile <kw> :args <remaining-args>}.

  Defaults to `:dev` when no explicit profile is provided."
  [args]
  (let [[a & more] args]
    (cond
      (nil? a) {:profile :dev :args []}

      (#{"dev" "test" "prod"} a)
      {:profile (keyword a) :args more}

      (= a "--dev")  {:profile :dev  :args more}
      (= a "--test") {:profile :test :args more}
      (= a "--prod") {:profile :prod :args more}

      :else {:profile :dev :args args})))

(defn- strip-diacritics
  [s]
  (let [nfd (Normalizer/normalize s Normalizer$Form/NFD)]
    (str/replace nfd #"\p{InCombiningDiacriticalMarks}+" "")))

(defn normalize-key
  "Conservatively normalize a human name into a stable key.

  Rules:
  - strip diacritics (Š → S)
  - lowercase
  - replace any run of non [a-z0-9] with a single '-'
  - trim '-' from ends

  This is intended for keys like `manufacturers.normalized_key` and
  `articles.normalized_key`."
  [s]
  (when (some? s)
    (-> s
      str
      strip-diacritics
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"(^-+)|(-+$)" "")
      (str/replace #"-+" "-"))))

(defn sql-literal
  "Render a *value* as a SQL literal.

  This is a minimal escape helper for building deterministic scripts without JDBC
  parameterization.

  - nil -> NULL
  - strings -> single-quoted with ' escaped to ''
  - numbers -> emitted as-is
  - booleans -> TRUE/FALSE

  Do not use this for identifiers (table/column names)."
  [v]
  (cond
    (nil? v) "NULL"
    (true? v) "TRUE"
    (false? v) "FALSE"
    (number? v) (str v)
    (keyword? v) (sql-literal (name v))
    :else (str "'" (str/replace (str v) "'" "''") "'")))

(defn sql-in-list
  "Render a seq of values into an IN list: (v1, v2, ...)."
  [xs]
  (str "(" (str/join ", " (map sql-literal xs)) ")"))

(defn- run-psql
  [{:keys [host port dbname user password] :as db} sql]
  (when-not (and host port dbname user)
    (throw (ex-info "Invalid db config (expected :host :port :dbname :user)" {:db (dissoc db :password)})))
  (let [{:keys [out err exit]}
        (sh/sh "psql"
          "-X"
          "-v" "ON_ERROR_STOP=1"
          "-h" host
          "-p" (str port)
          "-U" user
          "-d" dbname
          "-t" "-A"
          "-c" sql
          :env (merge (into {} (System/getenv))
                 (when password {"PGPASSWORD" password})))]
    (when (not= 0 exit)
      (throw (ex-info "psql command failed" {:exit exit :err (str/trim err)})))
    out))

(defn query
  "Run SQL (a SELECT or a DML with RETURNING) and return a vector of maps.

  The SQL should NOT end with a semicolon.
  DML statements (INSERT/UPDATE/DELETE) are wrapped in a CTE;
  plain SELECTs are wrapped in a FROM subquery."
  [db sql]
  (let [dml? (boolean (re-find #"(?i)^\s*(INSERT|UPDATE|DELETE)\b" sql))
        wrapper (if dml?
                  (str
                    "WITH t AS (\n" sql "\n)\n"
                    "SELECT COALESCE(json_agg(row_to_json(t)), '[]'::json) FROM t")
                  (str
                    "SELECT COALESCE(json_agg(row_to_json(t)), '[]'::json)\n"
                    "FROM (\n" sql "\n) t"))
        out (run-psql db wrapper)
        json-str (str/trim out)]
    (when (str/blank? json-str)
      (throw (ex-info "psql returned empty output (unexpected)" {:sql (subs wrapper 0 (min 200 (count wrapper)))})))
    (vec (json/read-str json-str :key-fn keyword))))

(defn query1
  "Like `query`, but return the first row (or nil)."
  [db sql]
  (first (query db sql)))

(def not-settled-noise-sql
  "SQL condition on alias `aa`: not already settled as noise/rejected by the backend article resolver."
  (str "NOT EXISTS (SELECT 1 FROM article_alias_resolutions r WHERE r.alias_id = aa.id"
    " AND r.status::text IN ('noise', 'rejected'))"))

(defn pprint-edn
  "Pretty-print EDN to stdout."
  [x]
  (pprint/pprint x)
  (flush))

(defn prn-edn
  "Print EDN to stdout as a single line."
  [x]
  (prn x)
  (flush))
