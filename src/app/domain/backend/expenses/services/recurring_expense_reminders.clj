(ns app.domain.backend.expenses.services.recurring-expense-reminders
	"Recurring expense reminder CRUD services using factory pattern."
	(:require
		[app.domain.backend.expenses.services.expense-template-lines :as expense-template-lines]
		[app.domain.backend.expenses.services.expense-templates :as expense-templates]
		[app.domain.backend.expenses.services.service-configs :as configs]
		[app.domain.backend.expenses.services.services-factory :as factory]
		[clojure.string :as str]
		[honey.sql :as sql]
		[next.jdbc :as jdbc]
		[next.jdbc.result-set :as rs])
	(:import
		[java.sql Date]
		[java.time Instant LocalDate ZoneOffset]))

(def config (configs/get-entity-config :recurring-expense-reminder))

(def service (factory/build-entity-service config))

(defn list-recurring-expense-reminders
	[db opts]
	((:list service) db opts))

(defn count-recurring-expense-reminders
	[db opts]
	((:count service) db opts))

(defn create-recurring-expense-reminder!
	[db data]
	((:create! service) db data))

(defn update-recurring-expense-reminder!
	[db recurring-expense-reminder-id updates]
	((:update! service) db recurring-expense-reminder-id updates))

(defn delete-recurring-expense-reminder!
	[db recurring-expense-reminder-id]
	((:delete! service) db recurring-expense-reminder-id))

(defn ->local-date
	"Coerce supported date representations to `java.time.LocalDate`."
	[value]
	(cond
		(nil? value) nil
		(instance? LocalDate value) value
		(instance? Date value) (.toLocalDate ^Date value)
		(instance? java.util.Date value) (-> ^java.util.Date value .toInstant (.atZone ZoneOffset/UTC) .toLocalDate)
		(string? value) (try
									(LocalDate/parse (str/trim value))
									(catch Exception _ nil))
		:else (try
					(-> value str str/trim LocalDate/parse)
					(catch Exception _ nil))))

(defn- ->positive-int
	[value fallback]
	(let [parsed (cond
							 (integer? value) value
							 (number? value) (int value)
							 (string? value) (try (Integer/parseInt (str/trim value))
																 (catch Exception _ fallback))
							 :else fallback)]
		(if (pos? parsed) parsed fallback)))

(defn next-due-date
	"Return the next due date after `due-date` for a recurrence frequency.

	`custom` currently means every N days. Unknown or missing frequencies return nil."
	[due-date frequency interval]
	(when-let [due (->local-date due-date)]
		(let [n (->positive-int interval 1)
					freq (some-> frequency str str/trim str/lower-case)]
			(case freq
				"weekly" (.plusWeeks due n)
				"monthly" (.plusMonths due n)
				"quarterly" (.plusMonths due (* 3 n))
				"yearly" (.plusYears due n)
				"custom" (.plusDays due n)
				nil))))

(defn- reminder-window-date
	[today reminder-days-before]
	(.plusDays (or (->local-date today) (LocalDate/now ZoneOffset/UTC))
		(->positive-int reminder-days-before 0)))

(defn due-for-reminder?
	"True when a template's due date falls within today's reminder window."
	([template]
	 (due-for-reminder? template (LocalDate/now ZoneOffset/UTC)))
	([template today]
	 (let [due (->local-date (:next_due_date template))
				window (reminder-window-date today (:reminder_days_before template))]
		(and due (not (.isAfter due window))))))

(defn- active-recurring-template-filters
	[]
	[[:= :status "active"]
	 [:is-not :recurrence_frequency nil]
	 [:is-not :next_due_date nil]])

