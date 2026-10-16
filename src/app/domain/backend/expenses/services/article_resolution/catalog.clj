(ns app.domain.backend.expenses.services.article-resolution.catalog
  "Catalog reads and writes for the article resolver (categories, subcategories, manufacturers,
  articles, alias mapping). Callers wrap writes in `jdbc/with-transaction`."
  (:require
    [app.domain.backend.expenses.services.articles.normalization :as normalization]
    [app.domain.backend.expenses.services.service-configs.normalization :as cfg-normalization]
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [taoensso.timbre :as log]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn- update-count [r] (long (or (::jdbc/update-count r) 0)))

(defn category-index
  "{category-name {:id uuid :subcategories [{:id :name} ...]}} sorted by name, without `excluded`
  categories. Subcategories are global (tenant_id IS NULL) and active."
  [db excluded]
  (let [cats (jdbc/execute! db ["SELECT id, name FROM categories ORDER BY name"] opts)
        subs (jdbc/execute! db
               [(str "SELECT id, category_id, name FROM subcategories"
                  " WHERE tenant_id IS NULL AND is_active ORDER BY name")]
               opts)
        by-cat (group-by :category_id subs)]
    (into (sorted-map)
      (for [{:keys [id name]} cats
            :when (not (contains? excluded name))]
        [name {:id id
               :subcategories (mapv #(select-keys % [:id :name]) (get by-cat id))}]))))

(defn sibling-article-id
  "Article id already mapped to aliases with the same normalized label and unit (most common), or nil."
  [db raw-label-normalized unit]
  (:article_id
   (jdbc/execute-one! db
     [(str "SELECT article_id, COUNT(*) c FROM article_aliases"
        " WHERE raw_label_normalized = ? AND unit = ? AND article_id IS NOT NULL"
        " GROUP BY article_id ORDER BY c DESC LIMIT 1")
      raw-label-normalized unit]
     opts)))

(defn catalog-keys
  "Distinct non-nil keys under which an article name may be stored: catalog key + backend key."
  [s]
  (vec (distinct (remove nil? [(normalization/normalize-catalog-key s)
                               (normalization/normalize-article-key s)]))))

(defn find-article
  "Article row {:id :canonical_name :normalized_key :unit} whose key is in `keys` for `unit`, or nil."
  [db keys unit]
  (when (seq keys)
    (jdbc/execute-one! db
      (sql/format {:select [:id :canonical_name :normalized_key :unit]
                   :from [:articles]
                   :where [:and [:in :normalized_key keys] [:= :unit unit]]
                   :limit 1})
      opts)))

(defn article-candidates
  "Up to `limit` articles of `unit` with a trigram-similar normalized_key: [{:key :name :score}].
  Returns [] when pg_trgm is unavailable."
  [db catalog-key unit limit]
  (if (str/blank? catalog-key)
    []
    (try
      (mapv (fn [{:keys [normalized_key canonical_name score]}]
              {:key normalized_key :name canonical_name :score score})
        (jdbc/execute! db
          [(str "SELECT normalized_key, canonical_name, similarity(normalized_key, ?) AS score"
             " FROM articles WHERE unit = ? AND normalized_key % ?"
             " ORDER BY score DESC, normalized_key LIMIT ?")
           catalog-key unit catalog-key limit]
          opts))
      (catch Exception e
        (log/warn "article-candidates failed (pg_trgm missing?)" (.getMessage e))
        []))))

(defn- match-name
  "Existing {:id :name} whose name equals `n` exactly, case-insensitively, or by catalog key."
  [rows n]
  (let [lower (str/lower-case n)
        k     (normalization/normalize-catalog-key n)]
    (or (some #(when (= n (:name %)) %) rows)
      (some #(when (= lower (str/lower-case (:name %))) %) rows)
      (some #(when (and k (= k (normalization/normalize-catalog-key (:name %)))) %) rows))))

(defn ensure-subcategory!
  "Global subcategory of `category-id` matching `sub-name` (exact, case-insensitive, then catalog key).
  Creates it when none matches and `allow-new?`. Returns {:id :name :created?} or nil."
  [db category-id sub-name allow-new?]
  (let [existing (fn []
                   (jdbc/execute! db
                     [(str "SELECT id, name FROM subcategories"
                        " WHERE category_id = ? AND tenant_id IS NULL AND is_active")
                      category-id]
                     opts))]
    (if-let [m (match-name (existing) sub-name)]
      (assoc m :created? false)
      (when allow-new?
        (let [r (jdbc/execute-one! db
                  [(str "INSERT INTO subcategories (id, category_id, tenant_id, name, description, is_active,"
                     " is_system_default, created_by_subject_ref, created_at, updated_at)"
                     " VALUES (?, ?, NULL, ?, NULL, TRUE, TRUE, 'article-resolver', NOW(), NOW())"
                     " ON CONFLICT (category_id, name) WHERE tenant_id IS NULL DO NOTHING")
                   (random-uuid) category-id sub-name])
              m (match-name (existing) sub-name)]
          (when m (assoc m :created? (pos? (update-count r)))))))))

(defn ensure-manufacturer!
  "Manufacturer matching `mfr-name` by catalog key, backend key or lower(display_name).
  Creates it when none matches and `allow-new?`. Returns {:id :display_name :created?} or nil."
  [db mfr-name allow-new?]
  (let [ck (normalization/normalize-catalog-key mfr-name)
        bk (cfg-normalization/normalize-manufacturer-key mfr-name)
        find! (fn []
                (jdbc/execute-one! db
                  [(str "SELECT id, display_name FROM manufacturers"
                     " WHERE normalized_key IN (?, ?) OR lower(display_name) = lower(?) LIMIT 1")
                   ck (or bk ck) mfr-name]
                  opts))]
    (if-let [m (find!)]
      (assoc m :created? false)
      (when (and allow-new? (not (str/blank? ck)))
        (let [r (jdbc/execute-one! db
                  (sql/format {:insert-into :manufacturers
                               :values [{:id (random-uuid) :display_name mfr-name :normalized_key ck}]
                               :on-conflict [:normalized_key] :do-nothing true}))]
          (when-let [m (find!)]
            (assoc m :created? (pos? (update-count r)))))))))

(defn ensure-article!
  "Find the article by catalog/backend key and unit, else insert it. Returns {:id :created?}."
  [db {:keys [canonical-name unit subcategory-id manufacturer-id]}]
  (if-let [a (find-article db (catalog-keys canonical-name) unit)]
    {:id (:id a) :created? false}
    (let [ck (normalization/normalize-catalog-key canonical-name)
          r  (jdbc/execute-one! db
               (sql/format {:insert-into :articles
                            :values [{:id (random-uuid) :canonical_name canonical-name :normalized_key ck
                                      :unit unit :subcategory_id subcategory-id :manufacturer_id manufacturer-id}]
                            :on-conflict [:normalized_key :unit] :do-nothing true}))
          a  (find-article db [ck] unit)]
      {:id (:id a) :created? (pos? (update-count r))})))

(defn map-aliases!
  "Point still-unmapped aliases at the article. Returns the ids actually updated."
  [db alias-ids article-id confidence]
  (if (empty? alias-ids)
    []
    (mapv :id
      (jdbc/execute! db
        (sql/format {:update :article_aliases
                     :set {:article_id article-id :confidence confidence :updated_at [:now]}
                     :where [:and [:in :id alias-ids] [:= :article_id nil]]
                     :returning [:id]})
        opts))))

(defn backfill-expense-items!
  "Set article_id on expense items of these aliases that have none yet. Returns the row count."
  [db alias-ids article-id]
  (if (empty? alias-ids)
    0
    (update-count
      (jdbc/execute-one! db
        (sql/format {:update :expense_items
                     :set {:article_id article-id}
                     :where [:and [:in :alias_id alias-ids] [:= :article_id nil]]})))))
