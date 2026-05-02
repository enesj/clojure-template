(ns app.domain.frontend.expenses.components.user-expense-form.forms
  "UI building blocks for user-scoped expense forms (form body + receipt approval)."
  (:require
    [app.domain.frontend.expenses.components.form-fields.helpers :refer [current-datetime-local
                                                                         new-line-item]]
    [app.domain.frontend.expenses.components.user-expense-form.normalization :as norm]
    [app.domain.frontend.expenses.components.user-expense-form.specs :as specs]
    [app.domain.frontend.expenses.ui.currencies :as currency-ui]
    [app.template.frontend.i18n :as i18n]
    [app.template.frontend.components.form :refer [form-fields]]
    [app.template.frontend.components.form.base :as base]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui use-effect use-memo use-state]]
    [uix.re-frame :refer [use-subscribe]]))

(defui receipt-approval-form
  "Receipt approval form for user-scoped expenses with optional split layout.

  Props:
  - :receipt-id - The receipt ID
  - :receipt - The receipt data
  - :initial-data - Initial form values
  - :on-cancel - Callback when cancel is clicked
  - :on-expense-saved - Callback when expense is saved
  - :on-review-saved - Callback when receipt review is saved
  - :split-layout? - If true, render fields in right column and line items get separate container"
  [{:keys [receipt-id receipt initial-data on-cancel on-expense-saved on-review-saved
           split-layout?]}]
  (let [t (i18n/use-t)
        locale (or (use-subscribe [:locale]) :bs)
        suppliers (or (use-subscribe [:user-expenses/suppliers]) [])
        payers (or (use-subscribe [:user-expenses/payers]) [])
        expense-categories (or (use-subscribe [:user-expenses/expense-categories]) [])
        expense-contexts (or (use-subscribe [:user-expenses/expense-contexts]) [])
        profile (or (use-subscribe [:profile/data]) {})
        enabled-currencies (currency-ui/enabled-currency-options profile)
        form-error (use-subscribe [:user-expenses/form-error])
        [validation-error set-validation-error!] (use-state nil)
        full-entity-spec (use-memo
                           #(specs/get-expense-form-spec suppliers payers
                              {:receipt-approval? true
                               :locale locale
                               :supplier-guess (some-> receipt :supplier-guess)
                               :receipt receipt
                               :receipt-id receipt-id
                               :expense-categories expense-categories
                                 :expense-contexts expense-contexts
                               :enabled-currencies enabled-currencies})
                               [suppliers payers expense-categories expense-contexts enabled-currencies receipt receipt-id locale])
        fields-only-spec (use-memo
                           #(specs/get-expense-form-spec suppliers payers
                              {:receipt-approval? true
                               :locale locale
                               :supplier-guess (some-> receipt :supplier-guess)
                               :receipt receipt
                               :receipt-id receipt-id
                               :expense-categories expense-categories
                                 :expense-contexts expense-contexts
                               :enabled-currencies enabled-currencies
                               :exclude-line-items? true})
                               [suppliers payers expense-categories expense-contexts enabled-currencies receipt receipt-id locale])
        line-items-spec (use-memo
                          #(vector (specs/build-line-items-field-spec locale))
                          [locale])
        form-initial-values (use-memo
                              (fn []
                                (let [default-values {:currency (currency-ui/default-currency profile)
                                                      :purchased_at (current-datetime-local)
                                                      :items [(new-line-item)]}]
                                  (merge default-values initial-data)))
                              [initial-data profile])
        rid-str (or (some-> receipt-id str) "unknown")
        posted? (= "posted" (:status receipt))
        clear-errors! (fn [e]
                        (.preventDefault e)
                        (set-validation-error! nil)
                        (rf/dispatch [:user-expenses/clear-form-error]))]
    (use-effect
      (fn []
        (rf/dispatch [:user-expenses/fetch-suppliers {:limit 100 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-payers {:limit 100 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-expense-categories {:limit 500 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-expense-contexts {:limit 500 :offset 0}])
        (when-not (currency-ui/has-enabled-currencies? profile)
          (rf/dispatch [:profile/fetch]))
        js/undefined)
      [profile])

    ($ :div {:class "space-y-4"}
      (when (or validation-error form-error)
        ($ :div {:class "ds-alert ds-alert-error flex items-center justify-between"}
          ($ :span (or validation-error form-error))
          ($ :button {:id (str "btn-clear-receipt-approve-error-" rid-str)
                      :type "button"
                      :class "ds-btn ds-btn-ghost ds-btn-xs"
                      :on-click clear-errors!}
            "✕")))

      ($ base/initialize-form
        {:entity-name "user-expense"
         :entity-spec full-entity-spec
         :editing false
         :initial-values form-initial-values
         :prevent-default? true
         :keywordize-keys true
         :on-submit (fn [{:keys [values]}]
                      (let [validation-result (norm/validate-expense-values values)]
                        (if (:ok? validation-result)
                          (do
                            (set-validation-error! nil)
                            (if posted?
                              (rf/dispatch
                                [:user-expenses/update-posted-receipt
                                 receipt-id
                                 (norm/prepare-expense-submit-values values)
                                 on-expense-saved])
                              (rf/dispatch
                                [:user-expenses/approve-receipt
                                 receipt-id
                                 (norm/prepare-expense-submit-values values)
                                 on-expense-saved])))
                          (set-validation-error! (:error validation-result)))))
         :render-fn (fn [{:keys [form-id handle-submit submitting? values] :as form-props}]
                      (let [expense-valid-now? (:ok? (norm/validate-expense-values values))
                            receipt-valid-now? (:ok? (norm/validate-receipt-review-values values))
                            can-save-receipt? (and receipt-valid-now?
                                                (norm/receipt-review-changed? form-initial-values values))]
                        ($ :form {:id form-id
                                  :on-submit handle-submit}
                          ($ form-fields
                            (merge form-props
                              {:entity-name "user-expense"
                               :legend ""
                               :editing false
                               :values values
                               :form-id form-id
                               :entity-spec (if split-layout? fields-only-spec full-entity-spec)}))

                          ($ :div {:class "flex justify-end gap-2 mt-4"}
                            (when on-cancel
                              ($ :button {:id (str "btn-cancel-receipt-approve-" rid-str)
                                          :type "button"
                                          :class "ds-btn"
                                          :disabled submitting?
                                          :on-click (fn [e]
                                                      (.preventDefault e)
                                                      (when (fn? on-cancel) (on-cancel)))}
                                (t :common/cancel)))
                            (if posted?
                              ($ :button {:id (str "btn-update-posted-" rid-str)
                                          :type "submit"
                                          :class "ds-btn ds-btn-primary"
                                          :disabled (or submitting? (not expense-valid-now?))}
                                (t :common/update))
                              ($ :<>
                                ($ :button {:id (str "btn-save-receipt-" rid-str)
                                            :type "button"
                                            :class "ds-btn ds-btn-outline"
                                            :disabled (or submitting? (not can-save-receipt?))
                                            :on-click (fn [e]
                                                        (.preventDefault e)
                                                        (.stopPropagation e)
                                                        (let [validation-result (norm/validate-receipt-review-values values)]
                                                          (if (:ok? validation-result)
                                                            (do
                                                              (set-validation-error! nil)
                                                              (rf/dispatch
                                                                [:user-expenses/save-receipt-review
                                                                 receipt-id
                                                                 (norm/prepare-expense-submit-values values)
                                                                 on-review-saved]))
                                                            (set-validation-error! (:error validation-result)))))}
                                  (t :common/save-receipt))
                                ($ :button {:id (str "btn-save-expense-" rid-str)
                                            :type "submit"
                                            :class "ds-btn ds-btn-primary"
                                            :disabled (or submitting? (not expense-valid-now?))}
                                  (t :common/save-expense)))))

                          (when split-layout?
                            ($ :div {:class "mt-6 -mx-4 px-4 pt-4 border-t border-base-300"}
                              ($ form-fields
                                (merge form-props
                                  {:entity-name "user-expense"
                                   :legend ""
                                   :editing false
                                   :values values
                                   :form-id form-id
                                   :entity-spec line-items-spec})))))))}))))
