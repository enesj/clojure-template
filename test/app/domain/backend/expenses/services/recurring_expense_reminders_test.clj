(ns app.domain.backend.expenses.services.recurring-expense-reminders-test
	(:require
		[app.domain.backend.expenses.services.recurring-expense-reminders :as sut]
		[clojure.test :refer [deftest is testing]])
	(:import
		[java.time Instant LocalDate]
		[java.util UUID]))

(deftest next-due-date-test
	(let [due (LocalDate/of 2026 1 31)]
		(testing "standard recurrence frequencies"
			(is (= (LocalDate/of 2026 2 7)
						(sut/next-due-date due "weekly" 1)))
			(is (= (LocalDate/of 2026 2 28)
						(sut/next-due-date due "monthly" 1)))
			(is (= (LocalDate/of 2026 7 31)
						(sut/next-due-date due "quarterly" 2)))
			(is (= (LocalDate/of 2027 1 31)
						(sut/next-due-date due "yearly" 1))))
		(testing "custom recurrence uses days and normalizes invalid intervals"
			(is (= (LocalDate/of 2026 2 14)
						(sut/next-due-date due "custom" 14)))
			(is (= (LocalDate/of 2026 2 1)
						(sut/next-due-date due "custom" 0))))
		(testing "unknown or missing inputs do not produce a next date"
			(is (nil? (sut/next-due-date due "sometimes" 1)))
			(is (nil? (sut/next-due-date nil "monthly" 1))))))

(deftest due-for-reminder-test
	(let [today (LocalDate/of 2026 4 10)]
		(testing "due inside the reminder window"
			(is (true? (sut/due-for-reminder? {:next_due_date (LocalDate/of 2026 4 15)
																				 :reminder_days_before 5}
																			today))))
		(testing "not due beyond the reminder window"
			(is (false? (sut/due-for-reminder? {:next_due_date (LocalDate/of 2026 4 16)
																					:reminder_days_before 5}
																			 today))))
		(testing "missing dates are never due"
			(is (nil? (sut/due-for-reminder? {:reminder_days_before 5} today))))))

(deftest reminder-prefill-test
	(let [tenant-id (UUID/randomUUID)
				reminder-id (UUID/randomUUID)
				template-id (UUID/randomUUID)
				supplier-id (UUID/randomUUID)
				context-id (UUID/randomUUID)]
		(with-redefs [sut/get-tenant-reminder
									(fn [_db t-id r-id]
										(is (= tenant-id t-id))
										(is (= reminder-id r-id))
										{:id reminder-id
										 :template_id template-id
										 :due_date (LocalDate/of 2026 5 1)
										 :template_name "May utilities"
										 :default_supplier_id supplier-id
										 :default_expense_context_id context-id
										 :default_currency "EUR"
										 :default_notes "Monthly utilities"})
									sut/reminder-template-lines
									(fn [_db t-id]
										(is (= template-id t-id))
										[{:label "Electricity" :default_amount 42.50M}
										 {:label "Water" :default_amount 18.25M}])]
			(let [prefill (sut/reminder-prefill :db tenant-id reminder-id)]
				(is (= reminder-id (get-in prefill [:reminder :id])))
				(is (= supplier-id (get-in prefill [:expense :supplier_id])))
				(is (= context-id (get-in prefill [:expense :expense_context_id])))
				(is (= "2026-05-01" (get-in prefill [:expense :purchased_at])))
				(is (= 60.75M (get-in prefill [:expense :total_amount])))
				(is (= [{:raw_label "Electricity" :line_total 42.50M}
								{:raw_label "Water" :line_total 18.25M}]
							(get-in prefill [:expense :items])))))))

(deftest reminder-status-actions-test
	(let [tenant-id (UUID/randomUUID)
				reminder-id (UUID/randomUUID)
				expense-id (UUID/randomUUID)
				updates (atom [])]
		(with-redefs [sut/get-tenant-reminder (fn [_db t-id r-id]
																						(when (and (= tenant-id t-id)
																										(= reminder-id r-id))
																							{:id reminder-id}))
									sut/update-recurring-expense-reminder! (fn [_db r-id update-map]
																													 (swap! updates conj [r-id update-map])
																													 update-map)]
			(testing "skip sets skipped status and timestamp"
				(let [result (sut/skip-reminder! :db tenant-id reminder-id)]
					(is (= "skipped" (:status result)))
					(is (instance? Instant (:skipped_at result)))))
			(testing "snooze sets snoozed status and until instant"
				(let [until (Instant/parse "2026-05-02T10:00:00Z")
							result (sut/snooze-reminder! :db tenant-id reminder-id until)]
					(is (= {:status "snoozed" :snoozed_until until} result))))
			(testing "record stores the created expense id"
				(let [result (sut/record-reminder! :db tenant-id reminder-id expense-id)]
					(is (= {:status "recorded" :recorded_expense_id expense-id} result))))
			(testing "missing reminder does not update"
				(reset! updates [])
				(is (nil? (sut/skip-reminder! :db tenant-id (UUID/randomUUID))))
				(is (empty? @updates))))))