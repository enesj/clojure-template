(ns app.domain.backend.expenses.integrations.cerebras-test
	(:require
		[app.domain.backend.expenses.integrations.cerebras :as cerebras]
		[app.domain.backend.expenses.integrations.cerebras.http :as cerebras-http]
		[cheshire.core :as json]
		[clojure.test :refer [deftest is testing]]))

(deftest refine-request-includes-max-tokens
	(testing "refine-receipt-markdown! includes max_tokens when refine-max-tokens is configured"
		(let [captured (atom nil)
					cfg {:api-key "test-key"
							 :base-url "https://example.invalid/v1"
							 :model "qwen-3.8-27b"
							 :conn-timeout-ms 1
							 :socket-timeout-ms 1
							 :max-retries 0
							 :retry-sleep-ms 0
							 :refine-max-tokens 9999}
					response-content (json/generate-string
														 {"merchant" {"name" nil "address" nil "tax_id" nil}
															"purchased_at" nil
															"currency" nil
															"totals" {"subtotal_cents" nil "tax_cents" nil "total_cents" 100}
															"items" []})]
			(with-redefs [cerebras-http/http-post!
										(fn [url opts]
											(reset! captured {:url url :opts opts})
											{:status 200
											 :body (json/generate-string
															 {:id "chatcmpl-test"
																:model "qwen-3.8-27b"
																:choices [{:index 0
																					 :finish_reason "stop"
																					 :message {:role "assistant"
																										 :content response-content}}]})})]
				(cerebras/refine-receipt-markdown! cfg "# markdown")
				(let [sent-body (some-> @captured :opts :body (json/parse-string true))]
					(is (= 9999 (:max_tokens sent-body))))))))

(deftest refine-accepts-json-in-message-reasoning-when-content-missing
	(testing "Some providers return JSON in message.reasoning (without message.content); we still parse it"
		(let [cfg {:api-key "test-key"
					 :base-url "https://example.invalid/v1"
					 :model "qwen-3.8-27b"
					 :conn-timeout-ms 1
					 :socket-timeout-ms 1
					 :max-retries 0
					 :retry-sleep-ms 0
					 :refine-max-tokens 9999}
				response-content (json/generate-string
															 {"merchant" {"name" nil "address" nil "tax_id" nil}
																"purchased_at" nil
																"currency" nil
																"totals" {"subtotal_cents" nil "tax_cents" nil "total_cents" 100}
																"items" []})
				reasoning (str "Some analysis...\n```json\n" response-content "\n```\n")]
			(with-redefs [cerebras-http/http-post!
								(fn [_url _opts]
									{:status 200
									 :body (json/generate-string
												 {:id "chatcmpl-test"
													:model "qwen-3.8-27b"
													:choices [{:index 0
																 :finish_reason "length"
																 :message {:role "assistant"
																				 :reasoning reasoning}}]})})]
				(let [res (cerebras/refine-receipt-markdown! cfg "# markdown")]
					(is (= "cerebras" (:provider res)))
					(is (map? (:extraction res)))
					(is (= 100 (get-in res [:extraction-cents "totals" "total_cents"]))))))))