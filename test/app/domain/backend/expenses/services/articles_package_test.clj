(ns app.domain.backend.expenses.services.articles-package-test
  (:require
    [app.domain.backend.expenses.services.articles.package :as package]
    [clojure.test :refer [deftest is testing]]))

(defn- p [s] (some-> (package/parse-package s) (update :package-size bigdec)))

(deftest parse-package-test
  (testing "volumes in ml"
    (is (= {:package-size 1250M :package-unit "ml" :pack-count nil} (p "Coca-Cola 1,25L")))
    (is (= 500M (:package-size (p "Pivo Twist Mojito limeta 0,5L"))))
    (is (= 1000M (:package-size (p "Schweppes Tonic 1L"))))
    (is (= 330M (:package-size (p "Pepsi 33cl"))))
    (is (= 200M (:package-size (p "Riondo Prosecco DOC Spumante 200ml")))))
  (testing "weights in g"
    (is (= {:package-size 1000M :package-unit "g" :pack-count nil} (p "Pšenično brašno T400 1kg")))
    (is (= 182.5M (:package-size (p "Snickers Trio bomboni 182,5g"))))
    (is (= 3M (:package-size (p "Monural 3g granule"))))
    (is (= 80M (:package-size (p "Djumbir suhi 80gr")))))
  (testing "multipacks"
    (is (= {:package-size 300M :package-unit "g" :pack-count 2} (p "Plazma keks mljeveni 2x300g")))
    (is (= {:package-size 33M :package-unit "g" :pack-count 9} (p "Brotlinies pecivo 9x33g"))))
  (testing "piece counts"
    (is (= {:package-size 10M :package-unit "kom" :pack-count nil} (p "Baterije Eddie AA 10 kom")))
    (is (= 10M (:package-size (p "Tryccef film tablete 400mg 10 kom"))) "mg is a dosage, not the package"))
  (testing "no size or ambiguous -> nil"
    (is (nil? (p "Paloma toalet papir 3sl")))
    (is (nil? (p "Euthyrox tablete 100 mcg A100")))
    (is (nil? (p "Vivapore flaster 15x9cm")))
    (is (nil? (p "Ayran 2% m.m.")))
    (is (nil? (p "Set parfema Xmas EDP 100ml + 10ml")))
    (is (nil? (p "Pšenično brašno T400")))
    (is (nil? (p nil)))
    (is (nil? (p "")))))
