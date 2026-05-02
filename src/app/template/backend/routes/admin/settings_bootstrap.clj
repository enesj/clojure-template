(ns app.template.backend.routes.admin.settings-bootstrap
  "Bootstrap runtime config rows for fresh environments.

   Reads source-controlled EDN defaults and seeds them into
   `frontend_runtime_configs` for channels that have no runtime row yet.
   Idempotent for fresh rows, with targeted additive reconciliations for
   known config migrations on existing rows."
  (:require
    [app.domain.backend.registry :as domain-registry]
    [app.template.backend.routes.admin.settings-io :as settings-io]
    [app.shared.frontend-config.io :as frontend-config-io]
    [app.shared.frontend-config.template-user :as template-user]
    [app.shared.specs.entities :as entities-spec]
    [app.shared.specs.form-fields :as form-fields-spec]
    [app.shared.specs.table-columns :as table-columns-spec]
    [app.shared.specs.view-options :as view-options-spec]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log]))

;; ---------------------------------------------------------------------------
;; Admin-owned EDN paths (same as historical settings_io.clj)
;; ---------------------------------------------------------------------------

(def ^:private admin-view-options-path "src/app/admin/frontend/config/view-options.edn")
(def ^:private admin-form-fields-path "src/app/admin/frontend/config/form-fields.edn")
(def ^:private admin-table-columns-path "src/app/admin/frontend/config/table-columns.edn")
(def ^:private admin-navigation-path "src/app/admin/frontend/config/navigation.edn")

(def ^:private validators
  {"admin" {:view-options view-options-spec/validate-view-options-strict
            :form-fields form-fields-spec/validate-form-fields-strict
        :table-columns table-columns-spec/validate-table-columns-strict
        :navigation (constantly {:valid? true})}
   "user" {:entities entities-spec/validate-user-entities
           :view-options view-options-spec/validate-view-options-strict
           :form-fields form-fields-spec/validate-form-fields-strict
       :table-columns table-columns-spec/validate-table-columns-strict
       :navigation (constantly {:valid? true})}})

(def ^:private expense-categories-entity :expense-categories)
(def ^:private expense-contexts-entity :expense-contexts)

(declare admin-defaults user-defaults)

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- deep-merge
  [& ms]
  (letfn [(merge-entry [a b]
            (merge-with
              (fn [x y]
                (if (and (map? x) (map? y))
                  (merge-entry x y)
                  y))
              (or a {})
              (or b {})))]
    (reduce merge-entry {} ms)))

