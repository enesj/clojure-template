(ns app.domain.backend.expenses.services.utility-expenses
	"Guided utility bill entry service.

	This service is intentionally a workflow helper: it creates normal expenses and
	normal expense items while pre-filling utility-specific classification."
	(:require
		[app.domain.backend.expenses.services.articles :as articles]
		[app.domain.backend.expenses.services.subcategories :as subcategories]
		[app.domain.backend.expenses.services.user-expenses :as user-expenses]
		[clojure.string :as str]
		[honey.sql :as sql]
		[next.jdbc :as jdbc]
		[next.jdbc.result-set :as rs])
	(:import
		[java.util UUID]))

(def utility-article-category-name "Utilities")
(def utility-expense-category-name "Utilities")

(defn- same-id?
	[a b]
	(= (some-> a str) (some-> b str)))

(defn- blank->nil
	[value]
	(if (and (string? value) (str/blank? value))
		nil
		value))

(defn- positive-amount!
	[amount]
	(let [amount* (cond
									(instance? java.math.BigDecimal amount) amount
									(number? amount) (bigdec amount)
									(string? amount) (try
																		 (bigdec (str/trim amount))
																		 (catch Exception _ nil))
									:else nil)]
		(when-not (and amount* (pos? (.signum ^java.math.BigDecimal amount*)))
			(throw (ex-info "Utility bill amount must be greater than 0"
							 {:status 400
								:field :amount
								:amount amount})))
		amount*))

(defn ensure-active-utility-subcategory!
	"Return a visible active Utilities subcategory for this tenant or throw."
	[db tenant-id subcategory-id]
	(when-not subcategory-id
		(throw (ex-info "Utility kind is required"
						 {:status 400
							:field :subcategory_id})))
	(let [subcategory (subcategories/tenant-managed-subcategory-record db subcategory-id)]
		(cond
			(nil? subcategory)
			(throw (ex-info "Utility kind not found"
							 {:status 404
								:field :subcategory_id
								:subcategory-id subcategory-id}))

			(and (:tenant_id subcategory)
				(not (same-id? tenant-id (:tenant_id subcategory))))
			(throw (ex-info "Utility kind is not available in this tenant"
							 {:status 403
								:field :subcategory_id
								:tenant-id tenant-id
								:subcategory-id subcategory-id}))

			(not= utility-article-category-name (:category_name subcategory))
			(throw (ex-info "Utility kind must belong to the Utilities article category"
							 {:status 400
								:field :subcategory_id
								:category-name (:category_name subcategory)
								:subcategory-id subcategory-id}))

			(false? (:is_active subcategory))
			(throw (ex-info "Disabled utility kinds cannot be selected for new expenses"
							 {:status 400
								:field :subcategory_id
								:subcategory-id subcategory-id}))

			:else subcategory)))

(defn- find-utility-expense-category
	[db tenant-id]
	(jdbc/execute-one!
		db
		(sql/format {:select [:id :name]
								 :from [:expense_categories]
								 :where [:and
												 [:= :tenant_id tenant-id]
												 [:= [:lower :name] (str/lower-case utility-expense-category-name)]]
								 :limit 1})
		{:builder-fn rs/as-unqualified-lower-maps}))

(defn ensure-utility-expense-category!
	"Return the tenant expense category used for utility bills, creating it when missing."
	[db tenant-id]
	(or (find-utility-expense-category db tenant-id)
		(jdbc/execute-one!
			db
			(sql/format {:insert-into :expense_categories
									 :values [{:id (UUID/randomUUID)
														 :tenant_id tenant-id
														 :name utility-expense-category-name
														 :is_default false}]
									 :returning [:id :name]})
			{:builder-fn rs/as-unqualified-lower-maps})
		(find-utility-expense-category db tenant-id)))

(defn utility-article-name
	[subcategory]
	(str (some-> (:name subcategory) str str/trim) " bill"))

(defn ensure-utility-article!
	"Return a canonical article classified under the selected utility subcategory."
	[db subcategory article-name]
	(let [subcategory-id (:id subcategory)
				canonical-name (or (some-> article-name blank->nil str str/trim)
												(utility-article-name subcategory))
				article (articles/find-or-create-article-by-canonical-name! db canonical-name "kom")
				current-subcategory-id (:subcategory_id article)]
		(cond
			(nil? current-subcategory-id)
			(articles/update-article! db (:id article) {:subcategory_id subcategory-id})

			(same-id? current-subcategory-id subcategory-id)
			article

			:else
			(throw (ex-info "Utility article already belongs to another subcategory"
							 {:status 409
								:field :article_name
								:article-id (:id article)
								:article-name canonical-name
								:current-subcategory-id current-subcategory-id
								:requested-subcategory-id subcategory-id})))))

(defn create-utility-expense!
	"Create a standard user expense from guided utility bill input."
	([db tenant-id user-id payload]
	 (create-utility-expense! db tenant-id user-id payload nil))
	([db tenant-id user-id {:keys [subcategory-id supplier-id payer-id expense-context-id
																 purchased-at amount currency notes article-name]}
		app-config]
	 (when-not tenant-id
		 (throw (ex-info "tenant-id is required" {:status 400})))
	 (when-not user-id
		 (throw (ex-info "user-id is required" {:status 400})))
	 (when-not payer-id
		 (throw (ex-info "Payer is required" {:status 400 :field :payer_id})))
	 (when-not purchased-at
		 (throw (ex-info "Paid date is required" {:status 400 :field :purchased_at})))
	 (let [amount* (positive-amount! amount)]
		 (jdbc/with-transaction [tx db]
			 (let [subcategory (ensure-active-utility-subcategory! tx tenant-id subcategory-id)
						 expense-category (ensure-utility-expense-category! tx tenant-id)
						 article (ensure-utility-article! tx subcategory article-name)
						 expense-data (cond-> {:payer_id payer-id
																	 :expense_category_id (:id expense-category)
																	 :purchased_at purchased-at
																	 :total_amount amount*
																	 :currency (or (some-> currency str str/trim str/upper-case not-empty) "BAM")}
														supplier-id (assoc :supplier_id supplier-id)
														expense-context-id (assoc :expense_context_id expense-context-id)
														(some-> notes blank->nil) (assoc :notes notes))
						 item {:article_id (:id article)
									 :raw_label (:canonical_name article)
									 :qty 1M
									 :unit "kom"
									 :unit_price amount*
									 :line_total amount*}]
				 (user-expenses/create-user-expense! tx tenant-id user-id expense-data [item] app-config))))))