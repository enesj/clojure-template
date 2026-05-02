(ns app.domain.backend.expenses.services.user-expense-reports.shared
  (:require
    [app.template.backend.security.privacy-subject :as privacy-subject]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs])
  (:import
    [java.math BigDecimal]
    [java.time YearMonth ZoneOffset]
    [java.util UUID]))

(def day-of-week-template
  [{:iso_day_of_week 1 :day_key "mon" :day_label "Monday"}
   {:iso_day_of_week 2 :day_key "tue" :day_label "Tuesday"}
   {:iso_day_of_week 3 :day_key "wed" :day_label "Wednesday"}
   {:iso_day_of_week 4 :day_key "thu" :day_label "Thursday"}
   {:iso_day_of_week 5 :day_key "fri" :day_label "Friday"}
   {:iso_day_of_week 6 :day_key "sat" :day_label "Saturday"}
   {:iso_day_of_week 7 :day_key "sun" :day_label "Sunday"}])

(def size-bucket-template
  [{:bucket-key "lt_10" :bucket-label "Under 10" :sort-order 1}
   {:bucket-key "10_25" :bucket-label "10 to 24.99" :sort-order 2}
   {:bucket-key "25_50" :bucket-label "25 to 49.99" :sort-order 3}
   {:bucket-key "50_100" :bucket-label "50 to 99.99" :sort-order 4}
   {:bucket-key "100_200" :bucket-label "100 to 199.99" :sort-order 5}
   {:bucket-key "gte_200" :bucket-label "200 and above" :sort-order 6}])