(defn- insert-before
  [xs target item]
  (let [[before after] (split-with #(not= target %) xs)]
    (vec (concat before [item] after))))

(defn- merge-ordered-values
  "Preserve current order while inserting missing defaults before their next
   default neighbor when possible. This lets additive config backfills surface
   new fields/columns without clobbering user-customized ordering."
  [current defaults]
  (let [defaults (vec (or defaults []))]
    (reduce (fn [acc item]
              (if (some #(= item %) acc)
                acc
                (let [remaining-defaults (rest (drop-while #(not= item %) defaults))
                      next-existing (some (set acc) remaining-defaults)]
                  (if next-existing
                    (insert-before acc next-existing item)
                    (conj (vec acc) item)))))
      (vec (or current []))
      defaults)))

(defn- merge-missing-map-entries
  [current defaults]
  (merge (or defaults {}) (or current {})))

(defn- merge-missing-deep-map-entries
  [current defaults]
  (deep-merge (or defaults {}) (or current {})))

(defn- reconcile-entity-map-entry
  [current defaults entity-key merge-fn]
  (let [default-entity (get defaults entity-key)
        current-entity (get current entity-key)]
    (cond
      (nil? default-entity) current
      (nil? current-entity) (assoc (or current {}) entity-key default-entity)
      :else (assoc (or current {}) entity-key (merge-fn current-entity default-entity)))))

(defn- reconcile-expense-category-form-fields
  [current defaults]
  (let [default-entity (get defaults expense-categories-entity)
        current-entity (get current expense-categories-entity)]
    (cond
      (nil? default-entity) current
      (nil? current-entity) (assoc (or current {}) expense-categories-entity default-entity)
      :else
      (assoc current expense-categories-entity
        (cond-> (-> current-entity
                  (update :create-fields merge-ordered-values (:create-fields default-entity))
                  (update :edit-fields merge-ordered-values (:edit-fields default-entity))
                  (update :field-config merge-missing-map-entries (:field-config default-entity)))
          (contains? default-entity :batch-edit-fields)
          (update :batch-edit-fields merge-ordered-values (:batch-edit-fields default-entity)))))))

(defn- reconcile-expense-category-table-columns
  [current defaults]
  (let [default-entity (get defaults expense-categories-entity)
        current-entity (get current expense-categories-entity)]
    (cond
      (nil? default-entity) current
      (nil? current-entity) (assoc (or current {}) expense-categories-entity default-entity)
      :else
      (assoc current expense-categories-entity
        (-> current-entity
          (update :available-columns merge-ordered-values (:available-columns default-entity))
          (update :default-visible-columns merge-ordered-values (:default-visible-columns default-entity))
          (update :filterable-columns merge-ordered-values (:filterable-columns default-entity))
          (update :sortable-columns merge-ordered-values (:sortable-columns default-entity))
          (update :always-visible merge-ordered-values (:always-visible default-entity))
          (update :computed-fields merge-missing-map-entries (:computed-fields default-entity))
          (update :column-config merge-missing-map-entries (:column-config default-entity))
          (update :column-metadata merge-missing-map-entries (:column-metadata default-entity)))))))

(defn- reconcile-expense-context-entities
  [current defaults]
  (reconcile-entity-map-entry current defaults expense-contexts-entity merge-missing-deep-map-entries))

(defn- reconcile-expense-context-view-options
  [current defaults]
  (reconcile-entity-map-entry current defaults expense-contexts-entity merge-missing-deep-map-entries))

(defn- reconcile-expense-context-table-columns
  [current defaults]
  (reconcile-entity-map-entry current defaults expense-contexts-entity
    (fn [current-entity default-entity]
      (-> current-entity
        (update :available-columns merge-ordered-values (:available-columns default-entity))
        (update :default-visible-columns merge-ordered-values (:default-visible-columns default-entity))
        (update :filterable-columns merge-ordered-values (:filterable-columns default-entity))
        (update :sortable-columns merge-ordered-values (:sortable-columns default-entity))
        (update :always-visible merge-ordered-values (:always-visible default-entity))
        (update :computed-fields merge-missing-map-entries (:computed-fields default-entity))
        (update :column-config merge-missing-map-entries (:column-config default-entity))
        (update :column-metadata merge-missing-map-entries (:column-metadata default-entity))))))

(defn- reconcile-runtime-config!
  [db {:keys [scope config-key read-fn write-fn defaults merge-fn]}]
  (let [current (or (read-fn db) {})
        updated (merge-fn current defaults)]
    (when (not= current updated)
      (write-fn db updated)
      (log/info "Bootstrap: reconciled runtime frontend config"
        {:scope scope
         :config-key config-key
         :entities [expense-categories-entity expense-contexts-entity]}))
    updated))

(defn- reconcile-expense-category-default-config!
  [db]
  (doseq [config [{:scope "admin"
                   :config-key :form-fields
                   :read-fn settings-io/read-form-fields
                   :write-fn settings-io/write-form-fields!
                   :defaults (admin-defaults :form-fields)
                   :merge-fn reconcile-expense-category-form-fields}
                  {:scope "admin"
                   :config-key :table-columns
                   :read-fn settings-io/read-table-columns
                   :write-fn settings-io/write-table-columns!
                   :defaults (admin-defaults :table-columns)
                   :merge-fn reconcile-expense-category-table-columns}
                  {:scope "user"
                   :config-key :form-fields
                   :read-fn settings-io/read-user-form-fields
                   :write-fn settings-io/write-user-form-fields!
                   :defaults (user-defaults :form-fields)
                   :merge-fn reconcile-expense-category-form-fields}
                  {:scope "user"
                   :config-key :table-columns
                   :read-fn settings-io/read-user-table-columns
                   :write-fn settings-io/write-user-table-columns!
                   :defaults (user-defaults :table-columns)
                   :merge-fn reconcile-expense-category-table-columns}]]
    (reconcile-runtime-config! db config)))

(defn- reconcile-expense-context-default-config!
  [db]
  (doseq [config [{:scope "user"
             :config-key :entities
             :read-fn settings-io/read-user-entities
             :write-fn settings-io/write-user-entities!
             :defaults (user-defaults :entities)
             :merge-fn reconcile-expense-context-entities}
          {:scope "user"
             :config-key :view-options
             :read-fn settings-io/read-user-view-options
             :write-fn settings-io/write-user-view-options!
             :defaults (user-defaults :view-options)
             :merge-fn reconcile-expense-context-view-options}
          {:scope "user"
             :config-key :table-columns
             :read-fn settings-io/read-user-table-columns
             :write-fn settings-io/write-user-table-columns!
             :defaults (user-defaults :table-columns)
             :merge-fn reconcile-expense-context-table-columns}]]
    (reconcile-runtime-config! db config)))

(defn- read-edn-default!
  "Read a bootstrap EDN source. Invalid/unreadable/missing EDN aborts bootstrap.

   Nil paths still resolve to {} so optional domain bundles can remain absent
   without blocking startup."
  [scope config-key path]
  (if-not path
    {}
    (let [resource-candidates (->> [path
                                    (str/replace-first path #"^src/" "")
                                    (str/replace-first path #"^resources/" "")
                                    (str/replace-first path #"^config/" "")
                                    (str/replace-first path #"^vendor/" "")]
                                distinct)
          source-exists? (or (.exists (io/file path))
                           (some io/resource resource-candidates))]
      (when-not source-exists?
        (throw (ex-info "Bootstrap: missing EDN default"
                 {:scope scope
                  :config config-key
                  :path path})))
      (frontend-config-io/read-edn-or-throw
        path
        {:log-message "Bootstrap: failed to read EDN default"
         :log-context {:scope scope
                       :config config-key
                       :path path}}))))

(defn- validate-defaults!
  [scope config-key data]
  (when-let [validate-fn (get-in validators [scope config-key])]
    (let [validation (validate-fn data)]
      (when-not (:valid? validation)
        (log/error "Bootstrap: invalid frontend config defaults"
          {:scope scope
           :config-key config-key
           :errors (:errors validation)
           :warnings (:warnings validation)
           :nested-locks-errors (:nested-locks-errors validation)})
        (throw (ex-info "Bootstrap: invalid frontend config defaults"
                 {:scope scope
                  :config-key config-key
                  :errors (:errors validation)
                  :warnings (:warnings validation)
                  :nested-locks-errors (:nested-locks-errors validation)})))))
  data)

(defn- row-exists?
  "Check whether a runtime config row already exists for scope + config-key."
  [db scope config-key]
  (let [row (jdbc/execute-one!
              db
              (sql/format {:select [:%count.*]
                           :from [:frontend_runtime_configs]
                           :where [:and
                                   [:= :scope scope]
                                   [:= :config_key (name config-key)]]})
              {:builder-fn rs/as-unqualified-lower-maps})]
    (pos? (or (:count row) 0))))

(defn- seed-channel!
  "Insert a runtime config row when none exists. No-op when a row is present."
  [db scope config-key data]
  (if (row-exists? db scope config-key)
    (log/debug "Bootstrap: channel already seeded" {:scope scope :config-key config-key})
    (do
      (jdbc/execute-one!
        db
        [(str
           "INSERT INTO frontend_runtime_configs "
           "(id, scope, config_key, config_edn, created_at, updated_at) "
           "VALUES (gen_random_uuid(), ?, ?, ?, NOW(), NOW()) "
           "ON CONFLICT (scope, config_key) DO NOTHING")
         scope
         (name config-key)
         (pr-str data)])
      (log/info "Bootstrap: seeded runtime config" {:scope scope :config-key config-key}))))

;; ---------------------------------------------------------------------------
;; Default assembly
;; ---------------------------------------------------------------------------

(defn- admin-defaults
  "Merge domain + admin defaults for a given config-key."
  [config-key]
  (let [domain-paths (domain-registry/get-admin-ui-config-paths)
        domain-configs (map #(read-edn-default! "admin" config-key (get % config-key))
                         domain-paths)
        admin-path (case config-key
                     :view-options admin-view-options-path
                     :form-fields admin-form-fields-path
                     :table-columns admin-table-columns-path
                     :navigation admin-navigation-path)
        admin-config (read-edn-default! "admin" config-key admin-path)
        merged (apply deep-merge (concat domain-configs [admin-config]))]
    (validate-defaults! "admin" config-key merged)))

(defn- user-config-paths-of
  "Collect user-facing config paths for a given config-key."
  [config-key]
  (let [all-path-maps (into []
                        (remove nil?)
                        (cons template-user/paths
                          (vals (domain-registry/get-ui-config-paths))))]
    (->> all-path-maps
      (keep #(get % config-key))
      vec)))

(defn- user-defaults
  "Merge template + domain user defaults for a given config-key."
  [config-key]
  (let [paths (user-config-paths-of config-key)
        merged (apply deep-merge (map #(read-edn-default! "user" config-key %) paths))]
    (validate-defaults! "user" config-key merged)))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------

(defn bootstrap-runtime-configs!
  "Ensure all mutable frontend config channels have runtime rows.

   Reads source-controlled EDN defaults and seeds them into the DB for any
   channel that does not yet have a row. Existing rows are preserved aside from
   targeted additive backfills for known config migrations.

   Channels seeded:
     admin/view-options, admin/form-fields, admin/table-columns
     admin/navigation
     user/entities, user/view-options, user/form-fields, user/table-columns
     user/navigation

   Safe to call on every startup — idempotent.
   Bootstrap fails closed: unreadable or invalid defaults abort startup rather
   than seeding an empty runtime snapshot."
  [db]
  (try
    (log/info "Bootstrap: checking runtime frontend config channels...")

    ;; Admin channels
    (seed-channel! db "admin" :view-options (admin-defaults :view-options))
    (seed-channel! db "admin" :form-fields (admin-defaults :form-fields))
    (seed-channel! db "admin" :table-columns (admin-defaults :table-columns))
    (seed-channel! db "admin" :navigation (admin-defaults :navigation))

    ;; User channels
    (seed-channel! db "user" :entities (user-defaults :entities))
    (seed-channel! db "user" :view-options (user-defaults :view-options))
    (seed-channel! db "user" :form-fields (user-defaults :form-fields))
    (seed-channel! db "user" :table-columns (user-defaults :table-columns))
    (seed-channel! db "user" :navigation (user-defaults :navigation))

    (reconcile-expense-category-default-config! db)
    (reconcile-expense-context-default-config! db)

    (log/info "Bootstrap: runtime frontend config check complete")
    :ok
    (catch Exception e
      (log/error e "Bootstrap: failed to seed runtime frontend configs")
      (throw (ex-info "Bootstrap: failed to seed runtime frontend configs" {} e)))))
