(ns app.domain.backend.expenses.services.service-configs.config-maps
  "Entity configuration maps for expenses services."
  (:require
    [app.domain.backend.expenses.services.articles :as articles]
    [app.domain.backend.expenses.services.service-configs.normalization :as normalize]

    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs])
  (:import
    [java.util UUID]))

(defn- normalize-unit-value
  [value]
  (some-> value str str/trim str/lower-case not-empty))

(def ^:private min-article-alias-normalized-length 2)

(defn- ensure-valid-article-alias-data
  [data]
  (let [raw-label-provided? (contains? data :raw_label)
        raw-label-normalized-provided? (contains? data :raw_label_normalized)
        raw-label (some-> (:raw_label data) str str/trim)
        normalized (cond
                     raw-label-provided? (articles/normalize-alias-label raw-label)
                     raw-label-normalized-provided? (articles/normalize-alias-label (:raw_label_normalized data))
                     :else nil)]
    (when (and (or raw-label-provided? raw-label-normalized-provided?)
            (or (str/blank? normalized)
              (< (count normalized) min-article-alias-normalized-length)))
      (throw (ex-info "raw_label normalizes to an invalid key"
               {:status 400
                :entity "article_aliases"
                :field :raw_label
                :raw_label raw-label
                :raw_label_normalized normalized})))
    (cond-> data
      raw-label-provided? (assoc :raw_label raw-label)
      (or raw-label-provided? raw-label-normalized-provided?) (assoc :raw_label_normalized normalized)
      (contains? data :unit) (update :unit normalize-unit-value))))

(def article-alias-config
  {:table-name "article_aliases"
   :table-alias :aa
   :primary-key :aa/id
   :required-fields [:supplier_id :raw_label :raw_label_normalized :unit]
   :allowed-order-by {:created-at :aa/created_at
                      :raw-label :aa/raw_label
                      :raw-label-normalized :aa/raw_label_normalized
                      :unit :aa/unit
                      :supplier-display-name :s/display_name
                      :article-canonical-name :a/canonical_name}
   :default-order-by :aa/created_at
   :search-fields [:aa/raw_label :aa/raw_label_normalized :aa/unit :s/display_name :a/canonical_name]
   :text-filter-columns {:supplier-display-name :s.display_name
                         :article-canonical-name :a.canonical_name
                         :raw-label :aa.raw_label
                         :raw-label-normalized :aa.raw_label_normalized
                         :unit :aa.unit}
   :joins [[:suppliers :s] [:= :s/id :aa/supplier_id]
           [:articles :a] [:= :a/id :aa/article_id]]
   :select-fields [[:aa.*]
                   [:s/display_name :supplier_display_name]
                   [:a/canonical_name :article_canonical_name]]
   :field-transformers {:raw_label_normalized articles/normalize-alias-label
                        :unit normalize-unit-value}
   :before-insert (fn [data]
                    (ensure-valid-article-alias-data data))
   :before-update (fn [_id updates]
                    (ensure-valid-article-alias-data updates))
   :has-search? true
   :has-count? true})

(def supplier-alias-config
  {:table-name "supplier_aliases"
   :table-alias :sa
   :primary-key :sa/id
   :required-fields [:raw_label :raw_label_normalized]
   :allowed-order-by {:created-at :sa/created_at
                      :updated-at :sa/updated_at
                      :raw-label :sa/raw_label
                      :raw-label-normalized :sa/raw_label_normalized
                      :supplier-display-name :s/display_name
                      :confidence :sa/confidence}
   :default-order-by :sa/created_at
   :search-fields [:sa/raw_label :sa/raw_label_normalized :s/display_name]
   :text-filter-columns {:supplier-display-name :s.display_name
                         :raw-label-normalized  :sa.raw_label_normalized}
   :joins [[:suppliers :s] [:= :s/id :sa/supplier_id]]
   :select-fields [[:sa.*]
                   [:s/display_name :supplier_display_name]]
   :field-transformers {:raw_label_normalized normalize/normalize-supplier-key}
   :has-search? true
   :has-count? true})

