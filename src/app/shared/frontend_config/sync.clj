(ns app.shared.frontend-config.sync
  "Sync planning for frontend config files.

  Computes patches to align config files with the DB schema,
  adding missing fields and removing unknown ones."
  (:require
    [clojure.string :as str]
    [app.shared.frontend-config.discovery :as discovery]
    [app.shared.frontend-config.schema :as schema]
    [app.shared.frontend-config.validation :as validation]))

(def ^:private table-columns-list-keys
  [:available-columns :default-visible-columns :filterable-columns :sortable-columns :always-visible])

(def ^:private form-fields-list-keys
  [:create-fields :edit-fields :batch-edit-fields :required-fields])

(defn- normalize-values
  [xs]
  (->> xs
    (map discovery/normalize-field-id)
    (remove nil?)
    set))

(defn- unknown-values
  [values allowed]
  (->> values
    (filter (fn [v]
              (let [normalized (discovery/normalize-field-id v)]
                (not (contains? allowed normalized)))))
    vec))

(defn- infer-id-style
  [values]
  (let [values (filter #(or (string? %) (keyword? %)) values)
        names (map name values)
        dash-count (count (filter #(str/includes? % "-") names))
        underscore-count (count (filter #(str/includes? % "_") names))]
    {:separator (cond
                  (and (pos? dash-count) (>= dash-count underscore-count)) "-"
                  (pos? underscore-count) "_"
                  :else "_")
     :type (cond
             (some string? values) :string
             (some keyword? values) :keyword
             :else :string)}))

(defn- format-field-id
  [raw-name {:keys [separator type]}]
  (let [formatted (if (= separator "-")
                    (str/replace raw-name "_" "-")
                    raw-name)]
    (if (= type :keyword)
      (keyword formatted)
      formatted)))

(defn- append-missing
  [existing missing style]
  (let [existing (vec existing)
        additions (map #(format-field-id % style) missing)]
    (into existing additions)))

(defn- plan-table-columns
  [data schema-index computed allowlist]
  (reduce-kv
    (fn [acc entity cfg]
      (let [entity* (discovery/normalize-entity-id entity)
            allowed (schema/allowed-fields {:schema-index schema-index
                                            :entity entity*
                                            :computed computed
                                            :allowlist allowlist
                                            :include-computed? true})]
        (if-not (schema/known-entity? schema-index allowlist entity*)
          (update acc :unknown-entities conj entity)
          (let [style (infer-id-style (mapcat #(get cfg % []) table-columns-list-keys))
                removed (into {}
                          (for [k table-columns-list-keys
                                :let [vals (get cfg k)
                                      unknown* (when (seq vals)
                                                 (unknown-values vals allowed))]
                                :when (seq unknown*)]
                            [k unknown*]))
                removed-config (let [vals (keys (:column-config cfg))
                                     unknown* (when (seq vals)
                                                (unknown-values vals allowed))]
                                 (when (seq unknown*)
                                   {:column-config unknown*}))
                removed (merge removed removed-config)
                filtered (into {}
                           (for [k table-columns-list-keys
                                 :when (contains? cfg k)
                                 :let [vals (get cfg k)
                                       keep* (->> (or vals [])
                                               (remove (set (get removed k)))
                                               vec)]]
                             [k keep*]))
                available-present? (contains? cfg :available-columns)
                available (get cfg :available-columns [])
                available-normalized (normalize-values available)
                db-order (get-in schema-index [:entity->fields entity* :raw] [])
                missing (->> db-order
                          (remove (fn [f]
                                    (or (contains? available-normalized (discovery/normalize-field-id f))
                                      (schema/internal-field? f))))
                          vec)
                available* (when (or (seq missing) available-present?)
                             (append-missing (get filtered :available-columns available)
                               missing
                               style))
                column-config (:column-config cfg)
                column-config* (when (map? column-config)
                                 (apply dissoc column-config (get removed :column-config)))
                updates (cond-> {}
                          (and (some? available*)
                            (not= available available*))
                          (assoc :available-columns available*)

                          (and (contains? filtered :default-visible-columns)
                            (not= (:default-visible-columns cfg)
                              (:default-visible-columns filtered)))
                          (assoc :default-visible-columns (:default-visible-columns filtered))

                          (and (contains? filtered :filterable-columns)
                            (not= (:filterable-columns cfg)
                              (:filterable-columns filtered)))
                          (assoc :filterable-columns (:filterable-columns filtered))

                          (and (contains? filtered :sortable-columns)
                            (not= (:sortable-columns cfg)
                              (:sortable-columns filtered)))
                          (assoc :sortable-columns (:sortable-columns filtered))

                          (and (contains? filtered :always-visible)
                            (not= (:always-visible cfg)
                              (:always-visible filtered)))
                          (assoc :always-visible (:always-visible filtered))

                          (and (map? column-config)
                            (not= column-config column-config*))
                          (assoc :column-config column-config*)

                          (seq removed)
                          (assoc :removed-fields
                            (merge (:removed-fields cfg) removed)))
                summary (cond-> {}
                          (seq missing) (assoc :add-available missing)
                          (seq removed) (assoc :removed removed))]
            (cond-> acc
              (seq updates) (update :updates assoc entity updates)
              (seq summary) (update :summary assoc entity summary))))))
    {:updates {}
     :summary {}
     :unknown-entities []}
    data))

(defn- plan-form-fields
  [data schema-index allowlist]
  (reduce-kv
    (fn [acc entity cfg]
      (let [entity* (discovery/normalize-entity-id entity)
            allowed (schema/allowed-fields {:schema-index schema-index
                                            :entity entity*
                                            :computed {}
                                            :allowlist allowlist
                                            :include-computed? false})]
        (if-not (schema/known-entity? schema-index allowlist entity*)
          (update acc :unknown-entities conj entity)
          (let [removed (into {}
                          (for [k form-fields-list-keys
                                :let [vals (get cfg k)
                                      unknown* (when (seq vals)
                                                 (unknown-values vals allowed))]
                                :when (seq unknown*)]
                            [k unknown*]))
                removed-config (let [vals (keys (:field-config cfg))
                                     unknown* (when (seq vals)
                                                (unknown-values vals allowed))]
                                 (when (seq unknown*)
                                   {:field-config unknown*}))
                removed (merge removed removed-config)
                filtered (into {}
                           (for [k form-fields-list-keys
                                 :when (contains? cfg k)
                                 :let [vals (get cfg k)
                                       keep* (->> (or vals [])
                                               (remove (set (get removed k)))
                                               vec)]]
                             [k keep*]))
                field-config (:field-config cfg)
                field-config* (when (map? field-config)
                                (apply dissoc field-config (get removed :field-config)))
                updates (cond-> {}
                          (and (contains? filtered :create-fields)
                            (not= (:create-fields cfg)
                              (:create-fields filtered)))
                          (assoc :create-fields (:create-fields filtered))

                          (and (contains? filtered :edit-fields)
                            (not= (:edit-fields cfg)
                              (:edit-fields filtered)))
                          (assoc :edit-fields (:edit-fields filtered))

                          (and (contains? filtered :required-fields)
                            (not= (:required-fields cfg)
                              (:required-fields filtered)))
                          (assoc :required-fields (:required-fields filtered))

                          (and (map? field-config)
                            (not= field-config field-config*))
                          (assoc :field-config field-config*)

                          (seq removed)
                          (assoc :removed-fields
                            (merge (:removed-fields cfg) removed)))
                summary (when (seq removed)
                          {:removed removed})]
            (cond-> acc
              (seq updates) (update :updates assoc entity updates)
              (seq summary) (update :summary assoc entity summary))))))
    {:updates {}
     :summary {}
     :unknown-entities []}
    data))

(defn- plan-view-options
  [data schema-index computed allowlist]
  (reduce-kv
    (fn [acc entity cfg]
      (let [entity* (discovery/normalize-entity-id entity)
            allowed (schema/allowed-fields {:schema-index schema-index
                                            :entity entity*
                                            :computed computed
                                            :allowlist allowlist
                                            :include-computed? true})]
        (if-not (schema/known-entity? schema-index allowlist entity*)
          (update acc :unknown-entities conj entity)
          (let [unknown-defaults (unknown-values (keys (:column-defaults cfg)) allowed)
                unknown-locks (unknown-values (keys (:column-locks cfg)) allowed)
                removed (cond-> {}
                          (seq unknown-defaults) (assoc :column-defaults unknown-defaults)
                          (seq unknown-locks) (assoc :column-locks unknown-locks))
                column-defaults (:column-defaults cfg)
                column-locks (:column-locks cfg)
                updates (cond-> {}
                          (and (map? column-defaults) (seq unknown-defaults))
                          (assoc :column-defaults (apply dissoc column-defaults unknown-defaults))

                          (and (map? column-locks) (seq unknown-locks))
                          (assoc :column-locks (apply dissoc column-locks unknown-locks))

                          (seq removed)
                          (assoc :removed-fields
                            (merge (:removed-fields cfg) removed)))
                summary (when (seq removed)
                          {:removed removed})]
            (cond-> acc
              (seq updates) (update :updates assoc entity updates)
              (seq summary) (update :summary assoc entity summary))))))
    {:updates {}
     :summary {}
     :unknown-entities []}
    data))

(defn- plan-entities
  [data schema-index allowlist]
  (let [removed-entities (->> (keys data)
                           (remove #(schema/known-entity? schema-index allowlist %))
                           vec)
        summary (into {}
                  (map (fn [entity]
                         [entity {:remove-entity? true}])
                    removed-entities))]
    {:updates {}
     :summary summary
     :remove-entities removed-entities
     :unknown-entities []}))

(defn plan-sync
  "Compute sync patches per bundle. Returns a vector of file patches."
  [bundles schema-index allowlist]
  (let [allowlist (schema/normalize-allowlist allowlist)]
    (mapcat
      (fn [bundle]
        (let [data (:data bundle)
              computed (validation/computed-fields-by-entity (:table-columns data))]
          (for [[kind value] data
                :let [plan (case kind
                             :entities (plan-entities value schema-index allowlist)
                             :table-columns (plan-table-columns value schema-index computed allowlist)
                             :form-fields (plan-form-fields value schema-index allowlist)
                             :view-options (plan-view-options value schema-index computed allowlist)
                             {:updates {} :summary {} :unknown-entities []})
                      has-changes? (boolean
                                     (or (seq (:updates plan))
                                       (seq (:remove-entities plan))
                                       (seq (:unknown-entities plan))))]]
            (merge bundle
              {:kind kind
               :path (get-in bundle [:paths kind])
               :updates (:updates plan)
               :summary (:summary plan)
               :remove-entities (:remove-entities plan)
               :unknown-entities (:unknown-entities plan)
               :has-changes? has-changes?}))))
      bundles)))
