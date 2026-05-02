(ns app.domain.frontend.expenses.components.user-expense-form.modals
  "Modal wrappers for user-scoped expense forms."
  (:require
    [app.domain.frontend.expenses.components.form-fields.helpers :refer [current-datetime-local
                                                                         new-line-item]]
    [app.domain.frontend.expenses.components.manual-expense-form.core :as manual-form]
    [app.domain.frontend.expenses.shared.manual-entry.core :as manual-entry]
    [app.domain.frontend.expenses.components.user-expense-form.forms :as forms]
    [app.domain.frontend.expenses.components.user-expense-form.normalization :as norm]
    [app.domain.frontend.expenses.components.user-expense-form.specs :as specs]
    [app.domain.frontend.expenses.ui.currencies :as currency-ui]
    [app.template.frontend.i18n :as i18n]
    [app.template.frontend.components.form.master-detail :refer [master-detail-form]]
    [clojure.string :as str]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui use-effect use-memo use-state]]
    [uix.re-frame :refer [use-subscribe]]))

(def default-payer-id manual-entry/payer-default-id)

(defn- expense-category-default?
  [category]
  (boolean
    (or (:is-default category)
      (:isDefault category))))

(defn- default-expense-category-id
  [expense-categories _settings]
  (some->> (or expense-categories [])
    (some (fn [category]
            (when (expense-category-default? category)
              (:id category))))
    str
    str/trim
    not-empty))

(defui user-expense-add-form-modal
  [{:keys [receipt-id receipt on-success on-review-saved on-cancel]}]
  ;; All hooks must be called unconditionally (React Rules of Hooks).
  (let [payers (or (use-subscribe [:user-expenses/payers]) [])
        payers-loading? (boolean (use-subscribe [:user-expenses/payers-loading?]))
        expense-categories (or (use-subscribe [:user-expenses/expense-categories]) [])
        expense-categories-loading? (boolean (use-subscribe [:user-expenses/expense-categories-loading?]))
        user-payer-id (use-subscribe [:user-expenses/user-payer-id])
        profile (or (use-subscribe [:profile/data]) {})
        settings (:settings profile)
        [requested? set-requested!] (use-state false)
        [prepared-initial-data set-prepared-initial-data!] (use-state nil)
        posted? (= "posted" (:status receipt))
        linked-expense (:linked-expense receipt)
        receipt-initial-data (use-memo
                               (fn []
                                 (when receipt
                                   (if (and posted? (map? linked-expense))
                                     ;; Posted receipt: pre-fill from the linked expense
                                     (norm/normalize-initial-data linked-expense)
                                     ;; Normal receipt: pre-fill from OCR data
                                     (norm/normalize-receipt-data receipt))))
                               [receipt posted? linked-expense])
        merged-initial-data (use-memo
                              #(merge {:purchased_at (current-datetime-local)
                                       :items [(new-line-item)]}
                                 receipt-initial-data)
                              [receipt-initial-data])]

    ;; Load dependencies for receipt approval path.
    (use-effect
      (fn []
        (when receipt-id
          (set-requested! true)
          (rf/dispatch [:user-expenses/fetch-suppliers {:limit 100 :offset 0}])
          (rf/dispatch [:user-expenses/fetch-payers {:limit 100 :offset 0}])
          (rf/dispatch [:user-expenses/fetch-expense-categories {:limit 500 :offset 0}])
          (rf/dispatch [:user-expenses/fetch-expense-contexts {:limit 500 :offset 0}]))
        js/undefined)
      [receipt-id])

    ;; Lock in initial values once payers and expense categories have loaded (receipt approval only).
    (use-effect
      (fn []
        (when (and receipt-id
                requested?
                (nil? prepared-initial-data)
                (or (seq payers) (not payers-loading?))
                (or (seq expense-categories) (not expense-categories-loading?)))
          (let [existing-payer-id (some-> (:payer_id merged-initial-data) str str/trim not-empty)
                existing-category-id (some-> (:expense_category_id merged-initial-data) str str/trim not-empty)
                default-payer-id* (some-> (default-payer-id payers (or (:default-payer-id settings)
                                                                     user-payer-id))
                                    str
                                    str/trim
                                    not-empty)
                default-category-id* (default-expense-category-id expense-categories settings)
                prepared (cond-> merged-initial-data
                           (and (nil? existing-payer-id) default-payer-id*)
                           (assoc :payer_id default-payer-id*)

                           (and (nil? existing-category-id) default-category-id*)
                           (assoc :expense_category_id default-category-id*))]
            (set-prepared-initial-data! prepared)))
        js/undefined)
      [receipt-id
       requested?
       prepared-initial-data
       payers
       payers-loading?
       expense-categories
       expense-categories-loading?
       settings
       user-payer-id
       merged-initial-data])

    (if-not receipt-id
      ;; Manual expense entry — adaptive form (handles its own loading)
      ($ manual-form/manual-expense-form
        {:on-cancel on-cancel
         :on-submit (fn [form-data]
                      (rf/dispatch [:user-expenses/create-expense-modal form-data on-success]))})

      ;; Receipt approval (or posted receipt editing)
      (if (nil? prepared-initial-data)
        ($ :div {:class "flex justify-center p-6"}
          ($ :span {:class "ds-loading ds-loading-spinner ds-loading-md text-primary"}))

        ($ forms/receipt-approval-form
          {:receipt-id receipt-id
           :receipt receipt
           :initial-data prepared-initial-data
           :on-cancel on-cancel
           :on-review-saved on-review-saved
           :on-expense-saved on-success})))))

