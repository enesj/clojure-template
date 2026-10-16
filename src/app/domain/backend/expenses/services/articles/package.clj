(ns app.domain.backend.expenses.services.articles.package
  "Package contents of an article (one piece), parsed from its canonical name: package_size in the base
  unit (g | ml | kom) and pack_count for multipacks (\"6x50g\"). Price per kg/l is derived from
  expense_items.unit_price, never stored on the article."
  (:require
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(def ^:private unit-factors
  "Unit token (lower case) -> [base unit, factor]."
  {"g" ["g" 1] "gr" ["g" 1] "kg" ["g" 1000]
   "ml" ["ml" 1] "cl" ["ml" 10] "dl" ["ml" 100] "l" ["ml" 1000]
   "kom" ["kom" 1] "komada" ["kom" 1]})

(def ^:private size-re
  "Optional \"N x\" pack count, a number (decimal comma or dot) and a unit token that ends the word.
  mg/mcg (dosage), cm, % and \"sl\" (layers) are deliberately not units."
  #"(?i)(?<![\p{L}\d.,])(?:(\d{1,3})\s*[x×]\s*)?(\d+(?:[.,]\d+)?)\s*(kg|gr|g|ml|cl|dl|l|komada|kom)(?![\p{L}\d])")

(defn- parse-number [s] (bigdec (str/replace s "," ".")))

(defn parse-package
  "{:package-size bigdec :package-unit \"g\"|\"ml\"|\"kom\" :pack-count int|nil} from a canonical name, or nil
  when the name has no size or more than one (\"Set parfema EDP 100ml + 10ml\" is ambiguous)."
  [canonical-name]
  (when-not (str/blank? canonical-name)
    (let [matches (re-seq size-re canonical-name)]
      (when (= 1 (count matches))
        (let [[_ cnt n u] (first matches)
              [base factor] (unit-factors (str/lower-case u))
              size (.stripTrailingZeros (* (parse-number n) (bigdec factor)))]
          (when (pos? size)
            {:package-size size
             :package-unit base
             :pack-count (some-> cnt parse-long)}))))))

(defn package-columns
  "parse-package as snake_case article columns (all nil when the name has no size)."
  [canonical-name]
  (let [{:keys [package-size package-unit pack-count]} (parse-package canonical-name)]
    {:package_size package-size :package_unit package-unit :pack_count pack-count}))

(defn backfill!
  "Fill package columns of `kom` articles whose package_size is still NULL. With {:dry-run? true} nothing is
  written. Returns {:scanned n :filled n :by-unit {..} :samples [[name parsed] ..]}."
  [db {:keys [dry-run?]}]
  (let [rows   (jdbc/execute! db
                 ["SELECT id, canonical_name FROM articles WHERE unit = 'kom' AND package_size IS NULL ORDER BY canonical_name"]
                 {:builder-fn rs/as-unqualified-lower-maps})
        parsed (keep (fn [{:keys [id canonical_name]}]
                       (when-let [p (parse-package canonical_name)] [id canonical_name p]))
                 rows)]
    (when-not dry-run?
      (jdbc/with-transaction [tx db]
        (doseq [[id name* _] parsed]
          (jdbc/execute-one! tx
            (sql/format {:update :articles
                         :set (assoc (package-columns name*) :updated_at [:now])
                         :where [:= :id id]})))))
    {:scanned (count rows)
     :filled (count parsed)
     :by-unit (frequencies (map (comp :package-unit last) parsed))
     :samples (mapv (fn [[_ n p]] [n p]) (take 15 (shuffle parsed)))}))

(defn unit-prices
  "Purchases of articles with a package size, newest first, with the price per base quantity:
  :price_per_kg (g), :price_per_l (ml) or :price_per_piece (kom). unit_price / (pack_count x package_size).
  Optional filters: :article-id, :tenant-id; :limit (default 100)."
  [db {:keys [article-id tenant-id limit] :or {limit 100}}]
  (jdbc/execute! db
    (sql/format
      {:select [[:a.canonical_name :article] [:s.display_name :supplier] [:e.purchased_at :purchased_at]
                :ei.qty :ei.unit_price :a.package_size :a.package_unit :a.pack_count
                [[:round [:/ [:* :ei.unit_price [:case [:= :a.package_unit "kom"] [:inline 1] :else [:inline 1000]]]
                          [:* [:coalesce :a.pack_count [:inline 1]] :a.package_size]] [:inline 2]]
                 :price_per_base]]
       :from [[:expense_items :ei]]
       :join [[:articles :a] [:= :a.id :ei.article_id]
              [:expenses :e] [:= :e.id :ei.expense_id]]
       :left-join [[:suppliers :s] [:= :s.id :e.supplier_id]]
       :where (cond-> [:and [:is-not :a.package_size nil] [:is-not :ei.unit_price nil]
                       [:= :a.unit "kom"] [:> :a.package_size 0]]
                article-id (conj [:= :a.id article-id])
                tenant-id (conj [:= :ei.tenant_id tenant-id]))
       :order-by [[:e.purchased_at :desc] [:ei.id :asc]]
       :limit limit})
    {:builder-fn rs/as-unqualified-lower-maps}))

(comment
  (parse-package "Coca-Cola 1,25L")          ; {:package-size 1250M :package-unit "ml" :pack-count nil}
  (parse-package "Plazma keks mljeveni 2x300g")
  (parse-package "Set parfema EDP 100ml + 10ml") ; nil
  (backfill! (:database @system.state/state) {:dry-run? true})
  :rcf)
