(ns app.domain.frontend.expenses.components.manual-expense-form.smart-input
  "Progressive smart input for manual expense entry.

  Phase 1 (Items): Search articles, each gets inline qty/price with defaults.
  Enter adds the next item. Enter on empty finishes items entry.

  Phase 2 (Context): Items summary, search for store/supplier/category,
  payer/date/currency/notes. Submit."
  (:require
    [app.domain.frontend.expenses.components.form-fields.helpers
     :refer [current-datetime-local format-decimal]]
    [app.domain.frontend.expenses.components.manual-expense-form.search :as search]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.components
     :refer [build-quick-pick-groups]]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.constants
     :refer [create-events create-field-names supplier-color-palette]]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.render
     :as smart-render]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.search-helpers
     :as smart-search-helpers]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.helpers
     :refer [build-quick-pick-supplier-color-map colorize-quick-pick-groups
             compute-items-total context-phase-initial-sub-stage
             current-related-context default-category-chip-to-preselect
             focused-search-types items-phase-quick-pick-types
             payer-default-id prepare-submit-values validate-form]]
    [app.domain.frontend.expenses.events.supplier-stores :as supplier-stores-events]
    [app.domain.frontend.expenses.ui.currencies :as currency-ui]
    [app.template.frontend.i18n :refer [use-t]]
    app.domain.frontend.expenses.events.user-expenses.quick-add-search
    [clojure.string :as str]
    [re-frame.core :as rf]
    [uix.core :refer [defui use-effect use-ref use-state]]
    [uix.re-frame :refer [use-subscribe]]))

;; ─────────────────────────────────────────────
;; Main component
;; ─────────────────────────────────────────────

