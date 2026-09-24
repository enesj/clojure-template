(ns app.domain.backend.expenses.services.utility-expenses-test
	(:require
		[app.domain.backend.expenses.services.articles :as articles]
		[app.domain.backend.expenses.services.expenses :as expenses]
		[app.domain.backend.expenses.services.subcategories :as subcategories]
		[app.domain.backend.expenses.services.utility-expenses :as sut]
		[clojure.test :refer [deftest is testing]])
	(:import
		[java.util UUID]))

(deftest expense-item-article-resolution-test
	(let [explicit-article-id (UUID/randomUUID)
				alias-article-id (UUID/randomUUID)]
		(testing "direct item article IDs are preserved when no alias is resolved"
			(is (= explicit-article-id
						(#'expenses/resolved-item-article-id {:article_id explicit-article-id} nil))))
		(testing "existing alias precedence remains unchanged"
			(is (= alias-article-id
						(#'expenses/resolved-item-article-id {:article_id explicit-article-id}
																								{:article_id alias-article-id}))))))

(deftest ensure-active-utility-subcategory-test
	(let [tenant-id (UUID/randomUUID)
				other-tenant-id (UUID/randomUUID)
				subcategory-id (UUID/randomUUID)]
		(testing "accepts active global Utilities subcategories"
			(with-redefs [subcategories/tenant-managed-subcategory-record
										(fn [_db id]
											(is (= subcategory-id id))
											{:id id
											 :tenant_id nil
											 :category_name "Utilities"
											 :name "Electricity"
											 :is_active true})]
				(is (= "Electricity"
							(:name (sut/ensure-active-utility-subcategory! :db tenant-id subcategory-id))))))

		(testing "accepts active tenant-local Utilities subcategories for the same tenant"
			(with-redefs [subcategories/tenant-managed-subcategory-record
										(fn [_db id]
											{:id id
											 :tenant_id tenant-id
											 :category_name "Utilities"
											 :name "District heating"
											 :is_active true})]
				(is (= tenant-id
							(:tenant_id (sut/ensure-active-utility-subcategory! :db tenant-id subcategory-id))))))

		(testing "rejects Subscriptions subcategories"
			(with-redefs [subcategories/tenant-managed-subcategory-record
										(fn [_db id]
											{:id id
											 :tenant_id nil
											 :category_name "Subscriptions"
											 :name "Internet"
											 :is_active true})]
				(try
					(sut/ensure-active-utility-subcategory! :db tenant-id subcategory-id)
					(is false "Expected validation error")
					(catch clojure.lang.ExceptionInfo e
						(is (= 400 (:status (ex-data e))))
						(is (= :subcategory_id (:field (ex-data e))))))))

		(testing "rejects disabled utility subcategories"
			(with-redefs [subcategories/tenant-managed-subcategory-record
										(fn [_db id]
											{:id id
											 :tenant_id nil
											 :category_name "Utilities"
											 :name "Old utility"
											 :is_active false})]
				(try
					(sut/ensure-active-utility-subcategory! :db tenant-id subcategory-id)
					(is false "Expected disabled error")
					(catch clojure.lang.ExceptionInfo e
						(is (= 400 (:status (ex-data e))))))))

		(testing "rejects tenant-local subcategories from another tenant"
			(with-redefs [subcategories/tenant-managed-subcategory-record
										(fn [_db id]
											{:id id
											 :tenant_id other-tenant-id
											 :category_name "Utilities"
											 :name "Private utility"
											 :is_active true})]
				(try
					(sut/ensure-active-utility-subcategory! :db tenant-id subcategory-id)
					(is false "Expected tenant isolation error")
					(catch clojure.lang.ExceptionInfo e
						(is (= 403 (:status (ex-data e))))))))))

(deftest ensure-utility-article-test
	(let [subcategory-id (UUID/randomUUID)
				article-id (UUID/randomUUID)
				subcategory {:id subcategory-id :name "Electricity"}]
		(testing "classifies a generated utility article when it has no subcategory"
			(let [updated (atom nil)]
				(with-redefs [articles/find-or-create-article-by-canonical-name!
											(fn [_db canonical-name unit]
												(is (= "Electricity bill" canonical-name))
												(is (= "kom" unit))
												{:id article-id
												 :canonical_name canonical-name
												 :subcategory_id nil})
											articles/update-article!
											(fn [_db id updates]
												(reset! updated [id updates])
												{:id id
												 :canonical_name "Electricity bill"
												 :subcategory_id (:subcategory_id updates)})]
					(is (= subcategory-id
								(:subcategory_id (sut/ensure-utility-article! :db subcategory nil))))
					(is (= [article-id {:subcategory_id subcategory-id}] @updated)))))

		(testing "rejects an existing utility article assigned to a different subcategory"
			(let [other-subcategory-id (UUID/randomUUID)]
				(with-redefs [articles/find-or-create-article-by-canonical-name!
											(fn [_db _canonical-name _unit]
												{:id article-id
												 :canonical_name "Electricity bill"
												 :subcategory_id other-subcategory-id})]
					(try
						(sut/ensure-utility-article! :db subcategory nil)
						(is false "Expected subcategory conflict")
						(catch clojure.lang.ExceptionInfo e
							(is (= 409 (:status (ex-data e)))))))))))