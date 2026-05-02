(ns app.domain.frontend.expenses.shared.manual-entry.core
  "Shared pure helpers for manual expense entry across web and mobile."
  (:require
    [app.domain.frontend.expenses.components.form-fields.helpers
     :refer [safe-parse-number]]
    [clojure.string :as str]))

(def unknown-supplier-id "00000000-0000-0000-0000-000000000001")
(def unknown-store-id "00000000-0000-0000-0000-000000000002")

(defn payer-default-id
  ([payers]
   (payer-default-id payers nil))
  ([payers preferred-payer-id]
   (let [preferred-id (some-> preferred-payer-id str str/trim not-empty)]
     (or preferred-id
       (some #(when (or (:is_default %) (:is-default %) (:isDefault %)) (:id %)) payers)
       (:id (first payers))))))

(defn expense-category-default-id
  [expense-categories _settings]
  (some (fn [category]
          (when (or (:is-default category) (:isDefault category))
            (:id category)))
    (or expense-categories [])))

(defn default-category-chip-to-preselect
  "Return the normalized default category chip to auto-preselect, or nil
   when auto-preselection is disabled, a category is already selected, or
   no default category exists."
  [expense-categories profile-settings default-category-preselect-enabled? selected-category]
  (let [default-category-id (some-> (expense-category-default-id expense-categories profile-settings)
                              str
                              str/trim
                              not-empty)]
    (when (and default-category-preselect-enabled?
            (nil? selected-category)
            default-category-id)
      (some (fn [category]
              (when (= default-category-id (some-> (:id category) str))
                {:id (:id category)
                 :label (or (:name category) "")}))
        expense-categories))))

(defn item-total
  [item]
  (let [qty (or (safe-parse-number (:qty item)) 1)
        price (or (safe-parse-number (:unit-price item)) 0)]
    (* qty price)))

(defn compute-items-total
  "Sum line totals for all items."
  [items]
  (reduce (fn [acc item]
            (+ acc (item-total item)))
    0
    items))

(defn article-ids-from-items
  [items]
  (->> items
    (keep :article-id)
    vec))

(defn prepare-submit-items
  "Convert internal items to backend-expected format."
  [items]
  (vec
    (keep (fn [{:keys [label qty unit-price]}]
            (let [q (or (safe-parse-number qty) 1)
                  p (or (safe-parse-number unit-price) 0)
                  total (* q p)]
              (when (and (not (str/blank? (str label))) (pos? total))
                {:raw_label (str label)
                 :qty q
                 :unit_price p
                 :line_total total})))
      items)))

(defn prepare-submit-values
  [{:keys [items context currency purchased-at payer-id notes]}]
  (let [prepared (prepare-submit-items items)
        total (reduce + 0 (map :line_total prepared))]
    (cond-> {:payer_id payer-id
             :purchased_at purchased-at
             :currency currency
             :total_amount total
             :items prepared}
      (:supplier context) (assoc :supplier_id (get-in context [:supplier :id]))
      (:store context) (assoc :store_id (get-in context [:store :id]))
      (:category context) (assoc :expense_category_id (get-in context [:category :id]))
      (:expense-context context) (assoc :expense_context_id (get-in context [:expense-context :id]))
      (not (str/blank? notes)) (assoc :notes notes))))

(defn ensure-unknown-context
  ([context]
   (ensure-unknown-context context {}))
  ([context {:keys [supplier-label store-label]
             :or {supplier-label "Unknown supplier"
                  store-label "Unknown store"}}]
   (cond-> (or context {})
     (not (:supplier context))
     (assoc :supplier {:id unknown-supplier-id
                       :label supplier-label})

     (not (:store context))
     (assoc :store {:id unknown-store-id
                    :label store-label}))))