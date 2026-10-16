(ns app.domain.backend.expenses.services.article-resolution.taxonomy-test
  (:require
    [app.domain.backend.expenses.services.article-resolution.taxonomy :as taxonomy]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]))

(deftest current-loads-from-classpath-test
  (let [t (taxonomy/current)]
    (testing "non-empty brand rules and seed map"
      (is (seq (:brand-rules t)))
      (is (seq (:seed-subcategories t)))
      (is (every? :re (:brand-rules t))))
    (testing "cached between calls"
      (is (identical? t (taxonomy/current))))))

(deftest current-never-writes-files-test
  (let [f      (some-> (io/resource "articles/taxonomy/brand-rules.edn") io/file)
        before (when f (.lastModified f))
        _      (taxonomy/current)
        _      (taxonomy/current)]
    (is (some? f) "brand-rules.edn resolves to a file on the classpath")
    (is (= before (.lastModified f)))))