(def store-alias-config
  {:table-name "store_aliases"
   :table-alias :sta
   :primary-key :sta/id
   :required-fields [:raw_label :raw_label_normalized]
   :allowed-order-by {:created-at :sta/created_at
                      :updated-at :sta/updated_at
                      :raw-label :sta/raw_label
                      :raw-label-normalized :sta/raw_label_normalized
                      :store-display-name :st/display_name
                      :supplier-display-name :s/display_name
                      :confidence :sta/confidence}
   :default-order-by :sta/created_at
   :search-fields [:sta/raw_label :sta/raw_label_normalized :st/display_name :st/address :s/display_name]
   :text-filter-columns {:supplier-display-name :s.display_name
                         :store-display-name :st.display_name
                         :store-address :st.address
                         :raw-label :sta.raw_label
                         :raw-label-normalized :sta.raw_label_normalized}
   :numeric-filter-columns {:confidence :sta.confidence}
   :joins [[:stores :st] [:= :st/id :sta/store_id]
           [:suppliers :s] [:= :s/id :st/supplier_id]]
   :select-fields [[:sta.*]
                   [:st/display_name :store_display_name]
                   [:st/address :store_address]
                   [:st/supplier_id :supplier_id]
                   [:s/display_name :supplier_display_name]]
   :field-transformers {:raw_label_normalized normalize/normalize-store-key}
   :has-search? true
   :has-count? true})

(def supplier-config
  {:table-name "suppliers"
   :primary-key :id
   :required-fields [:display_name]
   :allowed-order-by {:display-name :display_name
                      :normalized-key :normalized_key
                      :address :address
                      :created-at :created_at
                      :updated-at :updated_at
                      :store-count :store_count}
   :default-order-by :display_name
   :search-fields [:display_name :normalized_key]
   :text-filter-columns {:normalized-key :normalized_key}
   ;; Include store_count so the supplier list page can show/hide the expand chevron
   ;; without a separate prefetch. The correlated subquery is evaluated per-row.
   :select-fields [[:*]
                   [{:select [[[:count :*]]]
                     :from [:stores]
                     :where [:= :stores/supplier_id :suppliers/id]}
                    :store_count]]
   :field-transformers {:normalized_key normalize/normalize-supplier-key}
   :before-insert (fn [data]
                    (let [display-name (normalize/unescape-html-entities (:display_name data))]
                      (assoc data
                        :display_name display-name
                        :normalized_key (normalize/normalize-supplier-key display-name)
                        :id (UUID/randomUUID))))
   :before-update (fn [_id updates]
                    (if (:display_name updates)
                      (let [display-name (normalize/unescape-html-entities (:display_name updates))]
                        (assoc updates
                          :display_name display-name
                          :normalized_key (normalize/normalize-supplier-key display-name)))
                      updates))
   :has-search? true
   :has-count? true})

(def store-config
  {:table-name "stores"
   :table-alias :st
   :primary-key :id
   :required-fields [:supplier_id :display_name]
   :allowed-order-by {:display-name :st/display_name
                      :supplier-id :s/display_name
                      :supplier-display-name :s/display_name
                      :normalized-key :st/normalized_key
                      :address :st/address
                      :city-id :st/city_id
                      :city-name :c/name
                      :created-at :st/created_at
                      :updated-at :st/updated_at}
   :default-order-by :st/display_name
   :search-fields [:st/display_name :st/normalized_key :st/address :c/name :s/display_name]
   :text-filter-columns {:supplier-display-name :s.display_name
                         :normalized-key        :st.normalized_key
                         :address               :st.address
                         :city-name             :c.name}
   :joins [[:cities :c] [:= :c.id :st/city_id]
           [:suppliers :s] [:= :s/id :st/supplier_id]]
   :select-fields [[:st.*]
                   [:c/name :city_name]
                   [:s/display_name :supplier_display_name]]
   :field-transformers {:normalized_key normalize/normalize-store-key}
   :before-insert (fn [data]
                    (let [display-name (normalize/unescape-html-entities (:display_name data))
                          address (normalize/unescape-html-entities (:address data))
                          key-src (str (or display-name "") " " (or address ""))]
                      (-> data
                        (assoc :display_name display-name)
                        (assoc :address address)
                        (assoc :normalized_key (normalize/normalize-store-key key-src))
                        (assoc :id (UUID/randomUUID)))))
   :before-update (fn [_id updates]
                    (let [display-name (when (contains? updates :display_name)
                                         (normalize/unescape-html-entities (:display_name updates)))
                          address (when (contains? updates :address)
                                    (normalize/unescape-html-entities (:address updates)))
                          updates (cond-> updates
                                    (contains? updates :display_name)
                                    (assoc :display_name display-name)
                                    (contains? updates :address)
                                    (assoc :address address))]
                      (cond-> updates
                        (or (contains? updates :display_name)
                          (contains? updates :address))
                        (assoc :normalized_key (normalize/normalize-store-key (str (or display-name "") " " (or address "")))))))
   :has-search? true
   :has-count? true})

