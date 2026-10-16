(ns app.domain.backend.expenses.services.article-resolution.heuristics-test
  (:require
    [app.domain.backend.expenses.services.article-resolution.heuristics :as heuristics]
    [clojure.test :refer [deftest is testing]]))

(def ^:private categories
  ["Mliječni proizvodi i jaja" "Pakovana hrana i pića" "Zdravlje i apoteka"
   "Meso, morski plodovi i delikatesi"])

(def ^:private taxonomy
  {:brand-rules            [{:re (re-pattern "(?i)^\\Qmilka\\E\\b") :manufacturer "Mondelez International"
                             :category "Pakovana hrana i pića" :subcategory "Čokolada i bomboni"}
                            {:re (re-pattern "(?i)^\\Qmarlboro\\E\\b") :manufacturer "Philip Morris"
                             :category "Duhan i duvanski proizvodi" :subcategory "Cigarete"}]
   :keyword-category-hints {"mlijeko" ["Mliječni proizvodi i jaja" "Mlijeko"]
                            "secer"   ["Pakovana hrana i pića" "Opste"]}
   :meat-words             #{"sunka"}
   :supplier-hints         [{:re #"(?i)apoteka" :category "Zdravlje i apoteka"}]})

(deftest resolve-hint-test
  (testing "brand rule returns manufacturer, category and subcategory"
    (is (= {:category "Pakovana hrana i pića" :subcategory "Čokolada i bomboni"
            :manufacturer "Mondelez International" :source :brand-rule}
          (heuristics/resolve-hint taxonomy categories "Milka čokolada 100g" nil))))
  (testing "meat word maps to Svježe meso"
    (let [hint (heuristics/resolve-hint taxonomy categories "Šunka 1kg" nil)]
      (is (= "Meso, morski plodovi i delikatesi" (:category hint)))
      (is (= "Svježe meso" (:subcategory hint)))))
  (testing "keyword hint with generic subcategory keeps only the category"
    (let [hint (heuristics/resolve-hint taxonomy categories "Secer 1kg" nil)]
      (is (= "Pakovana hrana i pića" (:category hint)))
      (is (nil? (:subcategory hint)))))
  (testing "pharmacy supplier gives a category-only hint"
    (let [hint (heuristics/resolve-hint taxonomy categories "Nesto 1" "Apoteka Zdravlje")]
      (is (= "Zdravlje i apoteka" (:category hint)))
      (is (nil? (:subcategory hint)))))
  (testing "hint whose category is not in the DB is dropped"
    (is (nil? (heuristics/resolve-hint taxonomy categories "Marlboro Red" nil))))
  (testing "no match"
    (is (nil? (heuristics/resolve-hint taxonomy categories "Xyz" nil)))))

(deftest bad-brand-rule-test
  (testing "generic first word patterns are filtered"
    (is (true? (heuristics/bad-brand-rule? ["(?i)^\\Qmlijeko\\E\\b" "Meggle" "Mliječni proizvodi i jaja" nil]))))
  (testing "artifact manufacturers are filtered"
    (is (true? (heuristics/bad-brand-rule? ["(?i)^\\Qfoo\\E\\b" "SAR" "Ostalo" nil]))))
  (testing "a real brand rule is kept"
    (is (false? (heuristics/bad-brand-rule? ["(?i)^\\Qmilka\\E\\b" "Mondelez International" "Pakovana hrana i pića" nil])))))