(defui smart-expense-form
  "Progressive smart input for manual expense entry.

  Props:
  - :on-submit  fn called with prepared form data
  - :on-cancel  fn called when cancel is clicked
  - :submitting? boolean"
  [{:keys [on-submit on-cancel submitting?]}]
  (let [t (use-t)
        ;; Subscriptions
        suppliers (or (use-subscribe [:user-expenses/suppliers]) [])
        stores (or (use-subscribe [:user-expenses/stores]) [])
        expense-categories (or (use-subscribe [:user-expenses/expense-categories]) [])
        expense-contexts (or (use-subscribe [:user-expenses/expense-contexts]) [])
        articles (or (use-subscribe [:user-expenses/articles]) [])
        payers (or (use-subscribe [:user-expenses/payers]) [])
        payers-loading? (boolean (use-subscribe [:user-expenses/payers-loading?]))
        user-payer-id (use-subscribe [:user-expenses/user-payer-id])
        profile (or (use-subscribe [:profile/data]) {})
        profile-settings (:settings profile)

        ;; Quick Add search (backend)
        quick-search-results (use-subscribe [:user-expenses/quick-add-search-results :all])
        quick-search-loading? (use-subscribe [:user-expenses/quick-add-search-loading? :all])
        quick-add-related (use-subscribe [:user-expenses/quick-add-related])
        quick-add-history (use-subscribe [:user-expenses/quick-add-history])
        cooccurring-articles (use-subscribe [:user-expenses/cooccurring-articles])
        context-suggestions (use-subscribe [:user-expenses/context-suggestions])

        ;; Core state
        [phase set-phase!] (use-state :items)       ;; :items or :context
        [context-initial-sub-stage set-context-initial-sub-stage!] (use-state nil)
        [items set-items!] (use-state [])            ;; [{:id :article-id :label :qty :unit-price}]
        [context set-context!] (use-state {})        ;; {:supplier {:id :label} :store ... :category ...}
        [input-text set-input-text!] (use-state "")
        [article-mode? set-article-mode!] (use-state false) ;; narrows search to articles after first pick

        ;; Autocomplete UI
        [dropdown-open? set-dropdown-open!] (use-state false)
        [highlight-idx set-highlight-idx!] (use-state -1)
        [type-picker-text set-type-picker-text!] (use-state nil)
        [creating? set-creating!] (use-state false)
        ;; One-shot focus signal: when an article is added we set this to the
        ;; new item id, the matching item-row picks it up via use-effect and
        ;; calls back to clear it. Keeps qty-ref encapsulated in the row.
        [focus-item-id set-focus-item-id!] (use-state nil)

        ;; Form values (Phase 2)
        [currency set-currency!] (use-state "BAM")
        [purchased-at set-purchased-at!] (use-state (current-datetime-local))
        [payer-id set-payer-id!] (use-state nil)
        [notes _set-notes!] (use-state "")
        [default-category-preselect-enabled? set-default-category-preselect-enabled!] (use-state true)
        [error set-error!] (use-state nil)
        [ready? set-ready!] (use-state false)
        currency-options (currency-ui/enabled-currency-options profile)

        input-ref (use-ref nil)

        ;; Resolved default labels for Phase 1 chips
        payer-name (some (fn [p]
                           (when (= (str (:id p)) (str payer-id))
                             (or (:label p) (:display_name p) (:name p))))
                     payers)
        purchased-date (when purchased-at
                         (let [[date-part] (str/split (str purchased-at) #"T")
                               [y m d] (str/split date-part #"-")]
                           (str d "." m "." y)))

        ;; Search results: show immediate local matches while dedicated backend search loads.
        selected-category (:category context)
        available-search-types (focused-search-types context article-mode?)
        ;; Build a price map from related articles (supplier/store related data)
        ;; so local search results can show prices even before the backend search responds.
        related-article-prices (smart-search-helpers/build-related-article-prices quick-add-related)
        articles-with-prices (smart-search-helpers/attach-article-prices articles related-article-prices)
        local-search-results (smart-search-helpers/build-local-search-results
                               dropdown-open?
                               input-text
                               suppliers
                               stores
                               expense-categories
                               articles-with-prices
                               available-search-types
                               context)
        search-results (smart-search-helpers/build-search-results
                         dropdown-open?
                         local-search-results
                         quick-search-results
                         available-search-types)
        selected-supplier-id (some-> context :supplier :id)
        selected-supplier-id-str (some-> selected-supplier-id str)
        ;; Per-supplier full store pool — populated lazily by
        ;; ::supplier-stores-events/fetch-stores-for-supplier when a
        ;; supplier becomes selected (see use-effect below). The local
        ;; `stores` subscription is tenant-scoped to stores the user has
        ;; already bought from, so we need this expanded pool to surface
        ;; every branch of the chosen supplier in phase 2 quick picks.
        supplier-stores-pool (or (use-subscribe [:user-expenses/supplier-stores-pool
                                                 selected-supplier-id-str])
                               [])
        phase-two-stores (smart-search-helpers/resolve-phase-two-stores
                   selected-supplier-id-str
                   supplier-stores-pool
                   stores)
        related-context (current-related-context context)
        related-matches? (and related-context
                           (= (:entity-type quick-add-related) (:entity-type related-context))
                           (= (:entity-id quick-add-related) (some-> (:entity-id related-context) str)))
        history-loaded? (:loaded? quick-add-history)
        history-stores (vec (or (:stores quick-add-history) []))
        history-articles (vec (or (:articles quick-add-history) []))
        ;; Trust the backend history endpoint which returns usage-ranked
        ;; data (manual-first, all-expenses fallback). No alphabetical
        ;; general-pool fallback here.
        related-stores (cond
                         (seq history-stores) history-stores
                         ;; When a supplier is selected and related data
                         ;; has supplier-specific stores, use those.
                         (and history-loaded? related-matches?
                           (= :supplier (:entity-type related-context)))
                         (get-in quick-add-related [:related :stores] [])
                         :else [])
        related-articles (cond
                           (seq history-articles) history-articles
                           (and history-loaded? related-matches?)
                           (or (get-in quick-add-related [:related :articles]) [])
                           :else [])

        ;; Co-occurring article suggestions (article-mode) or normal quick picks
        items-phase-quick-pick-types* (items-phase-quick-pick-types available-search-types context article-mode?)
        raw-focused-quick-pick-groups (when (and (str/blank? input-text)
                                              (seq items-phase-quick-pick-types*))
                                        (build-quick-pick-groups
                                          items-phase-quick-pick-types*
                                          suppliers
                                          related-stores
                                          expense-categories
                                          related-articles
                                          selected-supplier-id))
        focused-quick-pick-supplier-color-map (build-quick-pick-supplier-color-map
                                                raw-focused-quick-pick-groups
                                                supplier-color-palette)
        focused-quick-pick-groups (some-> raw-focused-quick-pick-groups
                                    (colorize-quick-pick-groups focused-quick-pick-supplier-color-map))
        cooccurring-pick-items (when (and article-mode?
                                       (str/blank? input-text)
                                       (seq cooccurring-articles))
                                 (->> cooccurring-articles
                                   (mapv (fn [a]
                                           {:id (or (:id a) "")
                                            :label (or (:label a) "")
                                            :entity-type :article
                                            :last-price (:last_price a)
                                            :last-price-source (:last_price_source a)
                                            :entity a}))))

        total-dropdown-count (+ (count (or search-results []))
                               (if (and (not (str/blank? input-text))
                                     (not (search/number-input? input-text))) 1 0))

        ;; Computed totals
        items-total (compute-items-total items)

        ;; Actions
        focus-input! (fn []
                       (when-let [el @input-ref]
                         (js/setTimeout #(.focus el) 60)))

        add-item! (fn [article-id label-text last-price]
                    (let [new-id (str (random-uuid))
                          new-item {:id new-id
                                    :article-id article-id
                                    :label label-text
                                    :qty "1"
                                    :unit-price (if last-price (format-decimal last-price) "")}
                          next-items (conj (or items []) new-item)
                          all-article-ids (->> next-items
                                            (keep :article-id)
                                            vec)]
                      (set-items! (fn [prev] (conj prev new-item)))
                      (set-input-text! "")
                      (set-dropdown-open! false)
                      (set-highlight-idx! -1)
                      (set-type-picker-text! nil)
                      (set-article-mode! true)
                      (set-focus-item-id! new-id)
                      (rf/dispatch [:user-expenses/clear-quick-add-search :all])
                      (rf/dispatch [:user-expenses/fetch-cooccurring-articles
                                    all-article-ids selected-supplier-id])))

        remove-item! (fn [item-id]
                       (set-items! (fn [prev]
                                     (let [next-items (vec (remove #(= item-id (:id %)) prev))]
                                       (when (empty? next-items)
                                         (set-article-mode! false)
                                         (rf/dispatch [:user-expenses/clear-cooccurring-articles]))
                                       next-items))))

        update-item! (fn [item-id field value]
                       (set-items!
                         (fn [prev]
                           (mapv (fn [item]
                                   (if (= item-id (:id item))
                                     (assoc item field value)
                                     item))
                             prev))))

        add-context! (fn [entity-type id label-text entity]
                       (set-context!
                         (fn [c]
                           (let [base-chip {:id id :label label-text}
                                 supplier-chip (when-let [sid (or (:supplier-id entity) (:supplier_id entity))]
                                                 {:id sid
                                                  :label (or (:supplier-display-name entity)
                                                           (:supplier_display_name entity)
                                                           (some #(when (= (str (:id %)) (str sid))
                                                                    (or (:display-name %)
                                                                      (:display_name %) ""))
                                                             suppliers))})]
                             (case entity-type
                               :supplier
                               (let [c* (assoc c :supplier base-chip)]
                                 (if (and (:store c*)
                                       (not= (str (or (get-in c* [:store :supplier-id])
                                                    (get-in c* [:store :supplier_id])))
                                         (str id)))
                                   (dissoc c* :store)
                                   c*))

                               :store
                               (cond-> (assoc c :store (assoc base-chip
                                                         :supplier-id (or (:supplier-id entity) (:supplier_id entity))
                                                         :supplier-display-name (or (:supplier-display-name entity)
                                                                                  (:supplier_display_name entity))))
                                 supplier-chip (assoc :supplier supplier-chip))

                               (assoc c entity-type base-chip)))))
                       (when (= entity-type :category)
                         (set-default-category-preselect-enabled! false))
                       (set-input-text! "")
                       (set-dropdown-open! false)
                       (set-highlight-idx! -1)
                       (set-type-picker-text! nil)
                       (rf/dispatch [:user-expenses/clear-quick-add-search :all])
                       (focus-input!))

        remove-context! (fn [entity-type]
                          (when (= entity-type :category)
                            (set-default-category-preselect-enabled! false))
                          (set-context!
                            (fn [c]
                              (let [c* (dissoc c entity-type)
                                    c* (if (= entity-type :supplier) (dissoc c* :store) c*)]
                                c*))))

        set-expense-context-id! (fn [context-id]
                                  (let [context-id* (some-> context-id str str/trim not-empty)]
                                    (set-context!
                                      (fn [c]
                                        (if context-id*
                                          (if-let [expense-context (some #(when (= context-id* (some-> (:id %) str)) %)
                                                                    expense-contexts)]
                                            (assoc c :expense-context {:id (:id expense-context)
                                                                       :label (or (:name expense-context) "")})
                                            c)
                                          (dissoc c :expense-context))))))

        begin-context-phase! (fn [requested-sub-stage]
                               (let [article-ids (->> items (keep :article-id) vec)]
                                 (set-input-text! "")
                                 (set-article-mode! false)
                                 (set-dropdown-open! false)
                                 (set-highlight-idx! -1)
                                 (set-type-picker-text! nil)
                                 (set-context-initial-sub-stage!
                                   (context-phase-initial-sub-stage
                                     context
                                     requested-sub-stage))
                                 (set-phase! :context)
                                 (rf/dispatch [:user-expenses/clear-cooccurring-articles])
                                 (when (seq article-ids)
                                   (rf/dispatch [:user-expenses/fetch-context-suggestions article-ids]))
                                 (js/setTimeout
                                   (fn []
                                     (when-let [el @input-ref]
                                       (.focus el)))
                                   100)))

        handle-select-result (fn [result]
                               (let [etype (keyword (or (:entity-type result) (:entity_type result)))
                                     ;; Merge inner :entity fields to top level so add-context!
                                     ;; can read supplier_id, supplier_display_name etc.
                                     entity (merge (:entity result) result)]
                                 (if (= etype :article)
                                   (add-item! (:id result) (:label result)
                                     (or (:last-price result) (:last_price result)))
                                   (add-context! etype (:id result) (:label result) entity))))

        handle-create-inline (fn [text]
                               (set-type-picker-text! text)
                               (set-dropdown-open! false)
                               (set-input-text! ""))

        handle-type-pick (fn [entity-type text]
                           (let [create-event (get create-events entity-type)
                                 field-name (get create-field-names entity-type)
                                 params (cond-> {field-name text}
                                          (and (= entity-type :store)
                                            (get-in context [:supplier :id]))
                                          (assoc :supplier_id (get-in context [:supplier :id])))]
                             (set-creating! true)
                             (rf/dispatch
                               [create-event params
                                (fn [entity]
                                  (set-creating! false)
                                  (when (map? entity)
                                    (let [new-id (:id entity)
                                          new-label (case entity-type
                                                      :supplier (or (:display-name entity)
                                                                  (:display_name entity) text)
                                                      :store (or (:display-name entity)
                                                               (:display_name entity) text)
                                                      :category (or (:name entity) text)
                                                      :article (or (:canonical-name entity)
                                                                 (:canonical_name entity) text)
                                                      text)]
                                      (if (= entity-type :article)
                                        (add-item! new-id new-label nil)
                                        (add-context! entity-type new-id new-label entity)))))])))

        handle-input-change (fn [e]
                              (let [v (.. e -target -value)
                                    supplier-id (some-> context :supplier :id)]
                                (set-input-text! v)
                                (set-highlight-idx! -1)
                                (set-type-picker-text! nil)
                                (if (and (>= (count (str/trim v)) 2)
                                      (not (search/number-input? v)))
                                  (do (set-dropdown-open! true)
                                    (rf/dispatch [:user-expenses/quick-add-search :all (str/trim v)
                                                  {:supplier_id supplier-id}]))
                                  (do (set-dropdown-open! false)
                                    (rf/dispatch [:user-expenses/clear-quick-add-search :all])))))

        handle-input-keydown (fn [e]
                               (let [key (.-key e)]
                                 (cond
                                   (= key "Enter")
                                   (do (.preventDefault e)
                                     (let [text (str/trim input-text)]
                                       (cond
                                         ;; Empty input + has items -> transition to context phase
                                         (and (str/blank? text) (= phase :items) (seq items))
                                         (begin-context-phase! nil)

                                         ;; Highlighted suggestion -> select it
                                         (and dropdown-open?
                                           (>= highlight-idx 0)
                                           (< highlight-idx (count (or search-results []))))
                                         (handle-select-result (nth search-results highlight-idx))

                                         ;; Highlighted "Create" row
                                         (and dropdown-open?
                                           (= highlight-idx (count (or search-results [])))
                                           (not (str/blank? text)))
                                         (handle-create-inline text)

                                         ;; No highlight, has text -> create flow
                                         (not (str/blank? text))
                                         (handle-create-inline text))))

                                   (= key "ArrowDown")
                                   (do (.preventDefault e)
                                     (when dropdown-open?
                                       (set-highlight-idx!
                                         (fn [i] (min (dec total-dropdown-count) (inc i))))))

                                   (= key "ArrowUp")
                                   (do (.preventDefault e)
                                     (when dropdown-open?
                                       (set-highlight-idx! (fn [i] (max -1 (dec i))))))

                                   (= key "Escape")
                                   (do (.preventDefault e)
                                     (set-dropdown-open! false)
                                     (set-highlight-idx! -1)
                                     (set-type-picker-text! nil))

                                   ;; Backspace on empty -> remove last item (items phase) or context chip
                                   (and (= key "Backspace") (str/blank? input-text))
                                   (cond
                                     (and (= phase :items) (seq items))
                                     (set-items! (fn [prev]
                                                   (let [next-items (vec (butlast prev))]
                                                     (when (empty? next-items)
                                                       (set-article-mode! false)
                                                       (rf/dispatch [:user-expenses/clear-cooccurring-articles]))
                                                     next-items)))

                                     (and (= phase :context) (seq context))
                                     (let [last-type (last (keys context))]
                                       (remove-context! last-type))))))

        handle-submit (fn [e]
                        (.preventDefault e)
                        (let [validation (validate-form t
                                           {:items items
                                            :context context
                                            :payer-id payer-id})]
                          (if (:ok? validation)
                            (do (set-error! nil)
                              (when (fn? on-submit)
                                (on-submit (prepare-submit-values
                                             {:items items
                                              :context context
                                              :currency currency
                                              :purchased-at purchased-at
                                              :payer-id payer-id
                                              :notes notes}))))
                            (set-error! (:error validation)))))

        ;; Pre-compute submit disabled for context phase
        submit-disabled? (not (:ok? (validate-form t
                                      {:items items
                                       :context context
                                       :payer-id payer-id})))]

    ;; Load reference data
    (use-effect
      (fn []
        (rf/dispatch [:user-expenses/fetch-suppliers {:limit 100 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-stores {:limit 100 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-articles {:limit 200 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-payers {:limit 100 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-expense-categories {:limit 500 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-expense-contexts {:limit 500 :offset 0}])
        ;; Cleanup quick add search only when the form truly unmounts.
        (fn []
          (rf/dispatch [:user-expenses/clear-quick-add-search :all])
          (rf/dispatch [:user-expenses/clear-quick-add-related])
          (rf/dispatch [:user-expenses/clear-quick-add-history])
          (rf/dispatch [:user-expenses/clear-cooccurring-articles])
          (rf/dispatch [:user-expenses/clear-context-suggestions])))
      [])

    (use-effect
      (fn []
        (when-not (currency-ui/has-enabled-currencies? profile)
          (rf/dispatch [:profile/fetch]))
        js/undefined)
      [profile])

    (use-effect
      (fn []
        (let [allowed-values (set (map :value currency-options))
              preferred (currency-ui/default-currency profile)
              next-currency (if (contains? allowed-values preferred)
                              preferred
                              (some-> currency-options first :value))]
          (when (and next-currency
                  (not (contains? allowed-values currency)))
            (set-currency! next-currency)))
        js/undefined)
      [currency-options profile currency])

    ;; Set default payer once loaded enough to render the form.
    (use-effect
      (fn []
        (when (and (not ready?)
                (or (seq payers) (not payers-loading?)))
          (set-ready! true))
        js/undefined)
      [payers payers-loading? ready?])

    ;; Backfill the default payer once payers arrive, unless the user has
    ;; already made a deliberate selection.
    (use-effect
      (fn []
        (when (and (nil? payer-id)
                (seq payers))
          (set-payer-id! (some-> (payer-default-id payers (or (:default-payer-id profile-settings)
                                                            user-payer-id))
                           str)))
        js/undefined)
      [payers payer-id profile-settings user-payer-id])

    ;; Preselect the effective default expense category once, without
    ;; re-opening it after the user explicitly closes or replaces it.
    (use-effect
      (fn []
        (when-let [default-category-chip (default-category-chip-to-preselect
                                           expense-categories
                                           profile-settings
                                           default-category-preselect-enabled?
                                           selected-category)]
          (set-context! (fn [current-context]
                          (if (:category current-context)
                            current-context
                            (assoc current-context :category default-category-chip))))
          (set-default-category-preselect-enabled! false))
        js/undefined)
      [selected-category expense-categories profile-settings default-category-preselect-enabled?])

    ;; Load manual-history quick picks for phase 1 and re-scope them when a supplier is selected.
    (use-effect
      (fn []
        (rf/dispatch [:user-expenses/fetch-quick-add-history selected-supplier-id-str 10])
        js/undefined)
      [selected-supplier-id-str])

    ;; Load related records for focused quick picks when context narrows the search.
    (use-effect
      (fn []
        (let [limit (if (= 1 (count available-search-types)) 10 5)]
          (if (and related-context (< (count available-search-types) 4))
            (rf/dispatch [:user-expenses/fetch-quick-add-related
                          (:entity-type related-context)
                          (:entity-id related-context)
                          limit])
            (rf/dispatch [:user-expenses/clear-quick-add-related])))
        js/undefined)
      [available-search-types related-context (count available-search-types)])

    ;; Lazily fetch the *full* store catalogue for the selected supplier
    ;; (cached per-supplier) so phase 2 quick picks can show every branch,
    ;; not just the tenant-scoped subset returned by the standard list.
    (use-effect
      (fn []
        (when selected-supplier-id-str
          (rf/dispatch [::supplier-stores-events/fetch-stores-for-supplier
                        selected-supplier-id-str]))
        js/undefined)
      [selected-supplier-id-str])

    ;; Close dropdown on outside click
    (use-effect
      (fn []
        (let [handler (fn [_]
                        (set-dropdown-open! false)
                        (set-highlight-idx! -1))]
          (.addEventListener js/document "click" handler)
          (fn [] (.removeEventListener js/document "click" handler))))
      [])

    (smart-render/render-smart-expense-form
      {:t t
       :ready? ready?
       :handle-submit handle-submit
       :error error
       :set-error! set-error!
       :phase phase
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
       :set-payer-id! set-payer-id!
       :set-purchased-at! set-purchased-at!
       :set-currency! set-currency!
      :set-expense-context-id! set-expense-context-id!
       :handle-input-change handle-input-change
       :handle-input-keydown handle-input-keydown
       :handle-select-result handle-select-result
       :handle-create-inline handle-create-inline
       :handle-type-pick handle-type-pick
       :remove-context! remove-context!
       :update-item! update-item!
       :remove-item! remove-item!
       :focus-input! focus-input!
       :set-type-picker-text! set-type-picker-text!
       :focus-item-id focus-item-id
       :set-focus-item-id! set-focus-item-id!
       :submitting? submitting?
       :submit-disabled? submit-disabled?
       :begin-context-phase! begin-context-phase!
       :on-cancel on-cancel
       :context-suggestions context-suggestions
       :currency-options currency-options
       :payers payers
       :payer-id payer-id
       :purchased-at purchased-at
       :context-initial-sub-stage context-initial-sub-stage
       :suppliers suppliers
       :phase-two-stores phase-two-stores
       :expense-categories expense-categories
      :expense-contexts expense-contexts
       :articles articles
       :set-context-initial-sub-stage! set-context-initial-sub-stage!
       :set-phase! set-phase!})))