(defn due-templates
	"List active recurring templates due for reminder generation in `tenant-id`."
	([db tenant-id]
	 (due-templates db tenant-id {}))
	([db tenant-id {:keys [today limit offset]
							:or {limit 1000 offset 0}}]
	 (->> (expense-templates/list-expense-templates
				 db
				 (cond-> {:limit limit
								 :offset offset
								 :extra-filters (active-recurring-template-filters)}
					tenant-id (assoc :tenant-id tenant-id)))
		 (filter #(due-for-reminder? % today))
		 vec)))

(defn- find-existing-reminder
	[db template-id due-date]
	(first (list-recurring-expense-reminders
				 db
				 {:limit 1
					:offset 0
					:extra-filters [[:= :template_id template-id]
													[:= :due_date due-date]]})))

(defn- ensure-reminder!
	[db template-id due-date]
	(if-let [existing (find-existing-reminder db template-id due-date)]
		{:reminder existing
		 :created? false}
		{:reminder (create-recurring-expense-reminder!
							 db
							 {:template_id template-id
								:due_date due-date
								:status "pending"})
		 :created? true}))

(defn generate-due-reminders!
	"Generate reminder rows for due recurring templates and advance template dates.

	Returns `{:generated-count n :reminders [...] :templates-advanced-count n}`."
	([db tenant-id]
	 (generate-due-reminders! db tenant-id {}))
	([db tenant-id {:keys [today] :as opts}]
	 (let [today* (or (->local-date today) (LocalDate/now ZoneOffset/UTC))]
		(jdbc/transact db
			(fn [tx]
				(let [templates (due-templates tx tenant-id (assoc opts :today today*))]
					(loop [[template & more] templates
							 reminders []
							 generated 0
							 advanced 0]
						(if-not template
							{:generated-count generated
							 :reminders reminders
							 :templates-advanced-count advanced}
							(let [template-id (:id template)
										due-date (->local-date (:next_due_date template))
										{:keys [reminder created?]} (ensure-reminder! tx template-id due-date)
										next-date (next-due-date due-date
																	 (:recurrence_frequency template)
																	 (:recurrence_interval template))]
								(when next-date
									((:update! expense-templates/service)
									 tx
									 template-id
									 {:next_due_date next-date}
									 (cond-> {}
										tenant-id (assoc :tenant-id tenant-id))))
								(recur more
									(conj reminders reminder)
									(cond-> generated created? inc)
									(cond-> advanced next-date inc)))))))))))

(def ^:private reminder-select-fields
	[[:r.id :id]
	 [:r.template_id :template_id]
	 [:r.due_date :due_date]
	 [:r.status :status]
	 [:r.reminded_at :reminded_at]
	 [:r.snoozed_until :snoozed_until]
	 [:r.recorded_expense_id :recorded_expense_id]
	 [:r.skipped_at :skipped_at]
	 [:r.created_at :created_at]
	 [:r.updated_at :updated_at]
	 [:t.tenant_id :tenant_id]
	 [:t.name :template_name]
	 [:t.kind :template_kind]
	 [:t.default_expense_category_id :default_expense_category_id]
	 [:t.default_expense_context_id :default_expense_context_id]
	 [:t.default_supplier_id :default_supplier_id]
	 [:t.default_payer_id :default_payer_id]
	 [:t.default_currency :default_currency]
	 [:t.default_notes :default_notes]])

(defn- reminder-base-query
	[tenant-id]
	{:select reminder-select-fields
	 :from [[:recurring_expense_reminders :r]]
	 :join [[:expense_templates :t] [:= :t.id :r.template_id]]
	 :where [:and [:= :t.tenant_id tenant-id]]})

(defn- merge-where
	[query clause]
	(update query :where (fn [existing]
										(if existing
											[:and existing clause]
											clause))))

(defn get-tenant-reminder
	[db tenant-id reminder-id]
	(when (and tenant-id reminder-id)
		(jdbc/execute-one!
			db
			(sql/format (-> (reminder-base-query tenant-id)
										(merge-where [:= :r.id reminder-id])))
			{:builder-fn rs/as-unqualified-lower-maps})))

(defn list-tenant-reminders
	"List reminders for a tenant.

	Options:
	- `:status` filters by one status value
	- `:actionable?` returns pending reminders plus snoozed reminders whose snooze has elapsed"
	[db tenant-id {:keys [status actionable? limit offset]
						 :or {limit 50 offset 0}}]
	(let [now (Instant/now)
			query (cond-> (reminder-base-query tenant-id)
					 status (merge-where [:= :r.status (str status)])
					 actionable? (merge-where [:or
															 [:= :r.status "pending"]
															 [:and [:= :r.status "snoozed"]
																[:<= :r.snoozed_until now]]])
					 true (assoc :order-by [[:r.due_date :asc] [:r.created_at :asc]]
								 :limit limit
								 :offset offset))]
		(jdbc/execute! db (sql/format query) {:builder-fn rs/as-unqualified-lower-maps})))

(defn reminder-template-lines
	[db template-id]
	(expense-template-lines/list-expense-template-lines
		db
		{:limit 200
		 :offset 0
		 :extra-filters [[:= :template_id template-id]
									[:= :is_active true]]
		 :order-by :sort-order
		 :order-dir :asc}))

(defn reminder-prefill
	"Return an editable expense prefill payload for a reminder."
	[db tenant-id reminder-id]
	(when-let [reminder (get-tenant-reminder db tenant-id reminder-id)]
		(let [lines (reminder-template-lines db (:template_id reminder))
				items (mapv (fn [line]
									 (cond-> {:raw_label (:label line)}
										(:article_id line) (assoc :article_id (:article_id line))
										(:default_amount line) (assoc :line_total (:default_amount line))))
								 lines)
				total (reduce + 0M (keep :line_total items))]
			{:reminder reminder
			 :expense (cond-> {:supplier_id (:default_supplier_id reminder)
										:payer_id (:default_payer_id reminder)
										:expense_category_id (:default_expense_category_id reminder)
										:expense_context_id (:default_expense_context_id reminder)
										:purchased_at (some-> (:due_date reminder) str)
										:currency (:default_currency reminder)
										:notes (:default_notes reminder)
										:items items}
							(pos? total) (assoc :total_amount total))})))

(defn skip-reminder!
	[db tenant-id reminder-id]
	(when (get-tenant-reminder db tenant-id reminder-id)
		(update-recurring-expense-reminder!
			db
			reminder-id
			{:status "skipped"
			 :skipped_at (Instant/now)})))

(defn snooze-reminder!
	[db tenant-id reminder-id snoozed-until]
	(when (get-tenant-reminder db tenant-id reminder-id)
		(update-recurring-expense-reminder!
			db
			reminder-id
			{:status "snoozed"
			 :snoozed_until snoozed-until})))

(defn record-reminder!
	[db tenant-id reminder-id recorded-expense-id]
	(when (get-tenant-reminder db tenant-id reminder-id)
		(update-recurring-expense-reminder!
			db
			reminder-id
			{:status "recorded"
			 :recorded_expense_id recorded-expense-id})))