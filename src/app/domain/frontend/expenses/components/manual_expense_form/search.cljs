(ns app.domain.frontend.expenses.components.manual-expense-form.search
  "Client-side fuzzy search across expense entity types.
   Pure functions, no side effects."
  (:require
    [clojure.string :as str]))

;; ─────────────────────────────────────────────
;; Entity type metadata
;; ─────────────────────────────────────────────

(def entity-type-config
  {:supplier {:icon "🏪" :label "Supplier" :color "blue"
              :name-fn #(or (:display-name %) (:display_name %) "")}
   :store {:icon "🏬" :label "Store" :color "emerald"
           :name-fn #(or (:display-name %) (:display_name %) "")}
   :category {:icon "📂" :label "Category" :color "purple"
              :name-fn #(or (:name %) "")}
   :expense-context {:icon "🏷️" :label "Expense context" :color "violet"
         :name-fn #(or (:name %) "")}
   :article {:icon "📦" :label "Article" :color "amber"
             :name-fn #(or (:canonical-name %) (:canonical_name %) "")}
   :payer {:icon "👤" :label "Payer" :color "teal"
           :name-fn #(or (:label %) (:name %) "")}
   :date {:icon "📅" :label "Date" :color "sky"
          :name-fn (constantly "")}
   :currency {:icon "💱" :label "Currency" :color "orange"
              :name-fn (constantly "")}})

(defn entity-type-info
  [entity-type]
  (get entity-type-config entity-type))

(defn normalize-entity-type
  [result]
  (some-> (or (:entity-type result) (:entity_type result)) keyword))

(defn result-last-price
  [result]
  (or (:last_price result) (:last-price result)))

(defn result-last-price-source
  [result]
  (some-> (or (:last_price_source result) (:last-price-source result)) keyword))

(defn result-last-price-supplier-name
  [result]
  (or (:last_price_supplier_display_name result)
    (:last-price-supplier-display-name result)
    (:supplier_display_name result)
    (:supplier-display-name result)))

(defn global-last-price?
  [result]
  (= :global (result-last-price-source result)))

(defn last-price-tooltip
  [result]
  (when (global-last-price? result)
    (when-let [supplier-name (result-last-price-supplier-name result)]
      (str "Last price from " supplier-name))))

(defn filter-results-by-entity-types
  [results allowed-entity-types]
  (let [allowed-set (set allowed-entity-types)]
    (->> (or results [])
      (filter #(contains? allowed-set (normalize-entity-type %)))
      vec)))

;; ─────────────────────────────────────────────
;; Number detection and parsing
;; ─────────────────────────────────────────────

(defn number-input?
  "Returns true if the trimmed input looks like a monetary amount."
  [s]
  (some? (re-matches #"^\d+([.,]\d{0,2})?$" (str/trim (str s)))))

;; ─────────────────────────────────────────────
;; Fuzzy matching
;; ─────────────────────────────────────────────

(defn- score-match
  "Score how well `query` matches `text`. Returns 0-100 or nil for no match."
  [query text]
  (let [q (str/lower-case (str/trim (str query)))
        t (str/lower-case (str text))]
    (cond
      (str/blank? q) nil
      (= q t) 100
      (str/starts-with? t q) 85
      ;; Any word in text starts with query
      (some #(str/starts-with? % q) (str/split t #"\s+")) 70
      ;; Substring match
      (str/includes? t q) 55
      :else nil)))

(defn dedupe-results
  [results]
  (:results
   (reduce (fn [{:keys [results seen] :as acc} result]
             (let [entity-type (normalize-entity-type result)
                   key [entity-type (str (:id result))]
                   normalized-result (assoc result :entity-type entity-type)]
               (cond
                 (nil? entity-type)
                 acc

                 (contains? seen key)
                 (let [idx (get seen key)]
                   (update acc :results assoc idx (merge (nth results idx) normalized-result)))

                 :else
                 {:results (conj results normalized-result)
                  :seen (assoc seen key (count results))})))
     {:results [] :seen {}}
     results)))

(def ^:private entity-type-order
  "Canonical round-robin order for interleaving mixed entity types."
  [:supplier :store :category :expense-context :article])

(defn- result-score
  [result]
  (or (:score result)
    (some-> (:relevance result) (* 100))
    0))

(defn balance-results
  "Interleave results round-robin by entity type so the user sees a diverse mix.
   Within each type group, higher-scored items appear first."
  [results limit]
  (let [groups (reduce (fn [acc result]
                         (let [etype (normalize-entity-type result)]
                           (update acc etype (fnil conj []) result)))
                 {} results)
        groups (into {} (map (fn [[k v]] [k (sort-by result-score > v)])) groups)
        present-types (filterv #(contains? groups %) entity-type-order)]
    (loop [remaining (into {} (map (fn [[k v]] [k (seq v)])) groups)
           type-cycle (cycle present-types)
           out []
           empty-passes 0]
      (cond
        (>= (count out) limit) (vec (take limit out))
        (empty? remaining) (vec out)
        (>= empty-passes (count present-types)) (vec out)
        :else
        (let [etype (first type-cycle)]
          (if-let [items (get remaining etype)]
            (recur (if (next items)
                     (assoc remaining etype (next items))
                     (dissoc remaining etype))
              (rest type-cycle)
              (conj out (first items))
              0)
            (recur remaining (rest type-cycle) out (inc empty-passes))))))))

(defn merge-search-results
  [local-results backend-results limit]
  (balance-results
    (dedupe-results
      (concat (or local-results [])
        (or backend-results [])))
    limit))

;; ─────────────────────────────────────────────
;; Unified search
;; ─────────────────────────────────────────────

(defn search-all-entities
  "Search across all entity types. Returns sorted results.
   `entities` is {:suppliers [] :stores [] :categories [] :expense-contexts [] :articles []}
   `opts` is {:selected-supplier-id id-or-nil} for store filtering."
  [query entities opts]
  (let [q (str/trim (str query))]
    (when (>= (count q) 2)
      (let [{:keys [suppliers stores categories expense-contexts articles]} entities
            {:keys [selected-supplier-id]} opts
            search-type (fn [items entity-type]
                          (let [{:keys [name-fn]} (get entity-type-config entity-type)]
                            (->> items
                              (keep (fn [item]
                                      (let [item-name (name-fn item)
                                            score (score-match q item-name)]
                                        (when score
                                          (cond-> {:id (:id item)
                                                   :label item-name
                                                   :entity-type entity-type
                                                   :score score
                                                   :entity item}
                                            ;; Carry through last_price for articles
                                            ;; so the price is available even before
                                            ;; the backend search responds.
                                            (or (:last_price item) (:last-price item))
                                            (assoc :last_price (or (:last_price item) (:last-price item))))))))
                              (sort-by :score >)
                              (take 4))))
            ;; Filter stores by selected supplier if one is chosen
            filtered-stores (if selected-supplier-id
                              (filter #(= (str selected-supplier-id)
                                         (str (or (:supplier-id %) (:supplier_id %))))
                                stores)
                              stores)]
        (->> (concat
               (search-type suppliers :supplier)
               (search-type filtered-stores :store)
               (search-type categories :category)
           (search-type expense-contexts :expense-context)
               (search-type articles :article))
          (sort-by :score >)
          (take 10)
          vec)))))