(defui user-expense-edit-form-modal
  "Edit user expense modal using master-detail-form wrapper for detail orchestration."
  [{:keys [expense-id initial-data on-success on-cancel]}]
  (let [t (i18n/use-t)
        locale (or (use-subscribe [:locale]) :bs)
        expense-id-str (some-> expense-id str)
        ;; Subscribe to detail state
        current-expense (use-subscribe [:user-expenses/current-expense])
        detail-loading? (boolean (use-subscribe [:user-expenses/current-expense-loading?]))
        detail-error (use-subscribe [:user-expenses/current-expense-error])
        suppliers (or (use-subscribe [:user-expenses/suppliers]) [])
        payers (or (use-subscribe [:user-expenses/payers]) [])
        expense-categories (or (use-subscribe [:user-expenses/expense-categories]) [])
        expense-contexts (or (use-subscribe [:user-expenses/expense-contexts]) [])
        profile (or (use-subscribe [:profile/data]) {})
        enabled-currencies (currency-ui/enabled-currency-options profile)

        ;; Memoize entity-spec
        entity-spec (use-memo
                      #(specs/get-expense-form-spec suppliers payers
                 {:locale locale
                  :expense-categories expense-categories
                  :expense-contexts expense-contexts
                          :enabled-currencies enabled-currencies})
                [suppliers payers expense-categories expense-contexts enabled-currencies locale])

        ;; Default values for expense form
        ;; Memoized to keep identity stable across renders (prevents fork resets).
        default-values (use-memo
                         (fn []
                           {:currency (currency-ui/default-currency profile)
                            :purchased_at (current-datetime-local)
                            :items [(new-line-item)]})
                         [profile])]

    ;; Load dependencies (suppliers/payers)
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

    ($ master-detail-form
      {:mode :edit
       :entity-name "user-expense"
       :entity-spec entity-spec
       :entity-id expense-id-str

       ;; Detail orchestration
       :load-detail! (fn [id] (rf/dispatch [:user-expenses/fetch-expense id]))
       :select-detail current-expense
       :detail-loading? detail-loading?
       :detail-error detail-error

       ;; Data transformation
       :normalize-initial-data norm/normalize-initial-data
       :validate-values norm/validate-expense-values
       :prepare-submit-values norm/prepare-expense-submit-values

       ;; Callbacks
       :on-submit (fn [prepared-data]
                    (rf/dispatch [:user-expenses/update-expense-modal expense-id-str prepared-data on-success]))
       :on-cancel on-cancel

       ;; Optional
       :initial-row-data initial-data
       :default-values default-values
      :button-text (t :common/update)})))
