(ns app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.text-items
  (:require
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.merchant :as merchant]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.table-items :as table-items]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.text :as text]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.totals :as totals]
    [app.domain.backend.expenses.workers.receipt-ocr.common :as common]
    [clojure.string :as str])
  (:import
    [java.math RoundingMode]))

(defn- embedded-qty
  "Return {:qty .. :token ..} for embedded quantities like '0,198x' or '1,000x'."
  [label]
  (when (string? label)
    (->> (re-seq #"(?iu)(\d[\d,\.]*)x\b" label)
      (keep (fn [[_ num]]
              (when (re-find #"[\.,]" num)
                (when-let [q (common/parse-money num)]
                  {:qty q
                   :token (str num "x")}))))
      last)))

(defn- address-noise-line?
  [line]
  (let [line (some-> line text/safe-trim)
        norm (text/normalize-text line)]
    (boolean
      (and line norm
        (or (some #(str/starts-with? norm %) merchant/address-prefixes)
          (re-matches #"(?iu)^\d{5}\s+\S.*$" line)
          (re-find #"(?iu)\b(?:br\.?|broj[a]?)\s*\d+[a-z]?\b" line))))))

(defn- continuation-label?
  [label]
  (let [label (some-> label table-items/normalize-item-label text/safe-trim)
        norm (text/normalize-text label)]
    (boolean
      (and label norm
        (or (not (str/includes? label " "))
          (re-find #"(?iu)^(?:x\d|\d+[x×]|\d+\s*[x×])" label)
          (re-find #"(?iu)\b(?:cm|mm|ml|gr|kg|xl|xxl)\b" norm))))))

(defn- qty-price-fragment-label?
  [label]
  (boolean
    (and (string? label)
      (re-matches #"(?iu)^\d[\d,\.]*x(?:\s+\d[\d,\.]*)?$" (str/trim label)))))

(defn- barcode-label?
  "True for a label that is only a product barcode/code, e.g. '69 60936564905'."
  [label]
  (boolean
    (and (string? label)
      (re-matches #"\d{1,4}\s\d{8,}|\d{8,}" (str/trim label)))))

(defn- wrapped-label-tail?
  "True when a label line ends in a dimension token (e.g. '220x125x300mm', '550x'),
  which marks a product name wrapped onto the next receipt line."
  [label]
  (boolean
    (and (string? label)
      (re-find #"(?iu)(?:\d[x×]\d[\p{L}\d.,]*|\d(?:mm|cm)|[x×])$" (str/trim label)))))

(defn- tax-rate-label?
  "VAT rate lines such as 'VA: 0,00%' or 'VE: 17,00%' (rate letter + percentage), never an item."
  [label]
  (boolean
    (and (string? label)
      (re-matches #"(?iu)^\p{L}{1,3}\s*:\s*-?\d[\d,\.]*\s*%$" (str/trim label)))))

(defn- tail-fragment?
  "True for the second half of a product name that the receipt printer wrapped onto the price line:
  a unit stub ('KO', 'M', 'kom 1'), a code-only tail ('12 3a40', 'X 10 3470', 'MG 1 A 3a26') or a short
  word followed by a pharmacy code ('slojev 7369', 'SNALIJ 577f', '0 SA P 172e'). Such a label never
  names a product on its own, so it is appended to the pending label line above it."
  [label]
  (let [label (some-> label text/safe-trim)]
    (boolean
      (and label
        (or (and (re-find #"\d" label) (not (re-find #"\p{L}{3,}" label)))
          (re-matches #"(?iu)\p{L}{1,2}" label)
          (re-matches #"(?iu)(?:kom|ko|kg|gr|ml)\.?(?:\s+\d{1,3})?" label)
          (re-matches #"(?iu)(?:\d{1,3}\s+)?\p{L}{1,7}(?:\s+\p{L}{1,2})?\s+(?=[0-9a-f]*\d)[0-9a-f]{3,4}" label))))))

(defn- join-wrapped-label
  "Join a pending label line and its wrapped tail. A unit split across the wrap ('.../KO' + 'M') is
  re-joined without a space."
  [pending tail]
  (if (and (re-find #"(?iu)/\s*ko$" pending) (re-matches #"(?iu)m" tail))
    (str pending tail)
    (str pending " " tail)))

(defn- line->text-item
  [line]
  (let [line (some-> line text/safe-trim)
        norm (text/normalize-text line)]
    (when (and line norm
            (not (table-items/summary-label? norm))
            (not (re-matches text/ba-datetime-line-re line))
            (not (re-matches text/ba-date-line-re line))
            (not (address-noise-line? line))
            (not (merchant/store-line? line))
            (not (merchant/separator-noise? line)))
      (when-let [[_ label price-str] (re-matches #"(?iu)^(.+?)\s+(\d{1,9}[\.,]\d{2})\s*(?:[A-KM-Z])?\s*(?:e|km|bam|€)?$" line)]
        (let [label (table-items/normalize-item-label label)
              price (common/parse-money price-str)
              embedded (when label (embedded-qty label))
              label (cond-> label
                      embedded (str/replace #"(?iu)\s+\d[\d,\.]*x\s*$" "")
                      true text/safe-trim)
              qty (or (:qty embedded) 1M)
              unit-price (when (and qty price (pos? (.compareTo (bigdec qty) 0M)))
                           (.divide (bigdec price) (bigdec qty) 4 RoundingMode/HALF_UP))]
          (when (and label
                  price
                  (or (re-find #"\p{L}" label)
                    (barcode-label? label))
                  (not (qty-price-fragment-label? label))
                  (not (tax-rate-label? label))
                  (not (table-items/summary-label? (text/normalize-text label))))
            {:raw_label label
             :qty qty
             :unit_price unit-price
             :line_total price}))))))

(def money-only-line-re
  #"(?iu)^[^\p{L}\p{N}]*(-?\d{1,9}[\.,]\d{2})\s*(?:[A-Z])?\s*(?:e|km|bam|€)?[^\p{L}\p{N}]*$")

(defn- line->money-only
  [line]
  (when-let [line (text/safe-trim line)]
    (when-let [[_ amount] (re-matches money-only-line-re line)]
      (common/parse-money amount))))

(defn- line->qty-unit-total
  [line]
  (let [tokens (->> (str/split (str/trim (or line "")) #"\s+")
                 (remove str/blank?)
                 vec)]
    (when (seq tokens)
      (or
        (some (fn [i]
                (let [t (get tokens i)
                      qty (when (and (string? t)
                                  (re-matches #"(?i)^[0-9][0-9,\\.]*x$" t))
                            (common/parse-money t))]
                  (when qty
                    (let [unit-price (common/parse-money (get tokens (inc i)))
                          line-total (common/parse-money (get tokens (+ i 2)))]
                      (when unit-price
                        {:qty qty
                         :unit_price unit-price
                         :line_total (or line-total
                                       (.setScale
                                         (.multiply (bigdec qty) (bigdec unit-price))
                                         2
                                         RoundingMode/HALF_UP))})))))
          (range (count tokens)))
        (some (fn [i]
                (let [t (get tokens i)
                      t0 (some-> t str/lower-case)
                      prev2 (when (>= i 2) (some-> (get tokens (- i 2)) str/lower-case))]
                  (when (and (= "x" t0) (pos? i) (not= "x" prev2))
                    (let [qty (common/parse-money (get tokens (dec i)))
                          unit-price (common/parse-money (get tokens (inc i)))
                          line-total-token (get tokens (+ i 2))
                          line-total (common/parse-money line-total-token)]
                      (when (and qty unit-price (or (nil? line-total-token) line-total))
                        {:qty qty
                         :unit_price unit-price
                         :line_total (or line-total
                                       (.setScale
                                         (.multiply (bigdec qty) (bigdec unit-price))
                                         2
                                         RoundingMode/HALF_UP))})))))
          (range (count tokens)))))))

(defn- line->price-qty-total
  "Parses a column-layout line `15,00 1,000 15,00` (unit price, qty, line total).
  The price x qty = total check keeps it from matching unrelated amount lines."
  [line]
  (when-let [[_ p q t] (some->> line
                         text/safe-trim
                         (re-matches #"(?u)^(\d{1,9}[.,]\d{2})\s+(\d{1,6}(?:[.,]\d{1,3})?)\s+(\d{1,9}[.,]\d{2})$"))]
    (let [price (common/parse-money p)
          qty (common/parse-money q)
          total (common/parse-money t)]
      (when (and price qty total
              (pos? (.signum (bigdec qty)))
              (<= (.abs (.subtract (.multiply (bigdec price) (bigdec qty)) (bigdec total)))
                0.02M))
        {:qty qty
         :unit_price price
         :line_total total}))))

(defn- text-item-label-line?
  "True when `line` can be an item label.

  A line that looks like address noise (e.g. a 5-digit product code such as
  '27150 Amisu Dzemper' matches the postal-code pattern) is still accepted when
  the next line is a quantity/price line like '1,000x 9,95 9,95E'."
  [line next-line]
  (let [line (some-> line text/safe-trim)
        norm (text/normalize-text line)]
    (boolean
      (and line norm
        (re-find #"\p{L}" line)
        (not (table-items/summary-label? norm))
        (not (re-matches text/ba-datetime-line-re line))
        (not (re-matches text/ba-date-line-re line))
        (or (not (address-noise-line? line))
          (some? (line->qty-unit-total next-line)))
        (not (merchant/store-line? line))
        (not (merchant/separator-noise? line))
        (nil? (totals/line->total-candidate line))))))

(def ^:private negative-pct-only-line-re
  #"(?iu)^-\d[\d,\.]*\s*%\s*:?$")

(defn- code-like-continuation?
  "True for a wrapped label tail made only of short codes (e.g. '2 5 2b3c')."
  [label]
  (boolean
    (and (string? label)
      (re-find #"\d" label)
      (not (re-find #"\p{L}{3,}" label)))))

(defn- line->qty-only
  [line]
  (let [line (some-> line text/safe-trim)]
    (or
      (when-let [[_ qty] (and line (re-matches #"(?i)^([0-9][0-9,\\.]*)x$" line))]
        (common/parse-money qty))
      (when-let [[_ qty] (and line (re-matches #"(?i)^([0-9][0-9,\\.]*)\s+x$" line))]
        (common/parse-money qty)))))

(defn- line->unit-and-total
  [line]
  (let [line (some-> line text/safe-trim)
        amounts (when line
                  (->> (re-seq #"-?\d{1,9}[\.,]\d{2}" line)
                    (keep common/parse-money)
                    vec))]
    (when (and line
            (seq amounts)
            (not (re-find #"[%:]" line)))
      {:unit_price (first amounts)
       :line_total (or (second amounts)
                     (first amounts))})))

(defn parse-text-items
  [text-content]
  (when (string? text-content)
    (let [lines (->> (str/split-lines text-content)
                  (map text/safe-trim)
                  (remove nil?)
                  vec)
          date-idx (some (fn [[idx line]]
                           (when (or (re-matches text/ba-datetime-line-re line)
                                   (re-matches text/ba-date-line-re line))
                             idx))
                     (map-indexed vector lines))
          lines (if (some? date-idx)
                  (subvec lines (inc date-idx))
                  lines)
          combine-labels
          (fn [pending-label inline-item]
            (let [pending (some-> pending-label table-items/normalize-item-label text/safe-trim)
                  raw-label (some-> (:raw_label inline-item) table-items/normalize-item-label text/safe-trim)]
              (cond
                (nil? pending)
                inline-item

                (nil? raw-label)
                (assoc inline-item :raw_label pending)

                (tail-fragment? raw-label)
                (assoc inline-item :raw_label (join-wrapped-label pending raw-label))

                (not (or (continuation-label? raw-label)
                       (wrapped-label-tail? pending)))
                inline-item

                (str/includes? (str/lower-case pending) (str/lower-case raw-label))
                (assoc inline-item :raw_label pending)

                (str/includes? (str/lower-case raw-label) (str/lower-case pending))
                inline-item

                :else
                (assoc inline-item :raw_label (str pending " " raw-label)))))]
      (loop [remaining lines
             pending-label nil
             pending-qty nil
             items []]
        (if-not (seq remaining)
          items
          (let [line (first remaining)
                inline-item0 (line->text-item line)
                inline-item (when inline-item0
                              (combine-labels pending-label inline-item0))
                amount-only (line->money-only line)
                qty-line (or (line->qty-unit-total line)
                           (line->price-qty-total line))
                qty-only (line->qty-only line)
                unit-and-total (line->unit-and-total line)
                pending-label (some-> pending-label table-items/normalize-item-label text/safe-trim)
                split-discount (when (and (seq items)
                                       (re-matches negative-pct-only-line-re line)
                                       (line->money-only (second remaining)))
                                 (table-items/discount-row
                                   [(str (str/replace line #"\s*:\s*$" "") ": " (second remaining))]))
                discount (when (seq items)
                           (or split-discount
                             (table-items/discount-row [line])))]
            (cond
              discount
              (recur (if split-discount (nnext remaining) (rest remaining))
                pending-label
                pending-qty
                (conj (pop items) (table-items/apply-discount (peek items) discount)))

              inline-item
              (recur (rest remaining) nil nil (conj items inline-item))

              (and pending-qty pending-label unit-and-total
                (re-find #"\p{L}" pending-label)
                (not (qty-price-fragment-label? pending-label))
                (not (table-items/summary-label? (text/normalize-text pending-label))))
              (recur (rest remaining)
                nil
                nil
                (conj items {:raw_label pending-label
                             :qty pending-qty
                             :unit_price (:unit_price unit-and-total)
                             :line_total (or (:line_total unit-and-total)
                                           (.setScale
                                             (.multiply (bigdec pending-qty) (bigdec (:unit_price unit-and-total)))
                                             2
                                             RoundingMode/HALF_UP))}))

              (and qty-line pending-label
                (re-find #"\p{L}" pending-label)
                (not (qty-price-fragment-label? pending-label))
                (not (table-items/summary-label? (text/normalize-text pending-label))))
              (recur (rest remaining)
                nil
                nil
                (conj items {:raw_label pending-label
                             :qty (:qty qty-line)
                             :unit_price (:unit_price qty-line)
                             :line_total (:line_total qty-line)}))

              (and qty-only pending-label
                (re-find #"\p{L}" pending-label)
                (not (qty-price-fragment-label? pending-label))
                (not (table-items/summary-label? (text/normalize-text pending-label))))
              (recur (rest remaining) pending-label qty-only items)

              (and amount-only pending-label
                (re-find #"\p{L}" pending-label)
                (not (table-items/summary-label? (text/normalize-text pending-label))))
              (let [line-total (bigdec amount-only)
                    qty (or pending-qty 1M)
                    unit-price (if pending-qty
                                 (.divide line-total (bigdec pending-qty) 4 RoundingMode/HALF_UP)
                                 line-total)]
                (recur (rest remaining)
                  nil
                  nil
                  (conj items {:raw_label pending-label
                               :qty qty
                               :unit_price unit-price
                               :line_total line-total})))

              (text-item-label-line? line (second remaining))
              (let [label (some-> line table-items/normalize-item-label text/safe-trim)
                    label-norm (text/normalize-text label)
                    next-line (second remaining)
                    continuation? (and pending-label
                                    label
                                    (or (and (code-like-continuation? label)
                                          (some? (line->money-only next-line)))
                                      ;; wrapped tail followed by its qty/price line
                                      (and (tail-fragment? label)
                                        (some? (or (line->money-only next-line)
                                                 (line->qty-unit-total next-line)
                                                 (line->price-qty-total next-line)
                                                 (line->qty-only next-line))))))]
                (recur (rest remaining)
                  (cond
                    continuation?
                    (join-wrapped-label pending-label label)

                    (and label (re-find #"\p{L}" label) (not (table-items/summary-label? label-norm)))
                    label

                    :else
                    pending-label)
                  nil
                  items))

              :else
              (recur (rest remaining) pending-label nil items))))))))