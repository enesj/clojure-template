(ns app.domain.frontend.expenses.components.manual-expense-form.smart-input.components
  "Sub-components for the smart expense input."
  (:require
    [app.domain.frontend.expenses.components.form-fields.helpers
     :refer [format-decimal safe-parse-number]]
    [app.domain.frontend.expenses.components.manual-expense-form.search :as search]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.constants
     :refer [chip-styles type-button-styles]]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.helpers
     :refer [entity-type-label]]
    [app.template.frontend.ui.z-scale :as z]
    [clojure.string :as str]
    ["react-dom" :as react-dom]
    [uix.core :refer [$ defui use-effect use-ref use-state]]))

(defui entity-chip
  [{:keys [entity-type label on-remove size tooltip]}]
  (let [{:keys [icon]} (search/entity-type-info entity-type)
        style-class (get chip-styles entity-type "bg-base-200 text-base-content")
        large? (not= size :sm)]
    ($ :span {:class (str "inline-flex items-center gap-2 rounded-full font-medium border "
                       "transition-all select-none "
                       (if large? "px-4 py-2.5 text-base " "px-3 py-1.5 text-sm ")
                       style-class)
              :title tooltip}
      ($ :span {:class (if large? "text-lg" "text-base")} icon)
      ($ :span label)
      (when on-remove
        ($ :button {:type "button"
                    :class "ml-1 opacity-60 hover:opacity-100 text-lg leading-none transition-opacity"
                    :on-click (fn [e] (.preventDefault e) (.stopPropagation e) (on-remove))}
          "\u00D7")))))

(defui autocomplete-dropdown
  [{:keys [t results loading? highlight-idx on-select on-create input-text anchor-ref]}]
  (let [visible-results (take 10 results)
        [pos set-pos!] (use-state nil)]

    ;; Measure anchor position whenever dropdown content changes
    (use-effect
      (fn []
        (when-let [el (and anchor-ref @anchor-ref)]
          (let [rect (.getBoundingClientRect el)]
            (set-pos! {:top (+ (.-bottom rect) 8)
                       :left (.-left rect)
                       :width (.-width rect)})))
        js/undefined)
      [anchor-ref (count visible-results) loading? input-text])

    (when (and pos
            (or (seq visible-results) loading? (and input-text (not (str/blank? input-text)))))
      (when-let [portal-target (.-body js/document)]
        (react-dom/createPortal
          ($ :div {:class (str "bg-white rounded-2xl shadow-2xl border border-base-200 "
                            "max-h-[600px] overflow-y-auto")
                   :style {:position "fixed"
                           :z-index z/modal-portal-child
                           :top (str (:top pos) "px")
                           :left (str (:left pos) "px")
                           :width (str (:width pos) "px")}
                   :on-click (fn [e] (.stopPropagation e))}
            ;; Loading indicator
            (when loading?
              ($ :div {:class "flex items-center gap-3 px-5 py-4 text-base-content/40"}
                ($ :span {:class "ds-loading ds-loading-spinner ds-loading-sm"})
                ($ :span {:class "text-base"} (t :smart-expense/searching))))

            ;; Search results (capped at 10)
            (when (seq visible-results)
              (for [[idx result] (map-indexed vector visible-results)]
                (let [etype (keyword (or (:entity-type result) (:entity_type result)))
                      {:keys [icon]} (search/entity-type-info etype)
                      etype-label (entity-type-label t etype)
                      highlighted? (= idx highlight-idx)]
                  ($ :button {:key (str (name etype) "-" (:id result))
                              :type "button"
                              :class (str "w-full text-left px-5 py-4 flex items-center gap-4 "
                                       "transition-colors cursor-pointer "
                                       (if highlighted?
                                         "bg-primary/10"
                                         "hover:bg-base-100"))
                              :on-mouse-enter (fn [_])
                              :on-click (fn [e] (.preventDefault e) (.stopPropagation e)
                                          (on-select (assoc result :entity-type etype)))}
                    ($ :span {:class "text-2xl flex-none w-8 text-center"} icon)
                    ($ :span {:class "flex-1 text-lg font-medium truncate"} (:label result))
                    (when-let [price (search/result-last-price result)]
                      (let [global-price? (search/global-last-price? result)
                            tooltip (when (search/global-last-price? result)
                                      (when-let [sname (search/result-last-price-supplier-name result)]
                                        (t :smart-expense/price-from sname)))]
                        ($ :span {:class (str "text-sm font-mono "
                                           (if global-price?
                                             "text-amber-600 font-semibold"
                                             "text-base-content/50")
                                           (when tooltip " cursor-help"))
                                  :title tooltip}
                          (format-decimal price))))
                    ($ :span {:class (str "text-xs px-2.5 py-1 rounded-full border font-medium "
                                       (get chip-styles etype ""))}
                      etype-label)))))

            ;; "Create new" option
            (when (and input-text (not (str/blank? input-text)))
              ($ :button {:type "button"
                          :class (str "w-full text-left px-5 py-4 flex items-center gap-4 "
                                   "border-t border-base-200 transition-colors cursor-pointer "
                                   (if (= highlight-idx (count visible-results))
                                     "bg-primary/10"
                                     "hover:bg-base-100"))
                          :on-click (fn [e] (.preventDefault e) (.stopPropagation e)
                                      (on-create input-text))}
                ($ :span {:class "text-2xl flex-none w-8 text-center text-primary"} "+")
                ($ :span {:class "text-lg"}
                  (t :smart-expense/create-prefix)
                  ($ :span {:class "font-bold"} (str "\u201C" input-text "\u201D"))))))
          portal-target)))))

