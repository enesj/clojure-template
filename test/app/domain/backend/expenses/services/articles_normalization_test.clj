(ns app.domain.backend.expenses.services.articles-normalization-test
  (:require
    [app.domain.backend.expenses.services.articles.normalization :as normalization]
    [clojure.test :refer [deftest is testing]]))

(deftest normalize-alias-label-transliterates-bosnian-diacritics
  (testing "single word"
    (is (= "caj" (normalization/normalize-alias-label "ČAJ"))))

  (testing "mixed bosnian letters"
    (is (= "caj-djumbir-sumski-zar-cevap"
          (normalization/normalize-alias-label "Čaj Đumbir Šumski Žar Ćevap")))))

(deftest normalize-article-key-transliterates-bosnian-diacritics
  (is (= "cokolada-djanduja"
        (normalization/normalize-article-key "Čokolada Đanduja"))))

(deftest normalization-still-removes-non-alphanumerics-after-transliteration
  (is (= "caj-menta-20"
        (normalization/normalize-alias-label "Čaj / menta! 20%")))
  (is (= "sok-zuti"
        (normalization/normalize-article-key "Sok žuti"))))

(def ^:private catalog-key-table
  "Frozen outputs: [input catalog-key backend-article-key] (PLAN-auto-article-resolution.md 2.3)."
  [["Coca-Cola 1.5L" "coca-cola-1-5l" "coca-cola-15l"]
   ["Đevđelija" "ev-elija" "djevdjelija"]
   ["Šećer 1kg" "secer-1kg" "secer-1kg"]
   ["Čokolada Milka 100g" "cokolada-milka-100g" "cokolada-milka-100g"]
   ["MLIJEKO MEGGLE 2,8% 1L" "mlijeko-meggle-2-8-1l" "mlijeko-meggle-28-1l"]
   ["  Kesa  PE -- velika " "kesa-pe-velika" "kesa-pe----velika"]
   ["L'Oréal Elvital" "l-oreal-elvital" "loral-elvital"]
   ["Nescafé Gold 200 g" "nescafe-gold-200-g" "nescaf-gold-200-g"]
   ["Ćevapi 10/1" "cevapi-10-1" "cevapi-101"]
   ["Žvakaće gume" "zvakace-gume" "zvakace-gume"]])

(deftest normalize-catalog-key-matches-bb-test
  (testing "catalog key equals the frozen bb normalize-key outputs"
    (doseq [[input expected _] catalog-key-table]
      (is (= expected (normalization/normalize-catalog-key input)) (pr-str input))))
  (testing "backend article key keeps its (different) behaviour"
    (doseq [[input _ expected] catalog-key-table]
      (is (= expected (normalization/normalize-article-key input)) (pr-str input))))
  (testing "nil stays nil"
    (is (nil? (normalization/normalize-catalog-key nil)))))
