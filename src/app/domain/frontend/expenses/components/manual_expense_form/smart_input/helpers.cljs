(ns app.domain.frontend.expenses.components.manual-expense-form.smart-input.helpers
  "Pure helper functions for the smart expense input."
  (:require
    [app.domain.frontend.expenses.shared.manual-entry.core :as manual-entry]
    [clojure.string :as str]))

(def payer-default-id manual-entry/payer-default-id)

(def default-category-chip-to-preselect manual-entry/default-category-chip-to-preselect)

(def compute-items-total manual-entry/compute-items-total)

(def prepare-submit-items manual-entry/prepare-submit-items)

(def prepare-submit-values manual-entry/prepare-submit-values)

(defn entity-type-label
  "Translated entity type label."
  [t entity-type]
  (case entity-type
    :supplier (t :smart-expense/entity-supplier)
    :store    (t :smart-expense/entity-store)
    :category (t :smart-expense/entity-category)
    :expense-context (t :smart-expense/entity-expense-context)
    :article  (t :smart-expense/entity-article)
    :payer    (t :smart-expense/entity-payer)
    :date     (t :smart-expense/entity-date)
    :currency (t :smart-expense/entity-currency)
    ""))

(defn validate-form
  [t {:keys [items _context payer-id]}]
  (let [prepared (prepare-submit-items items)
        total (reduce + 0 (map :line_total prepared))]
    (cond
      (empty? prepared)
      {:ok? false :error (t :smart-expense/err-no-items)}

      (str/blank? (str payer-id))
      {:ok? false :error (t :smart-expense/err-no-payer)}

      (<= total 0)
      {:ok? false :error (t :smart-expense/err-no-total)}

      :else {:ok? true})))

(defn context-phase-initial-sub-stage
  "Resolve which phase-2 sub-stage should open first. An explicit requested
   sub-stage wins; otherwise existing supplier/store context opens directly
   into store-search and the default flow starts at defaults."
  [context requested-sub-stage]
  (or requested-sub-stage
    (when (or (:supplier context) (:store context))
      :store-search)
    :defaults))

(def context-search-order
  [:supplier :store :category])

