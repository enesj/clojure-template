(ns app.domain.frontend.expenses.components.user-expense-form.specs
  "Expense form field spec builder for user-scoped expense forms."
  (:require
    [app.domain.frontend.expenses.components.form-fields.line-items :refer [line-items-input]]
    [app.domain.frontend.expenses.components.form-fields.total-amount :refer [total-amount-input totals-display]]
    [app.domain.frontend.expenses.components.user-expense-form.inline-supplier-select :refer [user-supplier-select-with-inline-create]]
    [app.domain.frontend.expenses.ui.currencies :as currency-ui]
    [app.domain.frontend.expenses.ui.select-options :as select-options]
    [app.template.frontend.settings.resolver :as settings-resolver]
    [clojure.string :as str]))

(defn- translated-copy
  [locale translation-key fallback]
  (or (settings-resolver/translate-label-key (or locale :en) translation-key)
    fallback))

(defn- localized-field-label
  [locale field-key fallback-label]
  (settings-resolver/resolve-field-label
    (or locale :en)
    field-key
    {:label fallback-label
     :fallback-label fallback-label}))

(defn build-line-item-columns
  [locale]
  [{:id :raw_label
    :label (translated-copy locale :common/description "Description")
    :type :text
    :placeholder (translated-copy locale :expense-new/item-description-ph "Item description")
    :width "min-w-[180px]"}
   {:id :qty
    :label (translated-copy locale :common/qty "Qty")
    :type :number
    :step "0.001"
    :min "0"
    :placeholder (translated-copy locale :expense-new/item-qty-ph "Qty")
    :width "w-[100px]"}
   {:id :unit_price
    :label (translated-copy locale :common/unit-price "Unit price")
    :type :number
    :step "0.01"
    :min "0"
    :placeholder (translated-copy locale :expense-new/item-unit-price-ph "Unit price")
    :width "w-[100px]"}
   {:id :line_total
    :label (translated-copy locale :common/total "Total")
    :type :number
    :step "0.01"
    :min "0"
    :placeholder (translated-copy locale :expense-new/item-total-ph "Total")
    :width "w-[100px]"}])

(defn build-line-items-field-spec
  ([locale]
   (build-line-items-field-spec locale {}))
  ([locale {:keys [max-height]}]
   {:id :items
    :component line-items-input
    :label (translated-copy locale :common/line-items "Line Items")
    :columns (build-line-item-columns locale)
    :style {:maxHeight (or max-height "300px")}
    :overflow-y-class "overflow-y-auto"
    :scrollbar-gutter-stable? true}))

(def line-items-field-spec
  "Standalone line items field spec for use outside of the main form spec."
  (build-line-items-field-spec :en))

(defn get-expense-form-spec
  "Return a field spec vector for the user expense form.

  Optional opts support the receipt-approval UX."
  ([suppliers payers]
   (get-expense-form-spec suppliers payers {}))
  ([suppliers payers {:keys [locale receipt-approval? supplier-guess receipt receipt-id exclude-line-items? expense-categories expense-contexts enabled-currencies]
                      :as opts}]
   (let [receipt-id* (or receipt-id (:id receipt))
         receipt-id-str (some-> receipt-id* str)
         locale (or locale :en)
         currency-options (if (seq enabled-currencies)
                            enabled-currencies
                            currency-ui/fallback-currency-options)
         supplier-component (if (contains? opts :supplier-component)
                              (:supplier-component opts)
                              user-supplier-select-with-inline-create)
         receipt-supplier-guess (some-> (or (:supplier-guess receipt) supplier-guess)
                                  str
                                  str/trim
                                  not-empty)
         receipt-total-guess (:total-amount-guess receipt)
         totals-match? (:total-guess-equals-lines-total-guess? receipt)
         base-fields
         [(cond-> {:id :supplier_id
                   :type :select
                   :label (localized-field-label locale :supplier_id "Supplier")
                   :required true
                   :placeholder (translated-copy locale :expense-new/supplier-select "Select supplier")
                   :create-default-display-name (when receipt-approval?
                                                  receipt-supplier-guess)
                   :receipt-id receipt-id-str
                   :receipt-supplier-guess receipt-supplier-guess
                   :options (map (fn [s]
                                   {:value (:id s)
                                    :label (select-options/supplier-label s)})
                              suppliers)}
            supplier-component
            (assoc :component supplier-component))
          {:id :payer_id
           :type :select
           :label (localized-field-label locale :payer_id "Payer")
           :required true
           :placeholder (translated-copy locale :expense-new/payer-select "Select payer")
           :options (map (fn [p]
                           {:value (:id p)
                            :label (str (:label p)
                                     (when (:type p)
                                       (str " (" (:type p) ")")))})
                      payers)}
          {:id :expense_category_id
           :type :select
           :label (localized-field-label locale :expense_category_id "Expense category")
           :required false
           :placeholder (translated-copy locale :common/optional "Optional")
           :options (map (fn [c]
                           {:value (:id c)
                            :label (:name c)})
                      (or expense-categories []))}
              {:id :expense_context_id
               :type :select
               :label (localized-field-label locale :expense_context_id "Expense context")
               :required false
               :placeholder (translated-copy locale :common/optional "Optional")
               :options (map (fn [c]
                   {:value (:id c)
                    :label (:name c)})
                  (or expense-contexts []))}
          {:id :purchased_at
           :type :datetime-local
           :label (localized-field-label locale :purchased_at "Purchased at")
           :required true}
          {:id :total_amount
           :component (if receipt-approval? totals-display total-amount-input)
           :label (when-not receipt-approval?
                    (localized-field-label locale :total_amount "Total amount"))
           :required true
           ;; Receipt-approval UX: total is auto-derived.
           :show-use-total? (not receipt-approval?)
           :receipt-total-guess receipt-total-guess
           :totals-match? totals-match?}
          {:id :currency
           :type :select
           :label (localized-field-label locale :currency "Currency")
           :required true
           :options currency-options}
          {:id :notes
           :type :textarea
           :label (localized-field-label locale :notes "Notes")
           :required false
           :placeholder (translated-copy locale :expense-new/notes-placeholder "Optional notes")
           :layout (when receipt-approval? :stacked)}]]
     (if exclude-line-items?
       base-fields
       (conj base-fields
         (build-line-items-field-spec locale {:max-height (if receipt-approval? "260px" "300px")}))))))
