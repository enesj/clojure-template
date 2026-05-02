(ns app.domain.frontend.expenses.components.manual-expense-form.smart-input.render
  "Hook-free render tree for smart-expense-form."
  (:require
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.context-phase
     :refer [context-phase-view]]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.items-phase
     :refer [items-phase-view]]
    [uix.core :refer [$]]))

(defn render-smart-expense-form
  [{:keys [t ready? handle-submit error set-error! phase items context input-text input-ref
           article-mode? dropdown-open? highlight-idx type-picker-text creating? search-results
           quick-search-loading? cooccurring-pick-items focused-quick-pick-groups
           available-search-types items-total currency total-dropdown-count payer-name purchased-date
           set-payer-id! set-purchased-at! set-currency! handle-input-change handle-input-keydown
           set-expense-context-id! handle-select-result handle-create-inline handle-type-pick remove-context! update-item!
           remove-item! focus-input! set-type-picker-text! focus-item-id set-focus-item-id!
           submitting? submit-disabled? begin-context-phase! on-cancel context-suggestions currency-options
           payers payer-id purchased-at context-initial-sub-stage suppliers phase-two-stores
           expense-categories expense-contexts articles set-context-initial-sub-stage! set-phase!]}]
  (if-not ready?
    ($ :div {:class "flex flex-col items-center justify-center p-16 gap-4"}
      ($ :span {:class "ds-loading ds-loading-spinner ds-loading-lg text-primary"})
      ($ :p {:class "text-base-content/50 text-lg"} (t :smart-expense/loading)))

    ($ :form {:id "smart-expense-form"
              :on-submit handle-submit
              :class "space-y-6"}
      (when error
        ($ :div {:class "ds-alert ds-alert-error text-base flex items-center justify-between"}
          ($ :span error)
          ($ :button {:type "button"
                      :class "ds-btn ds-btn-ghost ds-btn-sm"
                      :on-click #(set-error! nil)}
            "×")))

      (when (= phase :items)
        ($ items-phase-view
          {:t t
           :items items
           :context context
           :input-text input-text
           :input-ref input-ref
           :article-mode? article-mode?
           :dropdown-open? dropdown-open?
           :highlight-idx highlight-idx
           :type-picker-text type-picker-text
           :creating? creating?
           :search-results search-results
           :quick-search-loading? quick-search-loading?
           :cooccurring-pick-items cooccurring-pick-items
           :focused-quick-pick-groups focused-quick-pick-groups
           :available-search-types available-search-types
           :items-total items-total
           :currency currency
           :total-dropdown-count total-dropdown-count
           :payer-name payer-name
           :purchased-date purchased-date
           :on-clear-payer #(set-payer-id! nil)
           :on-clear-date #(set-purchased-at! nil)
           :on-clear-currency #(set-currency! nil)
           :on-input-change handle-input-change
           :on-input-keydown handle-input-keydown
           :on-select-result handle-select-result
           :on-create-inline handle-create-inline
           :on-type-pick handle-type-pick
           :on-remove-context remove-context!
           :on-update-item update-item!
           :on-remove-item remove-item!
           :on-focus-input focus-input!
           :on-cancel-type-picker #(set-type-picker-text! nil)
           :on-set-error set-error!
           :focus-item-id focus-item-id
           :on-focus-handled #(set-focus-item-id! nil)}))

      (when (= phase :items)
        ($ :div {:class (str "sticky bottom-0 bg-white/95 backdrop-blur-sm "
                          "border-t border-base-200 pt-3 pb-3 -mx-6 px-6 "
                          "flex items-center gap-3")}
          ($ :button {:id "btn-add-store"
                      :type "button"
                      :class "ds-btn ds-btn-outline ds-btn-lg text-lg mr-auto"
                      :disabled (or submitting? (empty? items) (some? (:store context)))
                      :on-click (fn [e]
                                  (.preventDefault e)
                                  (begin-context-phase! :store-search))}
            (t :smart-expense/add-store))
          (when on-cancel
            ($ :button {:id "btn-cancel-smart-expense"
                        :type "button"
                        :class "ds-btn ds-btn-lg text-lg"
                        :disabled submitting?
                        :on-click (fn [e] (.preventDefault e) (on-cancel))}
              (t :smart-expense/cancel)))
          ($ :button {:id "btn-save-smart-expense"
                      :type "submit"
                      :class "ds-btn ds-btn-primary ds-btn-lg text-lg px-8"
                      :disabled (or submitting? submit-disabled? (empty? items))}
            (if submitting? (t :smart-expense/saving) (t :smart-expense/save)))))

      (when (= phase :context)
        ($ context-phase-view
          {:t t
           :items items
           :context context
           :input-text input-text
           :input-ref input-ref
           :dropdown-open? dropdown-open?
           :highlight-idx highlight-idx
           :type-picker-text type-picker-text
           :creating? creating?
           :search-results search-results
           :quick-search-loading? quick-search-loading?
           :context-suggestions context-suggestions
           :items-total items-total
           :currency currency
           :currency-options currency-options
           :payers payers
           :payer-id payer-id
           :purchased-at purchased-at
           :payer-name payer-name
           :purchased-date purchased-date
           :on-clear-payer #(set-payer-id! nil)
           :on-clear-date #(set-purchased-at! nil)
           :on-clear-currency #(set-currency! nil)
           :initial-sub-stage context-initial-sub-stage
           :suppliers suppliers
           :stores phase-two-stores
           :expense-categories expense-categories
           :expense-contexts expense-contexts
           :articles articles
           :on-input-change handle-input-change
           :on-input-keydown handle-input-keydown
           :on-select-result handle-select-result
           :on-create-inline handle-create-inline
           :on-type-pick handle-type-pick
           :on-remove-context remove-context!
           :on-set-phase (fn [next-phase]
                           (when (= next-phase :items)
                             (set-context-initial-sub-stage! nil))
                           (set-phase! next-phase))
           :on-focus-input focus-input!
           :on-set-payer-id set-payer-id!
           :on-set-purchased-at set-purchased-at!
           :on-set-currency set-currency!
           :on-set-expense-context-id set-expense-context-id!
           :on-cancel-type-picker #(set-type-picker-text! nil)}))

      (when (= phase :context)
        ($ :div {:class (str "sticky bottom-0 bg-white/95 backdrop-blur-sm "
                          "border-t border-base-200 pt-3 pb-3 -mx-6 px-6 "
                          "flex justify-end gap-3")}
          (when on-cancel
            ($ :button {:id "btn-cancel-smart-expense"
                        :type "button"
                        :class "ds-btn ds-btn-lg text-lg"
                        :disabled submitting?
                        :on-click (fn [e] (.preventDefault e) (on-cancel))}
              (t :smart-expense/cancel)))
          ($ :button {:id "btn-save-smart-expense"
                      :type "submit"
                      :class "ds-btn ds-btn-primary ds-btn-lg text-lg px-8"
                      :disabled (or submitting? submit-disabled?)}
            (if submitting? (t :smart-expense/saving) (t :smart-expense/save))))))))