(defui type-picker
  "Picker shown when user types a name that doesn't match existing entities.
   `allowed-types` optionally restricts which entity types are shown."
  [{:keys [t text on-pick on-cancel creating? allowed-types]}]
  ($ :div {:class "mt-4 p-6 bg-base-100 rounded-2xl border border-base-200"}
    ($ :p {:class "text-lg text-base-content/70 mb-4"}
      (t :smart-expense/what-is (str "\u201C" text "\u201D")))
    ($ :div {:class "flex flex-wrap gap-3"}
      (for [entity-type (if (seq allowed-types)
                          allowed-types
              [:article :supplier :store :category :expense-context])]
        (let [{:keys [icon]} (search/entity-type-info entity-type)
              btn-class (get type-button-styles entity-type)]
          ($ :button {:key (name entity-type)
                      :type "button"
                      :disabled creating?
                      :class (str "px-6 py-3.5 text-lg rounded-xl font-medium border "
                               "transition-all flex items-center gap-2 "
                               (when creating? "opacity-40 cursor-not-allowed ")
                               btn-class)
                      :on-click (fn [e] (.preventDefault e) (.stopPropagation e)
                                  (on-pick entity-type text))}
            (if creating?
              ($ :span {:class "ds-loading ds-loading-spinner ds-loading-xs"})
              ($ :span icon))
            ($ :span (entity-type-label t entity-type))))))
    ($ :button {:type "button"
                :class "mt-3 text-sm text-base-content/50 hover:text-base-content/70 transition-colors"
                :on-click (fn [e] (.preventDefault e) (on-cancel))}
      (t :smart-expense/cancel))))

(defn top-items-for-type
  "Return up to `n` items for the given entity type from local data."
  [entity-type suppliers stores expense-categories expense-contexts articles n selected-supplier-id]
  (let [name-fn (get-in search/entity-type-config [entity-type :name-fn])
        base-items (case entity-type
                     :supplier suppliers
                     :store (if selected-supplier-id
                              (filter #(= (str selected-supplier-id)
                                         (str (or (:supplier-id %) (:supplier_id %))))
                                stores)
                              stores)
                     :category expense-categories
                     :expense-context expense-contexts
                     :article articles
                     [])]
    (->> base-items
      (take n)
      (mapv (fn [item]
              (let [last-price (or (:last-price item) (:last_price item))
                    last-price-source (or (:last-price-source item) (:last_price_source item))
                    last-price-supplier-name (or (:last-price-supplier-display-name item)
                                               (:last_price_supplier_display_name item))]
                (cond-> {:id (:id item)
                         :label (or (when name-fn (name-fn item)) "")
                         :entity-type entity-type
                         :entity item}
                  (some? last-price) (assoc :last-price last-price)
                  last-price-source (assoc :last-price-source last-price-source)
                  last-price-supplier-name (assoc :last-price-supplier-display-name last-price-supplier-name))))))))

