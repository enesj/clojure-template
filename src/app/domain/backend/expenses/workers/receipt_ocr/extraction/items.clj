(ns app.domain.backend.expenses.workers.receipt-ocr.extraction.items
  (:require
    [app.domain.backend.expenses.workers.receipt-ocr.common :as common]
    [app.domain.backend.expenses.workers.receipt-ocr.extraction.shape :as shape]
    [clojure.string :as str]))

(def discount-label-pattern
  #"(?iu)\b(popust|popost|rabat|discount|akcija)\b")

(def totals-label-pattern
  #"(?iuU)\b(ukupno|ukupan|ukupna|total|subtotal|iznos|bez\s+poreza|za\s+uplatu|za\s+pla[ćc]anje|upla[ćc]eno|primljeno|gotovina|kartica|kusur|povrat|razlika|saldo|укупно|укупан|укупна|износ|без\s+пореза|за\s+уплату|за\s+пла[ћч]ање|упла[ћч]ено|примљено|готовина|картица|кусур|поврат|разлика|салдо)\b")

(def tax-label-pattern
  #"(?iuU)\b(pdv|vat|porez|osnovica|stopa|(?:osn|csn)\.?|pou|пдв|порез|основица|стопа)\b")

(def payment-label-pattern
  #"(?iuU)\b(gotovina|kartica|visa|mastercard|diners|amex|american\s+express|paypal|готовина|картица|виза|мастеркард|динерс|амекс)\b")

(def meta-label-pattern
  #"(?iu)\b(ra[čc]un|fiskalni|kasa|kasir|operator|datum|vrijeme|broj|id)\b")

(def meta-reference-number-pattern
  #"(?iu)\bbr(?:\.|:)\s*[:#-]?\s*[\p{L}\p{N}/-]+\b")

(def header-label-pattern
  #"(?iu)^(?:naziv|opis|artik(?:al|l|la|li)?|šifra|sifra|kol(?:\.|i[čc]ina)?|qty|quantity|cijena|price|jed(?:\.|inica)?)(?:[\s\|/:;,-]+(?:naziv|opis|artik(?:al|l|la|li)?|šifra|sifra|kol(?:\.|i[čc]ina)?|qty|quantity|cijena|price|jed(?:\.|inica)?))*$")

(defn normalize-item-label
  [raw-label]
  (when-let [s (some-> raw-label str str/trim not-empty)]
    (-> s
      str/lower-case
      (str/replace #"[,:;]+" " ")
      (str/replace #"\s+" " ")
      str/trim)))

(defn- label-matches?
  [pattern label]
  (boolean
    (and (seq label)
      (re-find pattern label))))

(defn- discount-row?
  [label]
  (label-matches? discount-label-pattern label))

(defn- vat-percent-row?
  [label]
  (and (seq label)
    (str/includes? label "%")
    (boolean (re-find #"(?iu)\b(v\.?|pdv|vat|porez)\b" label))))

(defn- likely-grand-total-row?
  [{:keys [items-count grand-total]} raw-label line-total]
  (and (some? grand-total)
    (some? line-total)
    (> (long (or items-count 0)) 3)
    (<= (shape/abs-decimal-diff line-total grand-total) 0.01)
    (let [raw (some-> raw-label str str/trim)
          label (normalize-item-label raw)
          short? (<= (count (or label "")) 8)
          colon? (boolean (and raw (str/ends-with? raw ":")))]
      (or (label-matches? totals-label-pattern label)
        colon?
        short?))))

(defn- abbrev-summary-row?
  [{:keys [items-count]} raw-label label line-total]
  (let [raw (some-> raw-label str str/trim)]
    (and (some? line-total)
      (> (long (or items-count 0)) 3)
      (seq raw)
      (str/ends-with? raw ":")
      (seq label)
      (let [compact (-> label
                      (str/replace #"[\s\.]" "")
                      (str/trim))]
        (and (<= (count compact) 6)
          (boolean (re-matches #"(?iu)\p{L}+" compact)))))))

(defn non-item-reason
  [ctx item]
  (let [raw-label (:raw_label item)
        label (normalize-item-label raw-label)
        line-total (common/parse-money (:line_total item))]
    (cond
      (str/blank? (or label "")) :blank-label
      (nil? line-total) :missing-line-total
      (label-matches? header-label-pattern label) :header
      (or (label-matches? meta-label-pattern label)
        (label-matches? meta-reference-number-pattern raw-label)) :metadata
      (or (= "maestro" label)
        (label-matches? payment-label-pattern label)) :payment
      (label-matches? tax-label-pattern label) :tax
      (vat-percent-row? label) :tax
      (abbrev-summary-row? ctx raw-label label line-total) :metadata
      (likely-grand-total-row? ctx raw-label line-total) :grand-total
      (label-matches? totals-label-pattern label) :totals
      :else nil)))

(defn- discount-override?
  [prev-item discount-item]
  (let [prev-total (common/parse-money (:line_total prev-item))
        disc-total (common/parse-money (:line_total discount-item))
        prev-label (normalize-item-label (:raw_label prev-item))
        diff (when (and prev-total disc-total)
               (shape/abs-decimal-diff prev-total disc-total))]
    (and prev-total
      disc-total
      (not (discount-row? prev-label))
      (< (double disc-total) (double prev-total))
      (some? diff)
      (<= (double diff) (* 0.6 (double prev-total))))))

(defn- apply-discount-override
  [prev-item discount-item]
  (let [disc-total (common/parse-money (:line_total discount-item))
        qty (common/parse-money (:qty prev-item))
        qty-one? (and (some? qty) (= 1M (bigdec qty)))]
    (cond-> (assoc prev-item :line_total disc-total)
      (or qty-one? (nil? (:unit_price prev-item)))
      (assoc :unit_price disc-total))))

(defn- price-mismatch?
  [qty unit-price line-total]
  (let [qty (common/parse-money qty)
        unit-price (common/parse-money unit-price)
        line-total (common/parse-money line-total)
        expected (when (and qty unit-price)
                   (.multiply ^java.math.BigDecimal (bigdec unit-price)
                     ^java.math.BigDecimal (bigdec qty)))
        diff (when (and expected line-total)
               (shape/abs-decimal-diff expected line-total))]
    (and qty
      unit-price
      line-total
      (pos? (.signum ^java.math.BigDecimal (bigdec qty)))
      (some? diff)
      (> diff 0.01))))

(defn- repair-item-prices
  "When unit_price × qty differs from line_total, trust line_total and recalculate unit_price."
  [item]
  (let [qty (common/parse-money (:qty item))
        line-total (common/parse-money (:line_total item))]
    (if-not (price-mismatch? qty (:unit_price item) line-total)
      [item false]
      (let [repaired-unit-price (.divide ^java.math.BigDecimal (bigdec line-total)
                                  ^java.math.BigDecimal (bigdec qty)
                                  2
                                  java.math.RoundingMode/HALF_UP)]
        [(assoc item
           :unit_price repaired-unit-price
           :price_repaired true)
         true]))))

(defn clean-extraction-items
  [items {:keys [items-count grand-total] :as ctx}]
  (let [label-sample-limit 5
        add-sample (fn [m reason raw-label]
                     (let [s (some-> raw-label str str/trim not-empty)]
                       (if-not s
                         m
                         (update m reason (fn [v]
                                            (let [v (vec (or v []))]
                                              (if (>= (count v) label-sample-limit)
                                                v
                                                (conj v s))))))))
        {:keys [items dropped-by-reason dropped-labels-sample discount-overrides]}
        (reduce
          (fn [{:keys [items] :as acc} item]
            (let [raw-label (:raw_label item)
                  label (normalize-item-label raw-label)]
              (cond
                (discount-row? label)
                (let [prev (peek items)]
                  (if (and prev (discount-override? prev item))
                    (-> acc
                      (update :items (fn [v] (conj (pop v) (apply-discount-override prev item))))
                      (update :discount-overrides (fnil inc 0)))
                    (-> acc
                      (update-in [:dropped-by-reason :discount] (fnil inc 0))
                      (update :dropped-labels-sample add-sample :discount raw-label))))

                :else
                (if-let [reason (non-item-reason ctx item)]
                  (-> acc
                    (update-in [:dropped-by-reason reason] (fnil inc 0))
                    (update :dropped-labels-sample add-sample reason raw-label))
                  (update acc :items conj item)))))
          {:items []
           :dropped-by-reason {}
           :dropped-labels-sample {}
           :discount-overrides 0}
          (or items []))
        [items price-repairs]
        (reduce
          (fn [[kept-items repairs] item]
            (let [[item repaired?] (repair-item-prices item)]
              [(conj kept-items item)
               (if repaired?
                 (inc repairs)
                 repairs)]))
          [[] 0]
          items)
        items (vec items)
        original-count (long (or items-count (count (or items []))))
        kept-count (count items)
        dropped-count (->> dropped-by-reason vals (reduce + 0))
        post-processing
        (when (or (pos? dropped-count)
                (pos? (long discount-overrides))
                (pos? (long price-repairs)))
          {:original-count original-count
           :kept-count kept-count
           :dropped-count dropped-count
           :discount-overrides (long (or discount-overrides 0))
           :price-repairs (long (or price-repairs 0))
           :dropped-by-reason dropped-by-reason
           :dropped-labels-sample (not-empty dropped-labels-sample)
           :grand-total (when grand-total (str grand-total))})]
    {:items items
     :post-processing post-processing}))