(def manufacturer-config
  {:table-name "manufacturers"
   :primary-key :id
   :required-fields [:display_name]
   :allowed-order-by {:display-name :display_name
                      :normalized-key :normalized_key
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :display_name
   :search-fields [:display_name :normalized_key]
   :text-filter-columns {:normalized-key :normalized_key}
   :field-transformers {:normalized_key normalize/normalize-manufacturer-key}
   :before-insert (fn [data]
                    (let [display-name (normalize/unescape-html-entities (:display_name data))]
                      (-> data
                        (assoc :display_name display-name)
                        (assoc :id (UUID/randomUUID))
                        (assoc :normalized_key (normalize/normalize-manufacturer-key display-name)))))
   :before-update (fn [_id updates]
                    (if (:display_name updates)
                      (let [display-name (normalize/unescape-html-entities (:display_name updates))]
                        (assoc updates
                          :display_name display-name
                          :normalized_key (normalize/normalize-manufacturer-key display-name)))
                      updates))
   :has-search? true
   :has-count? true})

(def category-config
  {:table-name "categories"
   :primary-key :id
   :required-fields [:name]
   :allowed-order-by {:name :name
                      :description :description
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :name
   :search-fields [:name :description]
   :text-filter-columns {:description :description}
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))))
   :before-update (fn [_id updates]
                    updates)
   :has-search? true
   :has-count? true})

