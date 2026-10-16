(ns app.domain.backend.expenses.services.article-resolution.verify-test
  (:require
    [app.domain.backend.expenses.services.article-resolution.verify :as verify]
    [clojure.test :refer [deftest is testing]]))

(def ^:private index
  {"Mliječni proizvodi i jaja" {:subcategories [{:name "Sir"} {:name "Mlijeko"}]}
   "Pakovana hrana i pića" {:subcategories []}})

(def ^:private item
  {:kind "article" :canonical_name "Belje ABC sir Classic 100g" :category nil :subcategory "Sir"
   :manufacturer "Belje" :confidence 90})

(defn- answers
  [cat conf same mfr]
  {:category {:type "choice" :choice cat :confidence conf}
   :same-product {:type "noul" :noul same}
   :manufacturer-ok {:type "noul" :noul mfr}})

(deftest questions-test
  (let [q (verify/questions index item)]
    (is (= #{"none" "Mliječni proizvodi i jaja" "Pakovana hrana i pića"} (set (keys (get-in q [:category :criteria])))))
    (is (= "Includes: Mlijeko, Sir" (get-in q [:category :criteria "Mliječni proizvodi i jaja"])))
    (is (contains? q :manufacturer-ok))
    (is (not (contains? (verify/questions index (assoc item :manufacturer nil)) :manufacturer-ok)))))

(deftest apply-answers-test
  (testing "confident Jev category fills a null Claude category (the Belje case)"
    (let [r (verify/apply-answers {} index item (answers "Mliječni proizvodi i jaja" 0.9 0.9 0.9))]
      (is (= "Mliječni proizvodi i jaja" (get-in r [:item :category])))
      (is (empty? (:problems r)))
      (is (true? (get-in r [:summary :category_filled])))))
  (testing "low confidence does not fill"
    (is (nil? (get-in (verify/apply-answers {} index item (answers "Mliječni proizvodi i jaja" 0.4 0.9 0.9)) [:item :category]))))
  (testing "\"none\" never fills"
    (is (nil? (get-in (verify/apply-answers {} index item (answers "none" 0.99 0.9 0.9)) [:item :category]))))
  (testing "confident disagreement is flagged"
    (let [r (verify/apply-answers {} index (assoc item :category "Pakovana hrana i pića")
              (answers "Mliječni proizvodi i jaja" 0.9 0.9 0.9))]
      (is (= [:category-disagreement] (:problems r)))
      (is (= "Pakovana hrana i pića" (get-in r [:item :category])) "Claude's value is kept for review")))
  (testing "name mismatch and doubtful manufacturer"
    (let [r (verify/apply-answers {} index item (answers "Mliječni proizvodi i jaja" 0.9 0.2 0.1))]
      (is (= [:name-mismatch] (:problems r)))
      (is (nil? (get-in r [:item :manufacturer])))
      (is (true? (get-in r [:summary :manufacturer_dropped]))))))

(deftest fill-category-mode-test
  (let [opts {:mode :fill-category}]
    (testing "only items without a valid category are verified"
      (is (true? (verify/needs-verification? opts index item)))
      (is (false? (verify/needs-verification? opts index (assoc item :category "Pakovana hrana i pića"))))
      (is (true? (verify/needs-verification? opts index (assoc item :category "Ostalo"))) "excluded category counts as invalid")
      (is (false? (verify/needs-verification? opts index (assoc item :kind "noise")))))
    (testing "full mode verifies every article"
      (is (true? (verify/needs-verification? {:mode :full} index (assoc item :category "Pakovana hrana i pića")))))
    (testing "only the category question is asked"
      (is (= [:category] (keys (verify/questions index item opts))))
      (is (= #{:category :same-product :manufacturer-ok} (set (keys (verify/questions index item))))))))
