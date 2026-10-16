(ns app.domain.backend.expenses.services.article-resolution.noise-test
  (:require
    [app.domain.backend.expenses.services.article-resolution.noise :as noise]
    [clojure.test :refer [deftest is testing]]))

(defn- reason
  [label]
  (noise/ocr-noise-reason {:raw_label label :raw_label_normalized label}))

(deftest ocr-noise-reason-test
  (testing "noise labels"
    (is (= "punctuation-only" (reason "----")))
    (is (= "digits-only" (reason "123")))
    (is (= "too-few-alnum" (reason "ab")))
    (is (= "no-word" (reason "6f93")))
    (is (= "blank" (reason "  ")))
    (is (= "placeholder" (reason "unknown"))))
  (testing "code-only fragments from real receipts"
    (is (= "digits-only" (reason "69 60936564905")))
    (is (= "digits-only" (reason "0,280x 7,95")) "weight x price line")
    (is (= "too-few-alnum" (reason "KO")))
    (is (= "no-word" (reason "MG 1 A 3a26")))
    (is (= "no-word" (reason "A 30 3b52")))
    (is (= "no-word" (reason "X 10 3470")))
    (is (= "unit-only" (reason "kom 1")))
    (is (= "word-with-code" (reason "0 SAP 172e")))
    (is (= "word-with-code" (reason "ČVRST 17e0")))
    (is (= "word-with-code" (reason "SNALIJ 577f"))))
  (testing "real product labels are not noise"
    (is (nil? (reason "Mlijeko Meggle 1L")))
    (is (nil? (reason "Čaj")) "letters with diacritics count (was too-few-alnum)")
    (is (nil? (reason "KLEPE")))
    (is (nil? (reason "Bubrezi 175g")))
    (is (nil? (reason "Vegeta 250")))
    (is (nil? (reason "CORTINAT KREMA 75 ML 20a6")) "two words next to a pharmacy code")
    (is (nil? (reason "PIVO S PREMIUM 0,33L")))
    (is (nil? (reason "Ruzice"))))
  (testing "blank normalized label"
    (is (= "blank-normalized"
          (noise/ocr-noise-reason {:raw_label "Mlijeko" :raw_label_normalized ""})))))
