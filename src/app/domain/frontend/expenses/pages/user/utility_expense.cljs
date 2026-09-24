(ns app.domain.frontend.expenses.pages.user.utility-expense
	"Guided user workflow for adding utility bills as normal expenses."
	(:require
		[app.domain.frontend.expenses.shared.manual-entry.core :as manual-entry]
		[app.domain.frontend.expenses.ui.currencies :as currencies]
		[app.template.frontend.components.button :refer [button]]
		[app.template.frontend.i18n :refer [use-t]]
		[clojure.string :as str]
		[re-frame.core :as rf]
		[uix.core :refer [$ defui use-effect use-state]]
		[uix.re-frame :refer [use-subscribe]]))

(defn- today-string []
	(.slice (.toISOString (js/Date.)) 0 10))

(defn- row-id [row]
	(some-> (or (:id row) (:db/id row)) str))

(defn- row-label [row & ks]
	(or (some #(some-> (get row %) str str/trim not-empty) ks)
		(row-id row)
		""))

(defn- category-name [subcategory]
	(or (:category-name subcategory)
		(:category_name subcategory)
		(:subcategories/category_name subcategory)))

(defn- active-subcategory? [subcategory]
	(not (false? (or (:is-active subcategory)
								 (:is_active subcategory)
								 (:subcategories/is_active subcategory)
								 true))))

(defn- utilities-subcategories [subcategories]
	(->> subcategories
		(filter #(and (= "Utilities" (category-name %))
							 (active-subcategory? %)))
		(sort-by #(str/lower-case (row-label % :name :subcategories/name)))
		vec))

(defui select-field [{:keys [id label value on-change options placeholder required? disabled?]}]
	($ :div {:class "space-y-2"}
		($ :label {:for id :class "text-sm font-medium text-slate-700"}
			label
			(when required? ($ :span {:class "text-error ml-1"} "*")))
		($ :select {:id id
								:class "ds-select ds-select-bordered w-full"
								:value (or value "")
								:disabled disabled?
								:on-change #(on-change (-> % .-target .-value))}
			(when placeholder
				($ :option {:value ""} placeholder))
			(for [{:keys [value label]} options]
				($ :option {:key value :value value} label)))))

(defui input-field [{:keys [id label type value on-change placeholder required? min step]}]
	($ :div {:class "space-y-2"}
		($ :label {:for id :class "text-sm font-medium text-slate-700"}
			label
			(when required? ($ :span {:class "text-error ml-1"} "*")))
		($ :input {:id id
							 :class "ds-input ds-input-bordered w-full"
							 :type (or type "text")
							 :value (or value "")
							 :placeholder placeholder
							 :min min
							 :step step
							 :on-change #(on-change (-> % .-target .-value))})))

(defui utility-expense-page []
	(let [t (use-t)
				subcategories (or (use-subscribe [:app.template.frontend.subs.entity/entities :subcategories]) [])
				utility-kinds (utilities-subcategories subcategories)
				suppliers (or (use-subscribe [:user-expenses/suppliers]) [])
				payers (or (use-subscribe [:user-expenses/payers]) [])
				user-payer-id (use-subscribe [:user-expenses/user-payer-id])
				expense-contexts (or (use-subscribe [:user-expenses/expense-contexts]) [])
				profile (or (use-subscribe [:profile/data]) {})
				currency-options (currencies/enabled-currency-options profile)
				form-loading? (boolean (use-subscribe [:user-expenses/form-loading?]))
				form-error (use-subscribe [:user-expenses/form-error])
				[utility-kind-id set-utility-kind-id!] (use-state "")
				[supplier-id set-supplier-id!] (use-state "")
				[payer-id set-payer-id!] (use-state "")
				[expense-context-id set-expense-context-id!] (use-state "")
				[purchased-at set-purchased-at!] (use-state (today-string))
				[amount set-amount!] (use-state "")
				[currency set-currency!] (use-state "")
				[notes set-notes!] (use-state "")
				utility-options (mapv (fn [subcategory]
																{:value (row-id subcategory)
																 :label (row-label subcategory :name :subcategories/name)})
													utility-kinds)
				supplier-options (mapv (fn [supplier]
																 {:value (row-id supplier)
																	:label (row-label supplier :display-name :display_name :name :suppliers/display_name)})
													 suppliers)
				payer-options (mapv (fn [payer]
															{:value (row-id payer)
													 :label (row-label payer :label :payers/label :display-name :display_name :name :payers/display_name)})
												payers)
				context-options (mapv (fn [context]
																{:value (row-id context)
																 :label (row-label context :name :expense-contexts/name)})
													expense-contexts)
				selected-currency (or (some-> currency str str/trim not-empty)
														(currencies/default-currency profile)
														"BAM")
				can-submit? (and (not form-loading?)
											(seq utility-kind-id)
											(seq payer-id)
											(seq purchased-at)
											(seq amount))
				submit! (fn [event]
									(.preventDefault event)
									(when can-submit?
										(rf/dispatch
											[:user-expenses/create-utility-expense
											 (cond-> {:utility-subcategory-id utility-kind-id
																:payer-id payer-id
																:purchased-at purchased-at
																:amount amount
																:currency selected-currency}
												 (seq supplier-id) (assoc :supplier-id supplier-id)
												 (seq expense-context-id) (assoc :expense-context-id expense-context-id)
												 (seq notes) (assoc :notes notes))
											 #(rf/dispatch [:navigate-to "/expenses/list"])])))]
		(use-effect
			(fn []
				(rf/dispatch [:user-expenses/fetch-subcategories {:managed-taxonomy-only true
																													:limit 100
																													:offset 0}])
				(rf/dispatch [:user-expenses/fetch-suppliers {:limit 500 :offset 0}])
				(rf/dispatch [:user-expenses/fetch-payers {:limit 500 :offset 0}])
				(rf/dispatch [:user-expenses/fetch-expense-contexts {:limit 500 :offset 0}])
				(rf/dispatch [:profile/fetch])
				js/undefined)
			[])
		(use-effect
			(fn []
				(when (str/blank? utility-kind-id)
					(when-let [default-id (some-> (first utility-kinds) row-id)]
						(set-utility-kind-id! default-id)))
				js/undefined)
			[utility-kinds utility-kind-id])
		(use-effect
			(fn []
				(when (str/blank? payer-id)
					(when-let [default-id (or (some-> (manual-entry/payer-default-id payers user-payer-id) str)
																	(some-> (first payers) row-id))]
						(set-payer-id! default-id)))
				js/undefined)
			[payers payer-id user-payer-id])
		(use-effect
			(fn []
				(when (str/blank? currency)
					(set-currency! (currencies/default-currency profile)))
				js/undefined)
			[profile currency])
		($ :div {:class "min-h-screen bg-slate-50"}
			($ :header {:class "bg-white border-b border-slate-200"}
				($ :div {:class "max-w-4xl mx-auto px-4 py-6"}
					($ :div {:class "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4"}
						($ :div
							($ :p {:class "text-sm font-medium text-slate-500"} (t :utility-expense/eyebrow))
							($ :h1 {:class "text-2xl font-bold text-slate-900"} (t :utility-expense/title))
							($ :p {:class "text-sm text-slate-600 mt-1"} (t :utility-expense/subtitle)))
						($ button {:id "btn-cancel-utility-expense"
											 :btn-type :ghost
											 :on-click #(rf/dispatch [:navigate-to "/expenses/list"])}
							(t :utility-expense/back-to-expenses)))))
			($ :main {:class "max-w-4xl mx-auto px-4 py-6"}
				($ :form {:id "utility-expense-form"
									:class "bg-white border border-slate-200 rounded-2xl shadow-sm p-5 sm:p-6 space-y-6"
									:on-submit submit!}
					(when form-error
						($ :div {:id "utility-expense-form-error"
										 :class "bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm"}
							form-error))
					(when-not (seq utility-options)
						($ :div {:id "utility-expense-no-kinds"
										 :class "bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-lg text-sm"}
							(t :utility-expense/no-utility-kinds)))
					($ :section {:class "space-y-4"}
						($ :div
							($ :h2 {:class "text-lg font-semibold text-slate-900"} (t :utility-expense/bill-section))
							($ :p {:class "text-sm text-slate-500"} (t :utility-expense/bill-section-help)))
						($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
							($ select-field {:id "utility-expense-kind"
															 :label (t :utility-expense/kind-label)
															 :value utility-kind-id
															 :on-change set-utility-kind-id!
															 :options utility-options
															 :placeholder (t :utility-expense/kind-placeholder)
															 :required? true
															 :disabled? (not (seq utility-options))})
							($ input-field {:id "utility-expense-amount"
															:label (t :utility-expense/amount-label)
															:type "number"
															:value amount
															:on-change set-amount!
															:placeholder "0.00"
															:min "0.01"
															:step "0.01"
															:required? true})
							($ input-field {:id "utility-expense-date"
															:label (t :utility-expense/date-label)
															:type "date"
															:value purchased-at
															:on-change set-purchased-at!
															:required? true})
							($ select-field {:id "utility-expense-currency"
															 :label (t :utility-expense/currency-label)
															 :value selected-currency
															 :on-change set-currency!
															 :options currency-options
															 :required? true})))
					($ :section {:class "space-y-4"}
						($ :div
							($ :h2 {:class "text-lg font-semibold text-slate-900"} (t :utility-expense/context-section))
							($ :p {:class "text-sm text-slate-500"} (t :utility-expense/context-section-help)))
						($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
							($ select-field {:id "utility-expense-payer"
															 :label (t :utility-expense/payer-label)
															 :value payer-id
															 :on-change set-payer-id!
															 :options payer-options
															 :placeholder (t :utility-expense/payer-placeholder)
															 :required? true})
							($ select-field {:id "utility-expense-supplier"
															 :label (t :utility-expense/supplier-label)
															 :value supplier-id
															 :on-change set-supplier-id!
															 :options supplier-options
															 :placeholder (t :utility-expense/supplier-placeholder)})
							($ select-field {:id "utility-expense-context"
															 :label (t :utility-expense/context-label)
															 :value expense-context-id
															 :on-change set-expense-context-id!
															 :options context-options
															 :placeholder (t :utility-expense/context-placeholder)})
							($ :div {:class "space-y-2 md:col-span-2"}
								($ :label {:for "utility-expense-notes"
													 :class "text-sm font-medium text-slate-700"}
									(t :utility-expense/notes-label))
								($ :textarea {:id "utility-expense-notes"
															:class "ds-textarea ds-textarea-bordered w-full min-h-24"
															:value notes
															:placeholder (t :utility-expense/notes-placeholder)
															:on-change #(set-notes! (-> % .-target .-value))}))))
					($ :div {:class "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pt-2 border-t border-slate-100"}
						($ :p {:class "text-xs text-slate-500"} (t :utility-expense/storage-note))
						($ :div {:class "flex gap-2 justify-end"}
							($ button {:id "btn-cancel-utility-expense-bottom"
												 :btn-type :outline
												 :on-click #(rf/dispatch [:navigate-to "/expenses/list"])}
								(t :utility-expense/cancel))
							($ button {:id "btn-save-utility-expense"
												 :btn-type :primary
												 :type "submit"
												 :disabled (not can-submit?)
												 :loading form-loading?}
								(if form-loading?
									(t :utility-expense/saving)
									(t :utility-expense/save))))))))))