(def expense-category-config
  {:table-name "expense_categories"
   :primary-key :id
   :tenant-scoped? true
   :required-fields [:name]
   :allowed-order-by {:name :name
                      :is-default :is_default
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :name
   :search-fields [:name]
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))
                      (update :exclude_from_reports #(boolean %))
                      (update :is_default #(boolean %))))
   :before-update (fn [_id updates]
                    (cond-> updates
                      (contains? updates :exclude_from_reports)
                      (update :exclude_from_reports boolean)

                      (contains? updates :is_default)
                      (update :is_default boolean)))
   :has-search? true
   :has-count? true})

(def expense-context-config
  {:table-name "expense_contexts"
   :primary-key :id
   :tenant-scoped? true
   :required-fields [:name]
   :allowed-order-by {:name :name
                      :is-active :is_active
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :name
   :search-fields [:name :description]
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))
                      (update :is_active #(if (nil? %) true (boolean %)))))
   :before-update (fn [_id updates]
                    (cond-> updates
                      (contains? updates :is_active)
                      (update :is_active boolean)))
   :has-search? true
   :has-count? true})

(def expense-template-config
  {:table-name "expense_templates"
   :primary-key :id
   :tenant-scoped? true
   :required-fields [:name :kind]
   :allowed-order-by {:name :name
                      :kind :kind
                      :status :status
                      :next-due-date :next_due_date
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :name
   :search-fields [:name :default_notes]
   :text-filter-columns {:kind :kind
                         :status :status}
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))
                      (update :kind #(vector :cast (or % "generic") :expense_template_kind))
                      (update :status #(vector :cast (or % "active") :expense_template_status))
                      (update :default_currency #(when % [:cast % :currency]))
                      (update :recurrence_frequency #(when % [:cast % :recurrence_frequency]))))
   :before-update (fn [_id updates]
                    (cond-> updates
                      (contains? updates :kind)
                      (update :kind #(when % [:cast % :expense_template_kind]))

                      (contains? updates :status)
                      (update :status #(when % [:cast % :expense_template_status]))

                      (contains? updates :default_currency)
                      (update :default_currency #(when % [:cast % :currency]))

                      (contains? updates :recurrence_frequency)
                      (update :recurrence_frequency #(when % [:cast % :recurrence_frequency]))))
   :has-search? true
   :has-count? true})

(def expense-template-line-config
  {:table-name "expense_template_lines"
   :primary-key :id
   :required-fields [:template_id :label]
   :allowed-order-by {:label :label
                      :sort-order :sort_order
                      :is-active :is_active
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :sort_order
   :search-fields [:label]
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))
                      (update :is_active #(if (nil? %) true (boolean %)))))
   :before-update (fn [_id updates]
                    (cond-> updates
                      (contains? updates :is_active)
                      (update :is_active boolean)))
   :has-search? true
   :has-count? true})

(def recurring-expense-reminder-config
  {:table-name "recurring_expense_reminders"
   :primary-key :id
   :required-fields [:template_id :due_date]
   :allowed-order-by {:due-date :due_date
                      :status :status
                      :reminded-at :reminded_at
                      :snoozed-until :snoozed_until
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :due_date
   :search-fields []
   :text-filter-columns {:status :status}
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))
                      (update :status #(vector :cast (or % "pending") :recurring_expense_reminder_status))))
   :before-update (fn [_id updates]
                    (cond-> updates
                      (contains? updates :status)
                      (update :status #(when % [:cast % :recurring_expense_reminder_status]))))
   :has-count? true})

(def city-config
  {:table-name "cities"
   :primary-key :id
   :required-fields [:name]
   :allowed-order-by {:name :name
                      :normalized-key :normalized_key
                      :zip :zip
                      :country :country
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :name
   :search-fields [:name :normalized_key]
   :text-filter-columns {:normalized-key :normalized_key
                         :zip            :zip
                         :country        :country}
   :before-insert (fn [data]
                    (let [name (some-> (:name data) normalize/unescape-html-entities str str/trim not-empty)]
                      (when-not name
                        (throw (ex-info "name is required" {:status 400 :field :name})))
                      (-> data
                        (assoc :id (UUID/randomUUID))
                        (assoc :name name)
                        (assoc :normalized_key (normalize/normalize-city-key name)))))
   :before-update (fn [_id updates]
                    (if (contains? updates :name)
                      (let [name (some-> (:name updates) normalize/unescape-html-entities str str/trim)]
                        (when (str/blank? name)
                          (throw (ex-info "name is required" {:status 400 :field :name})))
                        (assoc updates
                          :name name
                          :normalized_key (normalize/normalize-city-key name)))
                      updates))
   :has-search? true
   :has-count? true})

(defn- normalize-subcategory-name-key
  [value]
  (some-> value
    normalize/unescape-html-entities
    str
    str/trim
    not-empty
    normalize/normalize-store-key))

(defn- find-equivalent-subcategory
  [db category-id name exclude-id]
  (when (and category-id (seq (str (or name ""))))
    (let [normalized-name (normalize-subcategory-name-key name)]
      (when normalized-name
        (->> (jdbc/execute!
               db
               (sql/format
                 (cond-> {:select [:id :name]
                          :from [:subcategories]
                          :where [:= :category_id category-id]}
                   exclude-id (assoc :where [:and
                                             [:= :category_id category-id]
                                             [:<> :id exclude-id]])))
               {:builder-fn rs/as-unqualified-lower-maps})
          (some (fn [{existing-name :name :as row}]
                  (when (= normalized-name
                          (normalize-subcategory-name-key existing-name))
                    row))))))))

(defn- assert-unique-subcategory-name!
  [db category-id name exclude-id]
  (when-let [existing (find-equivalent-subcategory db category-id name exclude-id)]
    (throw (ex-info "subcategory with equivalent name already exists in this category"
             {:status 400
              :field :name
              :category-id category-id
              :conflicting-id (:id existing)
              :conflicting-name (:name existing)}))))

(def subcategory-config
  {:table-name "subcategories"
   :table-alias :sc
   :primary-key :id
   :required-fields [:category_id :name]
   :allowed-order-by {:name :sc/name
                      :category-name :c/name
                      :created-at :sc/created_at
                      :updated-at :sc/updated_at}
   :default-order-by :sc/name
   :search-fields [:sc/name :sc/description]
   :text-filter-columns {:category-name :c.name
                         :description   :sc.description}
   :joins [[:categories :c] [:= :c/id :sc/category_id]]
   :select-fields [[:sc.*]
                   [:c/name :category_name]]
   :before-insert (fn [db data]
                    (let [category-id (:category_id data)
                          name (some-> (:name data) normalize/unescape-html-entities str str/trim not-empty)]
                      (when-not category-id
                        (throw (ex-info "category_id is required" {:status 400 :field :category_id :data data})))
                      (when-not name
                        (throw (ex-info "name is required" {:status 400 :field :name :data data})))
                      (assert-unique-subcategory-name! db category-id name nil)
                      (-> data
                        (assoc :id (UUID/randomUUID))
                        (assoc :name name))))
   :before-update (fn [db id updates]
                    (if (or (contains? updates :name)
                          (contains? updates :category_id))
                      (let [current (jdbc/execute-one!
                                      db
                                      (sql/format {:select [:id :category_id :name]
                                                   :from [:subcategories]
                                                   :where [:= :id id]})
                                      {:builder-fn rs/as-unqualified-lower-maps})
                            category-id (if (contains? updates :category_id)
                                          (:category_id updates)
                                          (:category_id current))
                            name (if (contains? updates :name)
                                   (some-> (:name updates) normalize/unescape-html-entities str str/trim)
                                   (:name current))]
                        (when-not category-id
                          (throw (ex-info "category_id is required" {:status 400 :field :category_id :id id})))
                        (when (str/blank? name)
                          (throw (ex-info "name is required" {:status 400 :field :name :id id})))
                        (assert-unique-subcategory-name! db category-id name id)
                        (cond-> updates
                          (contains? updates :name) (assoc :name name)))
                      updates))
   :has-search? true
   :has-count? true})

(def payer-config
  {:table-name "payers"
   :table-alias :p
   :primary-key :id
   :tenant-scoped? true
   :required-fields [:label]
   :allowed-order-by {:label :p/label
                      :payer-type :p/type
                      :is-active :p/is_active
                      :created-at :p/created_at
                      :updated-at :p/updated_at}
   :default-order-by :p/label
   :search-fields [:p/label]
   :select-fields [[:p/*]
                   ;; Cast the payer_type enum to text so JDBC returns a plain
                   ;; string and JSON encoding stays predictable downstream.
                   [[:cast :p/type :text] :payer_type]
                   ;; Correlated subquery: expose only the privacy subject ref
                   ;; for the default-payer linkage, not users.id/name/email.
                   [{:select [:ues/subject_ref]
                     :from [[:user_expense_settings :ues]]
                     :where [:= :ues/default_payer_id :p/id]
                     :limit 1}
                    :user_subject_ref]
                   [{:select [[[:count :*] :n]]
                     :from [[:expenses :e]]
                     :where [:and
                             [:= :e/payer_id :p/id]
                             [:= :e/tenant_id :p/tenant_id]]}
                    :related_expense_count]]
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))
                      ;; Cast string to the payer_type enum; HoneySQL emits the
                      ;; correct ::payer_type cast so JDBC can bind the value.
                      (update :type (fn [t] [:cast (or t "custom") :payer_type]))
                      (update :is_default #(boolean %))
                      (update :is_active #(if (nil? %) true (boolean %)))))
   :before-update (fn [_id updates]
                    (dissoc updates :type))
   :has-search? true
   :has-count? true})

(def article-config
  {:table-name "articles"
   :primary-key :id
   :required-fields [:canonical_name :unit]
   :allowed-order-by {:canonical-name :canonical_name
                      :unit :unit
                      :normalized-key :normalized_key
                      :created-at :created_at
                      :updated-at :updated_at}
   :default-order-by :canonical_name
   :search-fields [:canonical_name :normalized_key :unit]
   :text-filter-columns {:canonical-name :canonical_name
                         :unit :unit
                         :normalized-key :normalized_key}
   :field-transformers {:normalized_key articles/normalize-article-key
                        :unit normalize-unit-value}
   :before-insert (fn [data]
                    (let [canonical-name (:canonical_name data)
                          unit (or (normalize-unit-value (:unit data)) "kom")]
                      (-> data
                        (assoc :id (UUID/randomUUID))
                        (assoc :unit unit)
                        (assoc :normalized_key (articles/normalize-article-key canonical-name)))))
   :before-update (fn [_id updates]
                    (cond-> updates
                      (contains? updates :unit)
                      (update :unit #(or (normalize-unit-value %) "kom"))

                      (:canonical_name updates)
                      (assoc :normalized_key (articles/normalize-article-key (:canonical_name updates)))))
   :has-search? true
   :has-count? true})

(def expense-config
  {:table-name "expenses"
   :primary-key :id
   :tenant-scoped? true
   :required-fields [:payer_id :purchased_at :total_amount]
   :allowed-order-by {:expense-date :purchased_at
                      :purchased-at :purchased_at
                      :created-at :created_at
                      :updated-at :updated_at
                      :total-amount :total_amount}
   :default-order-by :purchased_at
   :search-fields [:s/display_name :p/label]
   :joins [[:suppliers :s] [:= :s/id :e/supplier_id]
           [:payers :p] [:= :p/id :e/payer_id]]
   :select-fields [[:e.*]
                   [:s/display_name :supplier_display_name]
                   [:s/normalized_key :supplier_normalized_key]
                   [:p/label :payer_label]
                   [[:cast :p/type :text] :payer_type]]
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))
                      (update :currency #(when % [:cast % :currency]))))
   :before-update (fn [_id updates]
                    (-> updates
                      (update :currency #(when % [:cast % :currency]))))
   :has-count? true})

(def expense-item-config
  {:table-name "expense_items"
   :table-alias :ei
   :primary-key :ei/id
   :tenant-scoped? true
   :required-fields [:expense_id :line_total]
   :allowed-order-by {:expense-id :ei/expense_id
                      :raw-label :aa/raw_label
                      :alias-id :ei/alias_id
                      :created-at :ei/created_at
                      :qty :ei/qty
                      :unit :ei/unit
                      :unit-price :ei/unit_price
                      :line-total :ei/line_total
                      :expense-purchased-at :e/purchased_at
                      :article-canonical-name :a/canonical_name}
   :default-order-by :ei/created_at
   :search-fields [:aa/raw_label :a/canonical_name]
   :joins [[:expenses :e] [:= :e/id :ei/expense_id]
           [:article_aliases :aa] [:= :aa/id :ei/alias_id]
           [:articles :a] [:= :a/id :aa/article_id]]
   :select-fields [[:ei.*]
                   [:aa/raw_label :raw_label]
                   [:aa/raw_label_normalized :raw_label_normalized]
                   [:e/purchased_at :expense_purchased_at]
                   [:a/canonical_name :article_canonical_name]]
   :field-transformers {:unit normalize-unit-value}
   :before-insert (fn [db data]
                    (let [alias-svc (requiring-resolve 'app.domain.backend.expenses.services.article-aliases/find-or-create-alias!)
                          raw-label (some-> (:raw_label data) str str/trim)
                          supplier-id (:supplier_id data)
                          unit (normalize-unit-value (:unit data))]
                      (cond
                        (some? (:alias_id data))
                        (cond-> (dissoc data :raw_label :supplier_id)
                          unit (assoc :unit unit))

                        (some? raw-label)
                        (let [alias (alias-svc db supplier-id raw-label unit)]
                          (cond-> (-> data
                                    (dissoc :raw_label :supplier_id)
                                    (assoc :alias_id (:id alias)))
                            unit (assoc :unit unit)))

                        :else
                        (throw (ex-info "raw_label or alias_id is required"
                                 {:entity "expense_items"
                                  :missing-field :raw_label})))))
   :before-update (fn [db _id updates]
                    (let [alias-svc (requiring-resolve 'app.domain.backend.expenses.services.article-aliases/find-or-create-alias!)
                          raw-label (some-> (:raw_label updates) str str/trim)
                          supplier-id (:supplier_id updates)
                          unit (normalize-unit-value (:unit updates))]
                      (cond
                        (some? raw-label)
                        (let [alias (alias-svc db supplier-id raw-label unit)]
                          (cond-> (-> updates
                                    (dissoc :raw_label :supplier_id)
                                    (assoc :alias_id (:id alias)))
                            unit (assoc :unit unit)))

                        :else
                        (cond-> (dissoc updates :raw_label :supplier_id)
                          unit (assoc :unit unit)))))
   :has-search? true
   :has-count? true})

(def receipt-config
  {:table-name "receipts"
   :primary-key :id
   :tenant-scoped? true
   :required-fields [:storage_key]
   :allowed-order-by {:created-at :created_at
                      :updated-at :updated_at
                      :status :status}
   :default-order-by :created_at
   :search-fields [:original_filename :storage_key]
   :before-insert (fn [data]
                    (-> data
                      (assoc :id (UUID/randomUUID))
                      (assoc :status "uploaded")
                      (update :status #(vector :cast % :receipt_status))))
   :has-count? true
   :custom-service? true})

