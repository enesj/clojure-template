(ns app.domain.backend.expenses.workers.article-resolver-test
  (:require
    [app.domain.backend.expenses.workers.article-resolver :as worker]
    [clojure.test :refer [deftest is testing]]))

(deftest start-is-a-noop-when-disabled-or-keyless-test
  (testing "disabled"
    (let [w (worker/start! :db {:article-resolver {:enabled? false} :anthropic {:api-key "k"}})]
      (is (instance? java.io.Closeable w))
      (.close w)))
  (testing "enabled without API key"
    (when-not (System/getenv "ANTHROPIC_API_KEY")
      (let [w (worker/start! :db {:article-resolver {:enabled? true}})]
        (is (instance? java.io.Closeable w))
        (.close w)))))
