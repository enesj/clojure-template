(ns app.domain.backend.expenses.integration.article-resolutions-routes-test
  "Handler-level tests (handler + DB) for the article resolutions admin API."
  (:require
    [app.backend.fixtures :as fixtures]
    [app.domain.backend.expenses.routes.article-resolutions :as routes]
    [cheshire.core :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each fixtures/with-transaction-rollback)

(defn- parse-body [resp] (json/parse-string (:body resp) true))

(deftest list-and-stats-test
  (when-let [db fixtures/*test-db*]
    (testing "list returns 200 and a vector"
      (let [resp (( routes/list-handler db) {:request-method :get :query-params {"limit" "5"}})
            body (parse-body resp)]
        (is (= 200 (:status resp)))
        (is (vector? (:resolutions body)))))
    (testing "invalid status is a 400"
      (is (= 400 (:status ((routes/list-handler db) {:request-method :get :query-params {"status" "bogus"}})))))
    (testing "stats"
      (let [resp ((routes/stats-handler db) {:request-method :get})
            body (parse-body resp)]
        (is (= 200 (:status resp)))
        (is (contains? body :last-24h))
        (is (contains? body :by-status))))))

(deftest id-validation-test
  (when-let [db fixtures/*test-db*]
    (doseq [h [(routes/approve-handler db {}) (routes/reject-handler db) (routes/requeue-handler db)]]
      (is (= 400 (:status (h {:request-method :post :path-params {:id "not-a-uuid"} :body {}})))))
    (testing "unknown but valid id is not a server error"
      (let [resp ((routes/approve-handler db {})
                  {:request-method :post :path-params {:id (str (random-uuid))} :body {}})]
        (is (= 409 (:status resp)))))))

(deftest enqueue-backlog-test
  (when-let [db fixtures/*test-db*]
    (let [resp ((routes/enqueue-backlog-handler db) {:request-method :post :body {:limit 2}})]
      (is (= 200 (:status resp)))
      (is (<= 0 (:enqueued (parse-body resp)) 2)))))

(deftest audit-endpoints-test
  (when-let [db fixtures/*test-db*]
    (testing "candidates list"
      (let [resp ((routes/audit-candidates-handler db) {:request-method :get :query-params {"limit" "5"}})
            body (parse-body resp)]
        (is (= 200 (:status resp)))
        (is (vector? (:candidates body)))
        (is (= 80 (:max-confidence body)))))
    (testing "invalid verdict is a 400, unknown id a 404"
      (is (= 400 (:status ((routes/audited-handler db)
                           {:request-method :post :path-params {:id (str (random-uuid))} :body {:verdict "maybe"}}))))
      (is (= 404 (:status ((routes/audited-handler db)
                           {:request-method :post :path-params {:id (str (random-uuid))} :body {:verdict "ok"}})))))))
