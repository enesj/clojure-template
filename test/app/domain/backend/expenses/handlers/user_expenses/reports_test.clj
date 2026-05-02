(ns app.domain.backend.expenses.handlers.user-expenses.reports-test
  (:require
    [app.domain.backend.expenses.handlers.user-expenses.reports :as reports]
    [app.domain.backend.expenses.services.user-expense-reports.filters :as report-filters]
    [app.domain.backend.expenses.services.user-expense-reports.time :as report-time]
    [cheshire.core :as json]
    [clojure.test :refer [deftest is testing]])
  (:import
    (java.time Instant)
    (java.util UUID)))

(defn- req
  [{:keys [user-id role tenant-id query-params]}]
  (cond-> {:query-params (or query-params {})}
    user-id (assoc :session {:auth-session {:user {:id user-id :role role}
                                            :tenant {:id tenant-id}}})))

(defn- parse-body [resp]
  (json/parse-string (:body resp) true))

(deftest reports-require-authentication
  (let [handler (reports/day-of-week-spending-handler nil)
        resp (handler {:query-params {}})]
    (is (= 401 (:status resp)))))

(deftest reports-forbidden-for-invalid-role
  (let [handler (reports/expense-size-distribution-handler nil)
        resp (handler (req {:user-id (UUID/randomUUID)
                            :tenant-id (UUID/randomUUID)
                            :role "guest"
                            :query-params {}}))]
    (is (= 403 (:status resp)))))

(deftest filter-options-validates-supplier-id
  (let [handler (reports/filter-options-handler nil)
        resp (handler (req {:user-id (UUID/randomUUID)
                            :tenant-id (UUID/randomUUID)
                            :role "member"
                            :query-params {:supplier_id "not-a-uuid"}}))
        body (parse-body resp)]
    (is (= 400 (:status resp)))
    (is (= "Invalid supplier_id" (:error body)))))

(deftest day-of-week-handler-parses-common-options
  (let [captured (atom nil)
        user-id (UUID/randomUUID)
        tenant-id (UUID/randomUUID)
        supplier-id (UUID/randomUUID)
        payer-id (UUID/randomUUID)
        expense-category-id (UUID/randomUUID)
        expense-context-id (UUID/randomUUID)
        from "2026-01-01"
        to "2026-01-31T23:59:59Z"
        handler (reports/day-of-week-spending-handler nil)]
    (with-redefs [report-time/day-of-week
                  (fn [_db passed-user-id opts]
                    (reset! captured {:user-id passed-user-id :opts opts})
                    [{:day_of_week 1
                      :currency "BAM"
                      :total_amount 10M
                      :expense_count 2}])]
      (let [resp (handler (req {:user-id user-id
                                :tenant-id tenant-id
                                :role "member"
                                :query-params {:from from
                                               :to to
                                               :currency "bam"
                                               :supplier_id (str supplier-id)
                                               :payer_id (str payer-id)
                                               :expense_category_id (str expense-category-id)
                                               :expense_context_id (str expense-context-id)}}))
            body (parse-body resp)
            row (first (:data body))]
        (testing "response shape"
          (is (= 200 (:status resp)))
          (is (= 1 (:day_of_week row)))
          (is (= "BAM" (:currency row))))

        (testing "parsed opts sent to service"
          (is (= user-id (:user-id @captured)))
          (is (= tenant-id (get-in @captured [:opts :tenant-id])))
          (is (instance? Instant (get-in @captured [:opts :from])))
          (is (instance? Instant (get-in @captured [:opts :to])))
          (is (= "BAM" (get-in @captured [:opts :currency])))
          (is (= supplier-id (get-in @captured [:opts :supplier-id])))
          (is (= payer-id (get-in @captured [:opts :payer-id])))
          (is (= expense-category-id (get-in @captured [:opts :expense-category-id])))
          (is (= expense-context-id (get-in @captured [:opts :expense-context-id]))))))))

(deftest reports-parse-missing-expense-context-filter
  (let [captured (atom nil)
        user-id (UUID/randomUUID)
        tenant-id (UUID/randomUUID)
        handler (reports/expense-size-distribution-handler nil)]
    (with-redefs [report-time/size-distribution
                  (fn [_db passed-user-id opts]
                    (reset! captured {:user-id passed-user-id :opts opts})
                    [])]
      (let [resp (handler (req {:user-id user-id
                                :tenant-id tenant-id
                                :role "member"
                                :query-params {:expense-context-missing "true"}}))]
        (is (= 200 (:status resp)))
        (is (= user-id (:user-id @captured)))
        (is (true? (get-in @captured [:opts :expense-context-missing?])))))))

(deftest filter-options-handler-returns-expected-data
  (let [captured (atom nil)
        user-id (UUID/randomUUID)
        tenant-id (UUID/randomUUID)
        handler (reports/filter-options-handler nil)]
    (with-redefs [report-filters/options
                  (fn [_db passed-user-id opts]
                    (reset! captured {:user-id passed-user-id :opts opts})
                    {:currencies ["BAM"]
                     :suppliers [{:id (UUID/randomUUID)
                                  :label "Konzum"}]})]
      (let [resp (handler (req {:user-id user-id
                                :tenant-id tenant-id
                                :role "member"
                                :query-params {:currency "bam"}}))
            body (parse-body resp)]
        (is (= 200 (:status resp)))
        (is (= ["BAM"] (get-in body [:data :currencies])))
        (is (= user-id (:user-id @captured)))
        (is (= tenant-id (get-in @captured [:opts :tenant-id])))
        (is (= "BAM" (get-in @captured [:opts :currency])))))))