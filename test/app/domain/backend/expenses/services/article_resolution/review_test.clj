(ns app.domain.backend.expenses.services.article-resolution.review-test
  (:require
    [app.domain.backend.expenses.services.article-resolution.review :as review]
    [clojure.test :refer [deftest is testing]]))

(deftest score-entries-test
  (let [entries [{:auto-applicable? true :verdict :ok}
                 {:auto-applicable? true :verdict :ok}
                 {:auto-applicable? true :verdict :wrong-name}
                 {:auto-applicable? true :verdict nil}
                 {:auto-applicable? false :verdict :should-be-noise}]
        s (review/score-entries entries)]
    (is (= 5 (:groups s)))
    (is (= 4 (:auto-applicable s)))
    (is (= 3 (:auto-reviewed s)))
    (is (< 0.66 (:auto-precision s) 0.67) "2 ok of 3 reviewed auto entries")
    (is (= 0.8 (:coverage s)))
    (is (= {:ok 2 :wrong-name 1 :should-be-noise 1} (:by-verdict s))))
  (testing "no reviewed auto entries"
    (is (nil? (:auto-precision (review/score-entries [{:auto-applicable? true}]))))))

(deftest score-review-sheet-rejects-unknown-verdicts-test
  (let [f (java.io.File/createTempFile "sheet" ".edn")]
    (try
      (spit f (pr-str [{:auto-applicable? true :verdict :nope}]))
      (is (thrown? clojure.lang.ExceptionInfo (review/score-review-sheet (.getPath f))))
      (spit f (pr-str [{:auto-applicable? true :verdict :ok}]))
      (is (= 1.0 (:auto-precision (review/score-review-sheet (.getPath f)))))
      (finally (.delete f)))))
