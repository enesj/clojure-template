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
    (is (= "hex-ocr-artifact" (reason "6f93")))
    (is (= "blank" (reason "  ")))
    (is (= "placeholder" (reason "unknown"))))
  (testing "real product labels are not noise"
    (is (nil? (reason "Mlijeko Meggle 1L"))))
  (testing "blank normalized label"
    (is (= "blank-normalized"
          (noise/ocr-noise-reason {:raw_label "Mlijeko" :raw_label_normalized ""})))))
