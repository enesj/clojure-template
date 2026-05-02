(ns app.domain.frontend.expenses.components.user-expense-form.normalization
  "Normalization + validation helpers for the user expense form.

  This namespace is shared between the different UI wrappers (create, edit,
  receipt approval)."
  (:require
    [app.domain.frontend.expenses.components.form-fields.helpers :refer [current-datetime-local
                                                                         format-decimal
                                                                         line-items-total
                                                                         new-line-item
                                                                         safe-parse-number]]
    [app.shared.adapters.normalization :as normalization]
    [clojure.string :as str]))

(def ^:private amount-tolerance 0.01)

(defn- pad-two
  [value]
  (let [s (str value)]
    (if (< (count s) 2) (str "0" s) s)))

(defn- datetime-local
  "Coerce an ISO-ish timestamp into a datetime-local input value (YYYY-MM-DDTHH:MM).

  Falls back to `current-datetime-local` when parsing fails and `fallback?` is true."
  ([value]
   (datetime-local value false))
  ([value fallback?]
   (let [s (some-> value str)]
     (cond
       (str/blank? s)
       (when fallback? (current-datetime-local))

       ;; Already looks like datetime-local
       (re-matches #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$" s)
       s

       ;; ISO-ish string (may include seconds / timezone). Prefer a real parse.
       :else
       (try
         (let [d (js/Date. s)
               t (.getTime d)]
           (if (js/isNaN t)
             (when fallback? (current-datetime-local))
             (str (.getFullYear d)
               "-" (pad-two (inc (.getMonth d)))
               "-" (pad-two (.getDate d))
               "T" (pad-two (.getHours d))
               ":" (pad-two (.getMinutes d)))))
         (catch :default _
           (when fallback? (current-datetime-local))))))))

(defn normalize-receipt-data
  "Normalize extracted receipt data into form initial values."
  [receipt]
  (let [receipt (normalization/convert-db-keys->app-keys receipt)
        extract0 (or (:raw-extract-json receipt)
                   (:receipts/raw-extract-json receipt))
        raw-extract (cond
                      (map? extract0) (normalization/convert-db-keys->app-keys extract0)
                      (nil? extract0) {}
                      (string? extract0) (try
                                           (normalization/convert-db-keys->app-keys
                                             (js->clj (js/JSON.parse extract0) :keywordize-keys true))
                                           (catch :default _ {}))
                      :else (normalization/convert-db-keys->app-keys
                              (js->clj extract0 :keywordize-keys true)))

        ;; Worker persists a wrapper map like:
        ;; {:provider ... :response ... :extraction {:merchant ... :totals ... :items ...}}
        extraction (or (:extraction raw-extract)
                     (:receipt-extraction raw-extract))
        extract (if (map? extraction) extraction raw-extract)

        totals (let [t (or (:totals extract)
                         (:totals-extract extract)
                         {})]
                 (if (map? t) t {}))

        items0 (or (:items extract)
                 (:line-items extract)
                 (:receipt-items extract))
        items (when (sequential? items0) items0)

        purchased-at (or (:purchased-at extract)
                       (:date extract)
                       (:purchased-at-guess receipt)
                       (:receipts/purchased-at-guess receipt))

        total-amount0 (or (:total-amount totals)
                        (:total totals)
                        (:total-amount extract)
                        (:total-amount-guess receipt)
                        (:receipts/total-amount-guess receipt))
        total-amount (safe-parse-number total-amount0)

        currency0 (or (:currency totals)
                    (:currency extract)
                    (:currency-guess receipt)
                    (:receipts/currency-guess receipt)
                    "BAM")
        currency (cond
                   (keyword? currency0) (name currency0)
                   (string? currency0) currency0
                   (some? currency0) (str currency0)
                   :else "BAM")

        supplier (or (:supplier-guess-supplier receipt)
                   (:receipts/supplier-guess-supplier receipt))
        supplier-id (when (map? supplier) (:id supplier))

        payer-id (or (:payer-id receipt)
                   (:payer_id receipt)
                   (:receipts/payer-id receipt)
                   (:receipts/payer_id receipt))

        normalize-item (fn [item]
                         (let [id (random-uuid)
                               raw-label (or (:raw-label item)
                                           (:label item)
                                           (:name item))
                               qty (safe-parse-number (:qty item))
                               unit (some-> (:unit item) str str/trim not-empty)
                               unit-price (safe-parse-number (:unit-price item))
                               line-total (safe-parse-number (:line-total item))]
                           (cond-> {:id (str id)
                                    :raw_label (or (some-> raw-label str) "")
                                    :qty (if (number? qty) (str qty) "")
                                    :unit_price (if (number? unit-price) (format-decimal unit-price) "")
                                    :line_total (if (number? line-total) (format-decimal line-total) "")
                                    :line_total_auto? true}
                             unit (assoc :unit unit))))

        filename (or (:original-filename receipt)
                   (:storage-key receipt)
                   "(unknown)")

        receipt-category-id (or (:expense-category-id receipt)
                              (:expense_category_id receipt)
                              (:receipts/expense-category-id receipt))
        receipt-context-id (or (:expense-context-id receipt)
                 (:expense_context_id receipt)
                 (:receipts/expense-context-id receipt))
        receipt-notes (or (:notes receipt)
                        (:receipts/notes receipt))]
    {:supplier_id supplier-id
     :payer_id payer-id
     :expense_category_id receipt-category-id
           :expense_context_id receipt-context-id
     :purchased_at (datetime-local purchased-at true)
     :total_amount (if (number? total-amount) (format-decimal total-amount) "")
     :currency currency
     :notes (or (some-> receipt-notes str not-empty)
              (str "Extracted from receipt: " filename))
     :items (if (seq items)
              (mapv normalize-item items)
              [(new-line-item)])}))

(defn prepare-line-items
  "Internal helper: prepare/validate raw line items from the UI.

  Keeps items that have a non-blank label and a parseable line_total.
  Coerces numeric fields where possible."
  [items]
  (keep (fn [{:keys [id raw_label qty unit unit_price line_total]}]
          (let [parsed-total (safe-parse-number line_total)
                qty-num (safe-parse-number qty)
                unit-num (safe-parse-number unit_price)
                unit* (some-> unit str str/trim not-empty)
                raw-label* (some-> raw_label str)
                id* (some-> id str)]
            (when (and (not (str/blank? raw-label*)) (number? parsed-total))
              (cond-> {:raw_label raw-label*
                       :line_total parsed-total}
                (and id* (not (str/blank? id*))) (assoc :id id*)
                (number? qty-num) (assoc :qty qty-num)
                unit* (assoc :unit unit*)
                (number? unit-num) (assoc :unit_price unit-num)))))
    (or items [])))

(defn normalize-initial-data
  "Normalize an expense entity (from the shared entity store) into form initial values."
  [expense]
  (let [expense (normalization/convert-db-keys->app-keys expense)]
    (letfn [(normalize-line-item [item]
              (let [item (if (map? item) item {})
                    id (or (:id item)
                         (:expense-item-id item)
                         (random-uuid))
                    raw-label (or (:raw-label item) "")
                    qty (:qty item)
                    unit (some-> (:unit item) str str/trim not-empty)
                    unit-price (:unit-price item)
                    line-total (:line-total item)
                    auto? (if (contains? item :line-total-auto?)
                            (not (false? (:line-total-auto? item)))
                            true)]
                (cond-> {:id (str id)
                         :raw_label (if (some? raw-label) (str raw-label) "")
                         :qty (cond
                                (string? qty) qty
                                (number? qty) (str qty)
                                (nil? qty) ""
                                :else (str qty))
                         :unit_price (cond
                                       (string? unit-price) unit-price
                                       (number? unit-price) (format-decimal unit-price)
                                       (nil? unit-price) ""
                                       :else (str unit-price))
                         :line_total (cond
                                       (string? line-total) line-total
                                       (number? line-total) (format-decimal line-total)
                                       (nil? line-total) ""
                                       :else (str line-total))
                         :line_total_auto? auto?}
                  unit (assoc :unit unit))))]
      (let [supplier-id (or (:supplier-id expense) (:expenses/supplier-id expense))
            payer-id (or (:payer-id expense) (:expenses/payer-id expense))
            expense-category-id (or (:expense-category-id expense) (:expenses/expense-category-id expense))
            expense-context-id (or (:expense-context-id expense) (:expenses/expense-context-id expense))
            purchased-at (or (:purchased-at expense) (:expenses/purchased-at expense))
            total-amount (or (:total-amount expense) (:expenses/total-amount expense))
            currency (or (:currency expense) "BAM")
            notes (or (:notes expense) "")
            items (or (:items expense) (:expenses/items expense) [])
            normalized-items (if (seq items)
                               (mapv normalize-line-item items)
                               [(new-line-item)])]
        {:supplier_id supplier-id
         :payer_id payer-id
         :expense_category_id expense-category-id
         :expense_context_id expense-context-id
         :purchased_at (datetime-local purchased-at true)
         :total_amount total-amount
         :currency currency
         :notes notes
         :items normalized-items}))))

(defn validate-expense-values
  "Validate expense form values. Returns {:ok? true} or {:ok? false :error \"...\"}."
  [values]
  (let [supplier-id (:supplier_id values)
        payer-id (:payer_id values)
        purchased-at (:purchased_at values)
        prepared-items (vec (prepare-line-items (:items values)))
        computed-total (line-items-total prepared-items)
        parsed-total (safe-parse-number (:total_amount values))
        effective-total (or parsed-total (when (pos? computed-total) computed-total))
        total-diff (when (and (number? parsed-total)
                           (number? computed-total)
                           (pos? computed-total))
                     (js/Math.abs (- parsed-total computed-total)))
        total-mismatch? (and total-diff (> total-diff amount-tolerance))]
    (cond
      (or (str/blank? (str supplier-id))
        (str/blank? (str payer-id))
        (str/blank? (str purchased-at)))
      {:ok? false :error "Supplier, payer, and date are required."}

      (empty? prepared-items)
      {:ok? false :error "Add at least one line item with a label and total."}

      (or (nil? effective-total) (<= effective-total 0))
      {:ok? false :error "Enter a total amount greater than 0."}

      total-mismatch?
      {:ok? false
       :error (str "Total (" (or (format-decimal effective-total) effective-total)
                ") must match line items (" (or (format-decimal computed-total) computed-total) ").")}

      :else
      {:ok? true})))

(defn validate-receipt-review-values
  "Validate values for saving reviewed receipt data.

  Differs from `validate-expense-values` by NOT requiring expense-only fields
  (e.g. payer)."
  [values]
  (let [supplier-id (:supplier_id values)
        purchased-at (:purchased_at values)
        prepared-items (vec (prepare-line-items (:items values)))
        computed-total (line-items-total prepared-items)
        parsed-total (safe-parse-number (:total_amount values))
        effective-total (or parsed-total (when (pos? computed-total) computed-total))
        total-diff (when (and (number? parsed-total)
                           (number? computed-total)
                           (pos? computed-total))
                     (js/Math.abs (- parsed-total computed-total)))
        total-mismatch? (and total-diff (> total-diff amount-tolerance))]
    (cond
      (or (str/blank? (str supplier-id))
        (str/blank? (str purchased-at)))
      {:ok? false :error "Supplier and date are required."}

      (empty? prepared-items)
      {:ok? false :error "Add at least one line item with a label and total."}

      (or (nil? effective-total) (<= effective-total 0))
      {:ok? false :error "Enter a total amount greater than 0."}

      total-mismatch?
      {:ok? false
       :error (str "Total (" (or (format-decimal effective-total) effective-total)
                ") must match line items (" (or (format-decimal computed-total) computed-total) ").")}

      :else
      {:ok? true})))

(defn prepare-expense-submit-values
  "Prepare expense form values for submission."
  [values]
  (let [prepared-items (vec (prepare-line-items (:items values)))
        computed-total (line-items-total prepared-items)
        parsed-total (safe-parse-number (:total_amount values))
        effective-total (or parsed-total (when (pos? computed-total) computed-total))
        expense-context-id (some-> (:expense_context_id values) str str/trim not-empty)]
    {:supplier_id (:supplier_id values)
     :payer_id (:payer_id values)
     :expense_category_id (:expense_category_id values)
      :expense_context_id expense-context-id
     :purchased_at (:purchased_at values)
     :currency (:currency values)
     :notes (:notes values)
     :total_amount effective-total
     :items prepared-items}))

(defn receipt-review-changed?
  "Return true when the current review values differ from the initial review values."
  [initial-values current-values]
  (not= (prepare-expense-submit-values initial-values)
    (prepare-expense-submit-values current-values)))