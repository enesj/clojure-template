(ns app.domain.frontend.expenses.components.manual-expense-form.smart-input.search-helpers
  (:require
    [app.domain.frontend.expenses.components.manual-expense-form.search :as search]
    [clojure.string :as str]))

(defn build-related-article-prices
  [quick-add-related]
  (reduce
    (fn [m a]
      (if-let [p (:last_price a)]
        (assoc m (str (:id a)) p)
        m))
    {}
    (get-in quick-add-related [:related :articles])))

(defn attach-article-prices
  [articles related-article-prices]
  (if (seq related-article-prices)
    (mapv (fn [a]
            (if-let [p (get related-article-prices (str (:id a)))]
              (assoc a :last_price p)
              a))
      articles)
    articles))

(defn build-local-search-results
  [dropdown-open? input-text suppliers stores expense-categories expense-contexts articles-with-prices available-search-types context]
  (when (and dropdown-open?
          (>= (count (str/trim input-text)) 2))
    (-> (search/search-all-entities
          input-text
          {:suppliers suppliers
           :stores stores
           :categories expense-categories
           :expense-contexts expense-contexts
           :articles articles-with-prices}
          {:selected-supplier-id (some-> context :supplier :id)})
      (search/filter-results-by-entity-types available-search-types))))

(defn build-search-results
  [dropdown-open? local-search-results quick-search-results available-search-types]
  (let [filtered-quick-search-results (search/filter-results-by-entity-types quick-search-results available-search-types)]
    (when dropdown-open?
      (search/merge-search-results local-search-results filtered-quick-search-results 10))))

(defn resolve-phase-two-stores
  [selected-supplier-id-str supplier-stores-pool stores]
  (if (and selected-supplier-id-str
        (seq supplier-stores-pool))
    supplier-stores-pool
    stores))