(def size-bucket-case-sql
  "CASE
     WHEN e.total_amount < 10 THEN 'lt_10'
     WHEN e.total_amount >= 10 AND e.total_amount < 25 THEN '10_25'
     WHEN e.total_amount >= 25 AND e.total_amount < 50 THEN '25_50'
     WHEN e.total_amount >= 50 AND e.total_amount < 100 THEN '50_100'
     WHEN e.total_amount >= 100 AND e.total_amount < 200 THEN '100_200'
     ELSE 'gte_200'
   END")

(defn bd
  [v]
  (cond
    (instance? BigDecimal v) v
    (nil? v) 0M
    :else (bigdec v)))

(defn ensure-uuid
  [v]
  (cond
    (nil? v) nil
    (instance? UUID v) v
    :else (UUID/fromString (str v))))

(defn query-many
  [db query]
  (jdbc/execute! db (sql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn query-one
  [db query]
  (jdbc/execute-one! db (sql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn id-filter-clause
  [column value]
  (cond
    (nil? value) nil
    (sequential? value) (let [values (vec (remove nil? value))]
                          (when (seq values)
                            [:in column values]))
    :else [:= column value]))

(defn base-where
  [user-id {:keys [tenant-id from to currency supplier-id payer-id expense-category-id
                   expense-context-id expense-context-missing?]}]
  (let [supplier-clause (id-filter-clause :e.supplier_id supplier-id)
        payer-clause (id-filter-clause :e.payer_id payer-id)
        expense-category-clause (id-filter-clause :e.expense_category_id expense-category-id)
        expense-context-clause (when-not expense-context-missing?
                                 (id-filter-clause :e.expense_context_id expense-context-id))
        ;; Drop expenses whose category is flagged `exclude_from_reports=true`.
        ;; NOT EXISTS is null-safe: expenses with no category still pass.
        exclude-flagged-category-clause
        [:not
         [:exists
          {:select [[[:inline 1]]]
           :from [[:expense_categories :ec_excl]]
           :where [:and
                   [:= :ec_excl.id :e.expense_category_id]
                   [:= :ec_excl.exclude_from_reports true]]}]]]
    (cond-> [:and
             exclude-flagged-category-clause]
      user-id (conj (privacy-subject/user-match-clause :e.subject_ref user-id))
      tenant-id (conj [:= :e.tenant_id tenant-id])
      from (conj [:>= :e.purchased_at from])
      to (conj [:<= :e.purchased_at to])
      (seq currency) (conj [:= :e.currency [:cast currency :currency]])
      supplier-clause (conj supplier-clause)
      payer-clause (conj payer-clause)
      expense-category-clause (conj expense-category-clause)
      (true? expense-context-missing?) (conj [:is :e.expense_context_id nil])
      expense-context-clause (conj expense-context-clause))))

(defn item-base-where
  [user-id {:keys [category-id subcategory-id manufacturer-id] :as opts}]
  (let [category-clause (id-filter-clause :c.id category-id)
        subcategory-clause (id-filter-clause :sc.id subcategory-id)
        manufacturer-clause (id-filter-clause :m.id manufacturer-id)]
    (cond-> (base-where user-id opts)
      category-clause (conj category-clause)
      subcategory-clause (conj subcategory-clause)
      manufacturer-clause (conj manufacturer-clause))))

(defn month->range
  [month]
  (let [month* (YearMonth/parse month)
        from (-> month* (.atDay 1) (.atStartOfDay ZoneOffset/UTC) (.toInstant))
        to (-> month* (.plusMonths 1) (.atDay 1) (.atStartOfDay ZoneOffset/UTC) (.toInstant))]
    {:from from
     :to to}))

(defn enrich-with-all-weekdays
  [rows currency]
  (let [rows-by-key (into {} (map (juxt (fn [row] [(:currency row) (int (:iso_day_of_week row))]) identity) rows))
        currencies (cond
                     (seq currency) [currency]
                     (seq rows) (sort (distinct (keep :currency rows)))
                     :else [])]
    (mapv (fn [curr]
            {:currency curr
             :days (mapv (fn [{:keys [iso_day_of_week day_key day_label]}]
                           (let [base {:iso_day_of_week iso_day_of_week
                                       :day_key day_key
                                       :day_label day_label
                                       :total_amount 0M
                                       :expense_count 0}]
                             (merge base (get rows-by-key [curr iso_day_of_week]))))
                     day-of-week-template)})
      currencies)))

(defn fill-size-buckets
  [rows currency]
  (let [rows-by-key (into {} (map (juxt (fn [row] [(:currency row) (:bucket_key row)]) identity) rows))
        currencies (cond
                     (seq currency) [currency]
                     (seq rows) (sort (distinct (keep :currency rows)))
                     :else [])]
    (mapcat (fn [curr]
              (map (fn [{:keys [bucket-key bucket-label sort-order]}]
                     (let [base {:currency curr
                                 :bucket_key bucket-key
                                 :bucket_label bucket-label
                                 :sort_order sort-order
                                 :total_amount 0M
                                 :expense_count 0}]
                       (merge base (get rows-by-key [curr bucket-key]))))
                size-bucket-template))
      currencies)))

(defn uncategorized-row
  [currency]
  {:category_key "uncategorized"
   :category_name "Uncategorized"
   :subcategory_key "uncategorized"
   :subcategory_name "Uncategorized"
   :currency currency
   :total_amount 0M
   :subcategory_total_amount 0M
   :line_count 0})

(defn ensure-uncategorized
  [rows currency]
  (let [currencies (cond
                     (seq currency) [currency]
                     (seq rows) (sort (distinct (keep :currency rows)))
                     :else [])]
    (reduce
      (fn [acc curr]
        (if (some (fn [row]
                    (and (= curr (:currency row))
                      (= "uncategorized" (:category_key row))))
              acc)
          acc
          (conj acc (uncategorized-row curr))))
      (vec rows)
      currencies)))

(defn allocation-with-percentages
  [rows]
  (let [rows-by-currency (group-by :currency rows)
        totals-by-currency (into {}
                             (map (fn [[curr rs]]
                                    [curr (reduce (fn [acc {:keys [total_amount]}]
                                                    (+ acc (bd total_amount)))
                                            0M
                                            rs)]))
                             rows-by-currency)]
    (mapv (fn [row]
            (let [currency (:currency row)
                  total (get totals-by-currency currency 0M)
                  row-total (bd (:total_amount row))
                  pct (if (pos? (compare total 0M))
                        (* 100.0 (/ (double row-total) (double total)))
                        0.0)]
              (assoc row :allocation_pct pct)))
      rows)))

(defn summary-by-currency
  [db user-id opts]
  (query-many
    db
    {:select [:e.currency
              [[:sum :e.total_amount] :total_amount]
              [[:count :*] :expense_count]]
     :from [[:expenses :e]]
     :where (base-where user-id opts)
     :group-by [:e.currency]
     :order-by [[:e.currency :asc]]}))

(defn supplier-breakdown-by-currency
  [db user-id opts]
  (query-many
    db
    {:select [:e.supplier_id
              [:s.display_name :supplier_name]
              :e.currency
              [[:sum :e.total_amount] :total_amount]
              [[:count :*] :expense_count]]
     :from [[:expenses :e]]
     :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]]
     :where (base-where user-id opts)
     :group-by [:e.supplier_id :s.display_name :e.currency]
     :order-by [[[:sum :e.total_amount] :desc]
                [:s.display_name :asc]]}))

(defn option-row
  [row]
  (let [id (:id row)
        name (:name row)
        category-id (:category_id row)]
    (when (and id (some? name))
      (cond-> {:id (str id)
               :name (str name)}
        category-id (assoc :category_id (str category-id))))))

(defn query-option-list
  [db query]
  (->> (query-many db query)
    (keep option-row)
    vec))
