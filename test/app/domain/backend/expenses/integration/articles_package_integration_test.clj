(ns app.domain.backend.expenses.integration.articles-package-integration-test
  "Package columns on articles: filled on insert by the resolver catalog, backfill, unit-prices."
  (:require
    [app.backend.fixtures :as fixtures]
    [app.domain.backend.expenses.services.article-resolution.catalog :as catalog]
    [app.domain.backend.expenses.services.articles.package :as package]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [next.jdbc.transaction]))

(use-fixtures :each
  (fn [f]
    (binding [next.jdbc.transaction/*nested-tx* :ignore]
      (fixtures/with-transaction-rollback f))))

(defn- q1 [db sql-vec] (jdbc/execute-one! db sql-vec {:builder-fn rs/as-unqualified-lower-maps}))

(deftest ensure-article-fills-package
  (when-let [db fixtures/*test-db*]
    (let [{:keys [id]} (catalog/ensure-article! db {:canonical-name "IT Coca-Cola 1,25L" :unit "kom"})
          row (q1 db ["SELECT package_size, package_unit, pack_count FROM articles WHERE id = ?" id])]
      (is (= 1250M (bigdec (:package_size row))))
      (is (= "ml" (:package_unit row)))
      (is (nil? (:pack_count row))))
    (testing "weighed articles get no package"
      (let [{:keys [id]} (catalog/ensure-article! db {:canonical-name "IT Sir 500g" :unit "kg"})]
        (is (nil? (:package_size (q1 db ["SELECT package_size FROM articles WHERE id = ?" id]))))))))

(deftest backfill-and-unit-prices
  (when-let [db fixtures/*test-db*]
    (let [article (random-uuid) tenant (random-uuid) payer (random-uuid) expense (random-uuid)]
      (jdbc/execute-one! db ["INSERT INTO articles (id, canonical_name, normalized_key, unit) VALUES (?, 'IT Keks 2x300g', ?, 'kom')"
                             article (str "it-keks-" article)])
      (is (pos? (:filled (package/backfill! db {:dry-run? true}))))
      (is (nil? (:package_size (q1 db ["SELECT package_size FROM articles WHERE id = ?" article]))) "dry run writes nothing")
      (package/backfill! db {})
      (is (= [600M] (let [r (q1 db ["SELECT package_size * pack_count AS t FROM articles WHERE id = ?" article])] [(bigdec (:t r))])))
      (jdbc/execute-one! db ["INSERT INTO tenants (id, name, slug) VALUES (?, 'IT tenant', ?)" tenant (str "it-" tenant)])
      (jdbc/execute-one! db ["INSERT INTO payers (id, tenant_id, label) VALUES (?, ?, 'IT payer')" payer tenant])
      (jdbc/execute-one! db ["INSERT INTO expenses (id, tenant_id, payer_id, purchased_at, total_amount) VALUES (?, ?, ?, NOW(), 3)"
                             expense tenant payer])
      (jdbc/execute-one! db ["INSERT INTO expense_items (id, tenant_id, expense_id, qty, unit_price, line_total, article_id)
                              VALUES (?, ?, ?, 1, 3.00, 3.00, ?)" (random-uuid) tenant expense article])
      (let [[row] (package/unit-prices db {:article-id article})]
        (is (= 5.00M (bigdec (:price_per_base row))) "3.00 KM for 600 g = 5.00 KM/kg")))))