(defn focused-search-types
  [context article-mode?]
  (if article-mode?
    [:article]
    (vec (concat
           (remove #(contains? context %) context-search-order)
           [:article]))))

(defn items-phase-quick-pick-types
  "Return which entity types should be surfaced as visible quick-pick chips
   in phase 1. From a blank state, suppliers and stores are intentionally
   excluded so that category and articles get the prominent slot. Once context
   narrows the search, keep the remaining context chips visible so a selected
   supplier can immediately surface its stores."
  [available-search-types context article-mode?]
  (cond
    article-mode?
    []

    (empty? context)
    [:category :article]

    :else
    (vec (remove #{:supplier} available-search-types))))

(defn items-phase-quick-pick-layout
  "Split phase-1 quick-pick groups into a top slot and the normal lower slot.
   When category is still missing, pin the category group near the context-chip
   area so dismissing the default category keeps the replacement chips in the
   same visual location."
  [focused-quick-pick-groups context]
  (let [top-groups (if (:category context)
                     []
                     (->> focused-quick-pick-groups
                       (filter #(= :category (:entity-type %)))
                       vec))
        inline-groups (if (seq top-groups)
                        (->> focused-quick-pick-groups
                          (remove #(= :category (:entity-type %)))
                          vec)
                        (vec focused-quick-pick-groups))]
    {:top-groups top-groups
     :inline-groups inline-groups}))

(defn phase-two-missing-context-types
  "Return the context types that phase 2 should keep available in the
   current sub-stage. Defaults keeps category selectable until chosen;
   store-search keeps supplier/store/category available until chosen."
  [context sub-stage]
  (case sub-stage
    :defaults
    (if (:category context)
      []
      [:category])

    :store-search
    (vec (remove #(contains? context %) context-search-order))

    (vec (remove #(contains? context %) context-search-order))))

(defn search-placeholder
  [t context active-search? article-mode?]
  (if article-mode?
    (t :smart-expense/article-mode-ph)
    (let [types (focused-search-types context false)
          labels (map #(entity-type-label t %) types)
          prefix (if active-search?
                   (t :smart-expense/search-prefix)
                   (t :smart-expense/start-with-prefix))
          or-conn (t :smart-expense/or-connector)]
      (str prefix
        (case (count labels)
          0 (entity-type-label t :article)
          1 (first labels)
          2 (str (first labels) or-conn (second labels))
          (str (str/join ", " (butlast labels)) or-conn (last labels)))
        "..."))))

(defn current-related-context
  [context]
  (cond
    (:store context) {:entity-type :store :entity-id (get-in context [:store :id])}
    (:supplier context) {:entity-type :supplier :entity-id (get-in context [:supplier :id])}
    :else nil))

(defn build-supplier-color-map
  "Build `{supplier-id-string → palette-slot}` so that supplier chips and
   their stores share a hue. Slots are assigned in first-seen supplier
   order, skipping missing ids and duplicate suppliers, so the first N
   valid suppliers get distinct colors and assignment stays stable across
   renders as long as the supplier list order does."
  [suppliers palette]
  (let [n (count palette)]
    (if (or (zero? n) (empty? suppliers))
      {}
      (->> suppliers
        (keep (fn [supplier]
                (some-> (:id supplier) str)))
        distinct
        (map-indexed (fn [i supplier-id]
                       [supplier-id (nth palette (mod i n))]))
        (into {})))))

(defn- store-supplier-id
  [item]
  (or (get-in item [:entity :supplier-id])
    (get-in item [:entity :supplier_id])
    (:supplier-id item)
    (:supplier_id item)))

(defn build-quick-pick-supplier-color-map
  "Build `{supplier-id-string → palette-slot}` from the suppliers that are
   actually visible in the current quick-pick groups. This keeps the
   displayed supplier chips distinct even when the full supplier list is
   longer than the palette and would otherwise wrap back onto the same
   colors."
  [groups palette]
  (let [n (count palette)
        supplier-ids (->> groups
                       (mapcat (fn [{:keys [entity-type items]}]
                                 (keep (fn [item]
                                         (case entity-type
                                           :supplier (some-> (:id item) str)
                                           :store (some-> (store-supplier-id item) str)
                                           nil))
                                   items)))
                       distinct)]
    (if (or (zero? n) (empty? supplier-ids))
      {}
      (->> supplier-ids
        (map-indexed (fn [i supplier-id]
                       [supplier-id (nth palette (mod i n))]))
        (into {})))))

(defn colorize-quick-pick-groups
  "Inject a per-item `:chip-class` so supplier rows and the store rows
   that belong to them share a hue. Categories/articles pass through
   untouched. Items whose supplier is missing from the color map (e.g.
   stores with no supplier id) are left without an override and fall
   back to the default chip style."
  [groups supplier-color-map]
  (mapv
    (fn [{:keys [entity-type items] :as group}]
      (assoc group :items
        (mapv
          (fn [item]
            (let [supplier-id-str (case entity-type
                                    :supplier (some-> (:id item) str)
                                    :store (some-> (store-supplier-id item) str)
                                    nil)
                  slot (when supplier-id-str
                         (get supplier-color-map supplier-id-str))
                  klass (when slot
                          (case entity-type
                            :supplier (:supplier slot)
                            :store (:store slot)
                            nil))]
              (cond-> item
                klass (assoc :chip-class klass))))
          items)))
    groups))

(defn supplier-chip-class
  "Return the supplier-variant chip class for the given supplier id, or
   nil if the id is not in the color map."
  [supplier-color-map supplier-id]
  (some-> (get supplier-color-map (some-> supplier-id str)) :supplier))

(defn store-chip-class
  "Return the store-variant chip class for the given supplier id, or nil
   if the supplier id is not in the color map."
  [supplier-color-map supplier-id]
  (some-> (get supplier-color-map (some-> supplier-id str)) :store))
