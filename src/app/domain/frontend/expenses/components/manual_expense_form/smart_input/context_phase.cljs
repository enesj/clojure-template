(ns app.domain.frontend.expenses.components.manual-expense-form.smart-input.context-phase
  "Phase 2 rendering: context/review with items summary, context search,
  payer/date/currency/notes, and submit."
  (:require
    [app.domain.frontend.expenses.components.form-fields.helpers
     :refer [format-decimal safe-parse-number]]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.components
     :refer [autocomplete-dropdown entity-chip phase-two-quick-pick-groups
             quick-picks type-picker]]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.constants
     :refer [supplier-color-palette]]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.helpers
     :refer [build-quick-pick-supplier-color-map colorize-quick-pick-groups
             context-phase-initial-sub-stage entity-type-label
             phase-two-missing-context-types]]
    [clojure.string :as str]
    [uix.core :refer [$ defui use-state]]))

(defui context-phase-view
  "Renders Phase 2 (context + review) of the smart expense form.

  Two sub-stages:
  1. :defaults — items summary, category chip, payer/date/currency,
     plus an optional Add-store action.
  2. :store-search — store/supplier search + suggestions."
  [{:keys [t items context input-text input-ref
           dropdown-open? highlight-idx type-picker-text creating?
           search-results quick-search-loading? context-suggestions
           items-total currency currency-options
           payers payer-id purchased-at initial-sub-stage
           payer-name purchased-date
           on-clear-payer on-clear-date on-clear-currency
            suppliers stores expense-categories expense-contexts articles
           ;; handlers
           on-input-change on-input-keydown on-select-result
           on-create-inline on-type-pick on-remove-context
           on-set-phase on-focus-input on-set-payer-id
            on-set-purchased-at on-set-currency on-set-expense-context-id
           on-cancel-type-picker]}]
  (let [[sub-stage set-sub-stage!] (use-state
                                     (context-phase-initial-sub-stage
                                       context
                                       initial-sub-stage))
        selected-supplier-id (some-> context :supplier :id)
          selected-expense-context-id (some-> context :expense-context :id str)
        defaults-missing-types (phase-two-missing-context-types context :defaults)
        defaults-quick-pick-groups (when (seq defaults-missing-types)
                                     (phase-two-quick-pick-groups defaults-missing-types
                                       context-suggestions
                                       suppliers
                                       stores
                                       expense-categories
                                       articles
                                       selected-supplier-id))
        has-preselections? (or (seq context) payer-name purchased-date currency)]
    ($ :div {:class "space-y-6"}

      ;; Back to items link
      ($ :button {:type "button"
                  :class "text-base text-primary hover:text-primary/80 transition-colors"
                  :on-click (fn [e] (.preventDefault e)
                              (if (and (= sub-stage :store-search)
                                    (not= initial-sub-stage :store-search))
                                (set-sub-stage! :defaults)
                                (do (on-set-phase :items)
                                  (on-focus-input))))}
        (t :smart-expense/back-to-items))

      ;; Items summary
      ($ :div {:class "bg-base-100 rounded-2xl border border-base-200 p-4"}
        ($ :div {:class "flex items-center justify-between mb-3"}
          ($ :span {:class "font-semibold text-lg"}
            (t :smart-expense/items-summary (count items)))
          ($ :span {:class "text-xl font-bold"}
            (format-decimal items-total) " " currency))
        ($ :div {:class "space-y-1.5"}
          (for [item items]
            (let [q (or (safe-parse-number (:qty item)) 1)
                  p (or (safe-parse-number (:unit-price item)) 0)]
              ($ :div {:key (:id item)
                       :class "flex items-center gap-2 text-base text-base-content/70"}
                ($ :span "📦")
                ($ :span {:class "flex-1 truncate"} (:label item))
                ($ :span {:class "font-mono"}
                  (str q " × " (format-decimal p))))))))

      ;; Preselection chips (context + defaults)
      (when has-preselections?
        ($ :div {:class "rounded-2xl border border-base-200 p-3"}
          ($ :p {:class "text-sm text-base-content/70 mb-2 uppercase tracking-wider font-semibold"}
            (t :smart-expense/defaults-label))
          ($ :div {:class "flex flex-wrap gap-2"}
            (for [[entity-type chip] context]
              ($ entity-chip {:key (str "ctx-" (name entity-type))
                              :entity-type entity-type
                              :label (:label chip)
                              :tooltip (entity-type-label t entity-type)
                              :on-remove #(on-remove-context entity-type)}))
            (when payer-name
              ($ entity-chip {:key "default-payer"
                              :entity-type :payer
                              :label payer-name
                              :tooltip (entity-type-label t :payer)
                              :on-remove on-clear-payer}))
            (when purchased-date
              ($ entity-chip {:key "default-date"
                              :entity-type :date
                              :label purchased-date
                              :tooltip (entity-type-label t :date)
                              :on-remove on-clear-date}))
            (when currency
              ($ entity-chip {:key "default-currency"
                              :entity-type :currency
                              :label currency
                              :tooltip (entity-type-label t :currency)
                              :on-remove on-clear-currency}))))))

      ;; ── Sub-stage: defaults ──────────────────────────────────
      (when (= sub-stage :defaults)
        ($ :<>
          (when (seq defaults-quick-pick-groups)
            ($ :div {:class "space-y-4"}
              (for [{:keys [entity-type items]} defaults-quick-pick-groups]
                ($ :div {:key (str "defaults-" (name entity-type))
                         :class "space-y-2"}
                  ($ :p {:class "text-base text-base-content/50"}
                    (str (t :smart-expense/pick-prefix) (entity-type-label t entity-type)))
                  ($ quick-picks
                    {:entity-type entity-type
                     :items items
                     :on-select on-select-result})))))

          ;; Payer + Date + Currency row
          ($ :div {:class "grid grid-cols-3 gap-3"}
            ;; Payer
            ($ :div
              ($ :label {:class "text-sm text-base-content/50 mb-1 block"}
                (t :smart-expense/payer-label))
              ($ :select {:id "smart-expense-payer"
                          :class (str "w-full text-base p-3 h-12 rounded-xl border-2 "
                                   "border-base-300 bg-white focus:border-primary cursor-pointer")
                          :value (or payer-id "")
                          :on-change (fn [e] (on-set-payer-id (.. e -target -value)))}
                ($ :option {:value ""} (t :smart-expense/payer-select-ph))
                (for [p payers]
                  ($ :option {:key (:id p) :value (:id p)}
                    (str (:label p)
                      (when-let [pt (or (:type p) (:payer-type-label p))]
                        (str " (" pt ")")))))))
            ;; Date
            ($ :div
              ($ :label {:class "text-sm text-base-content/50 mb-1 block"}
                (t :smart-expense/date-label))
              ($ :input {:id "smart-expense-date"
                         :type "datetime-local"
                         :class (str "w-full text-base p-3 h-12 rounded-xl border-2 "
                                  "border-base-300 bg-white focus:border-primary")
                         :value purchased-at
                         :on-change (fn [e] (on-set-purchased-at (.. e -target -value)))}))
            ;; Currency
            ($ :div
              ($ :label {:class "text-sm text-base-content/50 mb-1 block"}
                (t :smart-expense/currency-label))
              ($ :select {:class (str "w-full text-base p-3 h-12 rounded-xl border-2 border-base-300 "
                                   "bg-white focus:border-primary cursor-pointer")
                          :value currency
                          :on-change (fn [e] (on-set-currency (.. e -target -value)))}
                (for [{:keys [value label]} currency-options]
                  ($ :option {:key value :value value} label)))))

          ;; Optional Expense Context row
          ($ :div {:class "grid grid-cols-1 gap-3"}
            ($ :div
              ($ :label {:class "text-sm text-base-content/50 mb-1 block"
                         :for "smart-expense-context"}
                (entity-type-label t :expense-context))
              ($ :select {:id "smart-expense-context"
                          :class (str "w-full text-base p-3 h-12 rounded-xl border-2 "
                                   "border-base-300 bg-white focus:border-primary cursor-pointer")
                          :value (or selected-expense-context-id "")
                          :on-change (fn [e] (on-set-expense-context-id (.. e -target -value)))}
                ($ :option {:value ""} (t :common/optional))
                (for [expense-context expense-contexts]
                  ($ :option {:key (:id expense-context) :value (:id expense-context)}
                    (:name expense-context))))))

          ;; Optional: add store context before saving
          (when-not (:store context)
            ($ :div {:class "pt-2 flex"}
              ($ :button {:id "btn-add-store"
                          :type "button"
                          :class "ds-btn ds-btn-outline ds-btn-lg text-lg py-3"
                          :on-click (fn [e] (.preventDefault e)
                                      (set-sub-stage! :store-search))}
                (t :smart-expense/add-store))))))

      ;; ── Sub-stage: store-search ──────────────────────────────
      (when (= sub-stage :store-search)
        ($ :<>
          ;; Context search for supplier/store/category
          (let [missing (phase-two-missing-context-types context :store-search)
                placeholder (when (seq missing)
                              (str (t :smart-expense/search-prefix)
                                (str/join ", " (map #(entity-type-label t %) missing))
                                "..."))
                single-missing? (= 1 (count missing))]
            (when (seq missing)
              (let [missing-set (set missing)
                    raw-quick-pick-groups (when (str/blank? input-text)
                                            (phase-two-quick-pick-groups missing
                                              context-suggestions
                                              suppliers
                                              stores
                                              expense-categories
                                              articles
                                              selected-supplier-id))
                    quick-pick-supplier-color-map (build-quick-pick-supplier-color-map
                                                    raw-quick-pick-groups
                                                    supplier-color-palette)
                    quick-pick-groups (some-> raw-quick-pick-groups
                                        (colorize-quick-pick-groups quick-pick-supplier-color-map))
                    filtered-results (filterv
                                       (fn [r]
                                         (let [et (keyword (or (:entity-type r) (:entity_type r)))]
                                           (contains? missing-set et)))
                                       (or search-results []))]
                ($ :<>
                  (when (seq quick-pick-groups)
                    ($ :div {:class "space-y-4"}
                      (for [{:keys [entity-type items]} quick-pick-groups]
                        ($ :div {:key (name entity-type)
                                 :class "space-y-2"}
                          ($ :p {:class "text-base text-base-content/50"}
                            (str (t :smart-expense/pick-prefix) (entity-type-label t entity-type)))
                          ($ quick-picks
                            {:entity-type entity-type
                             :items items
                             :on-select on-select-result})))))

                  ($ :div {:class "relative"
                           :on-click (fn [e] (.stopPropagation e))}
                    ($ :input {:ref input-ref
                               :id "smart-expense-context-input"
                               :type "text"
                               :auto-focus true
                               :class (str "w-full text-xl p-4 rounded-xl border-2 border-base-300 "
                                        "focus:border-primary focus:outline-none focus:shadow-lg "
                                        "focus:shadow-primary/10 "
                                        "transition-all bg-white placeholder:text-base-content/30")
                               :placeholder (if single-missing?
                                              (t :smart-expense/or-search-suffix
                                                (entity-type-label t (first missing)))
                                              placeholder)
                               :value input-text
                               :on-change on-input-change
                               :on-key-down on-input-keydown
                               :auto-complete "off"})

                    ;; Autocomplete dropdown (portal)
                    (when dropdown-open?
                      ($ autocomplete-dropdown
                        {:t t
                         :results filtered-results
                         :loading? (and quick-search-loading? (empty? filtered-results))
                         :highlight-idx highlight-idx
                         :on-select on-select-result
                         :on-create on-create-inline
                         :input-text (str/trim input-text)
                         :anchor-ref input-ref})))

                  ;; Type picker — only missing context types
                  (when type-picker-text
                    ($ type-picker
                      {:t t
                       :text type-picker-text
                       :on-pick on-type-pick
                       :on-cancel on-cancel-type-picker
                       :creating? creating?
                       :allowed-types missing}))))))

          ;; Payer + Date + Currency row (compact in store-search)
          ($ :div {:class "grid grid-cols-3 gap-3"}
            ;; Payer
            ($ :div
              ($ :label {:class "text-sm text-base-content/50 mb-1 block"}
                (t :smart-expense/payer-label))
              ($ :select {:id "smart-expense-payer-store"
                          :class (str "w-full text-base p-3 h-12 rounded-xl border-2 "
                                   "border-base-300 bg-white focus:border-primary cursor-pointer")
                          :value (or payer-id "")
                          :on-change (fn [e] (on-set-payer-id (.. e -target -value)))}
                ($ :option {:value ""} (t :smart-expense/payer-select-ph))
                (for [p payers]
                  ($ :option {:key (:id p) :value (:id p)}
                    (str (:label p)
                      (when-let [pt (or (:type p) (:payer-type-label p))]
                        (str " (" pt ")")))))))
            ;; Date
            ($ :div
              ($ :label {:class "text-sm text-base-content/50 mb-1 block"}
                (t :smart-expense/date-label))
              ($ :input {:id "smart-expense-date-store"
                         :type "datetime-local"
                         :class (str "w-full text-base p-3 h-12 rounded-xl border-2 "
                                  "border-base-300 bg-white focus:border-primary")
                         :value purchased-at
                         :on-change (fn [e] (on-set-purchased-at (.. e -target -value)))}))
            ;; Currency
            ($ :div
              ($ :label {:class "text-sm text-base-content/50 mb-1 block"}
                (t :smart-expense/currency-label))
              ($ :select {:class (str "w-full text-base p-3 h-12 rounded-xl border-2 border-base-300 "
                                   "bg-white focus:border-primary cursor-pointer")
                          :value currency
                          :on-change (fn [e] (on-set-currency (.. e -target -value)))}
                (for [{:keys [value label]} currency-options]
                  ($ :option {:key value :value value} label)))))

          ;; Optional Expense Context row (compact in store-search)
          ($ :div {:class "grid grid-cols-1 gap-3"}
            ($ :div
              ($ :label {:class "text-sm text-base-content/50 mb-1 block"
                         :for "smart-expense-context-store"}
                (entity-type-label t :expense-context))
              ($ :select {:id "smart-expense-context-store"
                          :class (str "w-full text-base p-3 h-12 rounded-xl border-2 "
                                   "border-base-300 bg-white focus:border-primary cursor-pointer")
                          :value (or selected-expense-context-id "")
                          :on-change (fn [e] (on-set-expense-context-id (.. e -target -value)))}
                ($ :option {:value ""} (t :common/optional))
                (for [expense-context expense-contexts]
                  ($ :option {:key (:id expense-context) :value (:id expense-context)}
                    (:name expense-context))))))))))