(defn build-quick-pick-groups
  [missing-types suppliers stores expense-categories expense-contexts articles selected-supplier-id]
  (let [limit (if (= 1 (count missing-types)) 10 5)]
    (->> missing-types
      (map (fn [entity-type]
             {:entity-type entity-type
              :items (top-items-for-type entity-type
                       suppliers
                       stores
                       expense-categories
                       expense-contexts
                       articles
                       limit
                       selected-supplier-id)}))
      (filter #(seq (:items %)))
      vec)))

(defn- dedupe-by
  [key-fn items]
  (let [seen (volatile! #{})]
    (->> items
      (keep (fn [item]
              (let [k (key-fn item)]
                (when-not (contains? @seen k)
                  (vswap! seen conj k)
                  item))))
      vec)))

(defn- store-supplier-id
  [store]
  (or (:supplier-id store)
    (:supplier_id store)
    (get-in store [:entity :supplier-id])
    (get-in store [:entity :supplier_id])))

(defn- store-visible-label
  [store]
  (or (:label store)
    (:address store)
    (:display-name store)
    (:display_name store)
    ""))

(defn- store-visible-dedupe-key
  [store]
  (let [supplier-id (some-> (store-supplier-id store) str)
        label (some-> (store-visible-label store) str str/trim str/lower-case)]
    (if (str/blank? label)
      (or (some-> (:id store) str)
        (str supplier-id "::missing-label"))
      (str supplier-id "::" label))))

(defn phase-two-quick-pick-groups
  [missing-types context-suggestions suppliers stores expense-categories expense-contexts articles selected-supplier-id]
  (let [limit (if (= 1 (count missing-types)) 10 5)
        local-groups-by-type (->> (build-quick-pick-groups missing-types
                                    suppliers
                                    stores
                                    expense-categories
                                    expense-contexts
                                    articles
                                    selected-supplier-id)
                               (map (juxt :entity-type identity))
                               (into {}))
        supplier-name-by-id (->> suppliers
                              (keep (fn [supplier]
                                      (let [supplier-id (some-> (:id supplier) str)
                                            supplier-label (or (:label supplier)
                                                             (:display-name supplier)
                                                             (:display_name supplier))]
                                        (when (and supplier-id
                                                (not (str/blank? (str supplier-label))))
                                          [supplier-id supplier-label]))))
                              (into {}))
        store-matches-supplier? (fn [s]
                                  (or (nil? selected-supplier-id)
                                    (= (str selected-supplier-id)
                                      (str (store-supplier-id s)))))
        wrap-store (fn [s]
                     {:id (:id s)
                      :label (store-visible-label s)
                      :entity-type :store
                      :entity s})
        wrap-supplier (fn [supplier]
                        {:id (:id supplier)
                         :label (or (:label supplier)
                                  (:display-name supplier)
                                  (:display_name supplier)
                                  "")
                         :entity-type :supplier
                         :entity supplier})
        context-store-items (->> (:stores context-suggestions)
                              (filter store-matches-supplier?)
                              (mapv wrap-store))
        pool-store-items (->> stores
                           (filter store-matches-supplier?)
                           (mapv wrap-store))
        store-candidates (cond
                           (seq context-store-items)
                           (if selected-supplier-id
                             (concat context-store-items pool-store-items)
                             context-store-items)

                           :else
                           pool-store-items)
        merged-store-items (->> store-candidates
                             (dedupe-by store-visible-dedupe-key)
                             (take limit)
                             vec)
        inferred-supplier-items (->> merged-store-items
                                  (keep (fn [store]
                                          (let [supplier-id (some-> (store-supplier-id store) str)
                                                supplier-label (or (get-in store [:entity :supplier-display-name])
                                                                 (get-in store [:entity :supplier_display_name])
                                                                 (get supplier-name-by-id supplier-id))]
                                            (when (and supplier-id
                                                    (not (str/blank? (str supplier-label))))
                                              {:id supplier-id
                                               :label supplier-label
                                               :entity-type :supplier
                                               :entity {:id supplier-id
                                                        :display_name supplier-label}}))))
                                  (dedupe-by #(some-> (:id %) str)))
        merged-supplier-items (->> (concat
                                     (mapv wrap-supplier (:suppliers context-suggestions))
                                     inferred-supplier-items)
                                (dedupe-by #(some-> (:id %) str))
                                (take limit)
                                vec)]
    (->> missing-types
      (keep (fn [entity-type]
              (let [raw-suggested (case entity-type
                                    :supplier (:suppliers context-suggestions)
                                    :store (:stores context-suggestions)
                                    :category (:categories context-suggestions)
                                    :expense-context (:expense-contexts context-suggestions)
                                    [])]
                (cond
                  ;; Suppliers: prefer article-context suggestions and backfill the
                  ;; owners of whatever store chips are currently visible so colors
                  ;; stay paired on-screen.
                  (= entity-type :supplier)
                  (when (seq merged-supplier-items)
                    {:entity-type :supplier
                     :items merged-supplier-items})

                  ;; Stores: with no selected supplier, keep phase-2 focused on the
                  ;; article-context matches only. Once a supplier is selected,
                  ;; allow its broader branch pool to fill the remaining slots.
                  (= entity-type :store)
                  (when (seq merged-store-items)
                    {:entity-type :store :items merged-store-items})

                  ;; Categories: merge suggestions with full local pool
                  ;; so the user always sees all available categories,
                  ;; not just the context-suggested subset.
                  (= entity-type :category)
                  (let [wrap-cat (fn [c]
                                   {:id (:id c)
                                    :label (or (:label c) (:name c) "")
                                    :entity-type :category
                                    :entity c})
                        history-items (mapv wrap-cat raw-suggested)
                        pool-items (mapv wrap-cat expense-categories)
                        merged (->> (concat history-items pool-items)
                                 (dedupe-by #(some-> (:id %) str))
                                 vec)]
                    (when (seq merged)
                      {:entity-type :category :items merged}))

                  (= entity-type :expense-context)
                  (let [wrap-context (fn [c]
                                       {:id (:id c)
                                        :label (or (:label c) (:name c) "")
                                        :entity-type :expense-context
                                        :entity c})
                        history-items (mapv wrap-context raw-suggested)
                        pool-items (mapv wrap-context expense-contexts)
                        merged (->> (concat history-items pool-items)
                                 (dedupe-by #(some-> (:id %) str))
                                 vec)]
                    (when (seq merged)
                      {:entity-type :expense-context :items merged}))

                  ;; Other types keep the existing precedence: history
                  ;; suggestions win when present, otherwise fall back
                  ;; to the local quick picks.
                  (seq raw-suggested)
                  {:entity-type entity-type
                   :items (mapv (fn [suggestion]
                                  {:id (:id suggestion)
                                   :label (:label suggestion)
                                   :entity-type entity-type
                                   :entity suggestion})
                            raw-suggested)}

                  :else
                  (get local-groups-by-type entity-type)))))
      vec)))

(defui quick-picks
  "Shows top N options as clickable chips when only one entity type is missing."
  [{:keys [entity-type items on-select]}]
  (let [{:keys [icon]} (search/entity-type-info entity-type)
        default-style (get chip-styles entity-type "bg-base-200 text-base-content")]
    (when (seq items)
      ($ :div {:class "flex flex-wrap gap-2"}
        (for [{:keys [id label chip-class] :as item} items]
          ($ :button {:key (str (name entity-type) "-" id)
                      :type "button"
                      :class (str "inline-flex items-center gap-2 px-4 py-2.5 rounded-full "
                               "text-base font-medium border cursor-pointer "
                               "transition-all hover:shadow-md hover:scale-[1.02] "
                               (or chip-class default-style))
                      :on-click (fn [e] (.preventDefault e) (.stopPropagation e)
                                  (on-select item))}
            ($ :span icon)
            ($ :span {:class "truncate max-w-[200px]"} label)))))))

(defui item-row
  "A single item row with inline qty and price inputs."
  [{:keys [item on-change on-remove on-enter-price auto-focus-qty? on-focus-handled]}]
  (let [{:keys [id label qty unit-price]} item
        qty-ref (use-ref nil)
        price-ref (use-ref nil)
        ;; Latest-callback ref: lets the use-effect read the current
        ;; `on-focus-handled` without listing it as a dep (it's recreated
        ;; every parent render, so adding it would re-run the effect on
        ;; every render).
        handled-ref (use-ref nil)
        line-total (* (or (safe-parse-number qty) 1)
                     (or (safe-parse-number unit-price) 0))
        input-class (str "text-center text-lg font-semibold p-2 h-12 rounded-lg "
                      "border-2 border-base-300 bg-white "
                      "focus:border-primary focus:outline-none focus:shadow-sm "
                      "transition-colors")
        label-class "text-sm font-bold text-base-content/60 select-none"]
    (reset! handled-ref on-focus-handled)
    ;; One-shot focus signal from parent: when this row is the freshly-added
    ;; item, focus the qty input and select its value so the user can type the
    ;; new quantity immediately, then notify the parent to clear the signal.
    (use-effect
      (fn []
        (when (and auto-focus-qty? @qty-ref)
          (js/setTimeout
            (fn []
              (when-let [el @qty-ref]
                (.focus el)
                (when (.-select el) (.select el))))
            30)
          (when-let [cb @handled-ref] (cb)))
        js/undefined)
      [auto-focus-qty?])
    ($ :div {:class (str "flex items-center gap-3 py-3 px-4 bg-base-100 rounded-xl "
                      "border-2 border-base-200 group")}
      ;; Article icon + name
      ($ :span {:class "text-xl flex-none"} "📦")
      ($ :span {:class "flex-1 text-base font-medium truncate min-w-0"} label)

      ;; Qty input
      ($ :div {:class "flex items-center gap-2"}
        ($ :label {:class label-class} "\u00D7")
        ($ :input {:ref qty-ref
                   :type "number"
                   :step "1"
                   :min "0"
                   :class (str "w-20 " input-class)
                   :value (str qty)
                   :on-focus (fn [e] (when-let [el (.-target e)]
                                       (when (.-select el) (.select el))))
                   :on-change (fn [e] (on-change id :qty (.. e -target -value)))
                   :on-key-down (fn [e]
                                  (when (= (.-key e) "Enter")
                                    (.preventDefault e)
                                    (when-let [el @price-ref]
                                      (.focus el)
                                      (when (.-select el) (.select el)))))}))

      ;; Price input
      ($ :div {:class "flex items-center gap-2"}
        ($ :label {:class label-class} "@")
        ($ :input {:ref price-ref
                   :type "number"
                   :step "0.01"
                   :min "0"
                   :class (str "w-28 " input-class)
                   :value (str unit-price)
                   :on-focus (fn [e] (when-let [el (.-target e)]
                                       (when (.-select el) (.select el))))
                   :on-change (fn [e] (on-change id :unit-price (.. e -target -value)))
                   :on-key-down (fn [e]
                                  (when (= (.-key e) "Enter")
                                    (.preventDefault e)
                                    (when on-enter-price (on-enter-price))))}))

      ;; Line total display
      ($ :span {:class "text-base font-mono font-bold text-base-content w-24 text-right"}
        (when (pos? line-total) (format-decimal line-total)))

      ;; Remove button
      ($ :button {:type "button"
                  :class (str "opacity-0 group-hover:opacity-60 hover:!opacity-100 "
                           "text-lg leading-none transition-opacity ml-1")
                  :on-click (fn [e] (.preventDefault e) (.stopPropagation e) (on-remove id))}
        "\u00D7"))))
