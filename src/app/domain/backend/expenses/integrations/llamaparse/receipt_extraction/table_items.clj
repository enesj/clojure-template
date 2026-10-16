(ns app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.table-items
  (:require
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.text :as text]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.totals :as totals]
    [app.domain.backend.expenses.workers.receipt-ocr.common :as common]
    [clojure.string :as str])
  (:import
    [java.math RoundingMode]))

(def unit-prefixes
  #{"t/pc"})

(defn- unit-prefix-row?
  [cells]
  (let [label (text/normalize-text (first cells))
        rest* (rest cells)]
    (boolean
      (and label
        (contains? unit-prefixes label)
        (every? (comp nil? text/safe-trim) rest*)))))

(def summary-prefixes
  ["ve" "vm" "osn" "pdv" "pdu" "ue" "пдв" "vat" "tax" "porez" "порез"
   "jib" "pib" "ibfm" "bf" "uplac" "upl" "gotovina" "kartica" "cek" "ček" "povrat" "ukupno" "укупно" "total"
   "ukupan" "укупан" "platna kartica" "платна картица" "готовина" "картица"
   "примљено" "razlika za povrat" "разлика за поврат"
   "popust" "pupust" "rabat" "discount" "akcija" "snizenje" "sniženje"])

(def ^:private whole-word-summary-prefixes
  ;; "pdu" and "ue" are OCR misreadings of "PDV" and "VE"
  #{"ve" "vm" "osn" "cek" "ček" "pdu" "ue"})

(defn summary-label?
  [label]
  (let [label (text/normalize-text label)
        label* (some-> label
                 (str/replace #"^[^\p{L}\p{N}]+" "")
                 ;; tax-rate rows start with the rate marker, e.g. "E PDV 17%"
                 (str/replace #"(?iu)^\p{L}\s+(?=(?:pdv|пдв|vat|tax|porez|порез)(?![\p{L}\p{N}]))" "")
                 text/safe-trim)]
    (boolean
      (and label*
        (some (fn [prefix]
                (if (contains? whole-word-summary-prefixes prefix)
                  (boolean (re-find (re-pattern (str "(?iu)^" prefix "(?![\\p{L}\\p{N}])")) label*))
                  (str/starts-with? label* prefix)))
          summary-prefixes)))))

(defn normalize-item-label
  [raw]
  (let [raw (some-> raw
              str
              (str/replace #"(?is)<br\s*/?>" " ")
              (str/replace #"(?is)<[^>]+>" " ")
              (str/replace #"[_]+" " ")
              (str/replace #"\s+" " ")
              (str/replace #"\|" " ")
              str/trim
              not-empty)
        raw (when raw
              (if-let [[_ _ rest]
                       (re-matches #"(?i)^([0-9]{4,}|[A-Z][0-9]{4,})[ \t]+(.+)$" raw)]
                (let [rest (str/trim rest)]
                  (if (re-find #"\p{L}" rest)
                    rest
                    raw))
                raw))
        raw (when raw
              (some-> raw
                (str/replace #"(?iu)^(?:artik(?:al|l|la|li)?|naziv|opis)\s*:?\s+(?=\p{L})" "")
                (str/replace #"\s+" " ")
                str/trim
                not-empty))]
    raw))

(defn- header-token?
  [norm]
  (let [tokens (when norm
                 (->> (str/split norm #"[^\p{L}\p{N}]+")
                   (remove str/blank?)
                   set))
        header-tokens #{"label" "naziv" "artikal" "artikl" "artikla" "name" "item" "opis"
                        "description" "cijena" "price" "kol" "kolicina" "količina"
                        "qty" "quantity" "ukupno" "total" "iznos" "oznaka" "stopa" "porez"
                        "pdv" "vat" "tax" "назив" "опис" "цијена" "кол"
                        "укупно" "износ" "ознака" "стопа" "порез" "пдв"}]
    (boolean
      (and norm
        (or (contains? header-tokens norm)
          (some header-tokens tokens))))))

(defn- header-row?
  [cells]
  (let [norms (->> cells (map text/normalize-text) (remove nil?) vec)
        hits (count (filter header-token? norms))]
    (>= hits 2)))

(defn- header->mapping
  [cells]
  (let [norms (map text/normalize-text cells)
        find-idx (fn [re]
                   (some (fn [[i n]] (when (and n (re-find re n)) i))
                     (map-indexed vector norms)))
        label-idx (or (find-idx #"^(?:label|naziv|name|oznaka|назив|opis|опис|description|item)$") 0)
        qty-idx (find-idx #"^(?:kol\.?|qty|quantity|кол\.?|kol|količina|kolicina)(?:$|\s)")
        unit-idx (find-idx #"^(?:cijena|price|цијена)$")
        total-idx (find-idx #"^(?:ukupno|total|укупно|iznos|износ)$")]
    {:label-idx label-idx
     :qty-idx qty-idx
     :unit-idx unit-idx
     :total-idx total-idx}))

(defn- abs-decimal-diff
  [a b]
  (when (and a b)
    (double (.abs (.subtract (bigdec a) (bigdec b))))))

(defn apply-discount
  "Apply a parsed discount `{:pct .. :amount ..}` to `item`, returning the item
  with the discounted :line_total (and :unit_price when :qty is known)."
  [{:keys [line_total qty] :as item} {:keys [pct amount]}]
  (let [base (common/parse-money line_total)
        pct* (common/parse-money pct)
        amount* (common/parse-money amount)]
    (cond
      (not (and base amount*))
      item

      pct*
      (let [base (bigdec base)
            pct (-> pct* bigdec .abs)
            amount (-> amount* bigdec .abs)
            expected-discount (* base (/ pct 100M))
            expected-final (.subtract base expected-discount)
            treat-as-discount?
            (or (<= (or (abs-decimal-diff amount expected-discount) 1e9) 0.05)
              (> (or (abs-decimal-diff amount expected-final) 1e9) 0.05))
            new-total (if treat-as-discount?
                        (.subtract base amount)
                        amount)
            qty* (common/parse-money qty)
            unit (when (and qty* (pos? (.compareTo (bigdec qty*) 0M)))
                   (.divide new-total (bigdec qty*) 4 RoundingMode/HALF_UP))]
        (cond-> item
          true (assoc :line_total new-total)
          unit (assoc :unit_price unit)))

      (neg? (bigdec amount*))
      (let [base (bigdec base)
            discount (-> amount* bigdec .abs)
            new-total (.subtract base discount)
            qty* (common/parse-money qty)
            unit (when (and qty* (pos? (.compareTo (bigdec qty*) 0M)))
                   (.divide new-total (bigdec qty*) 4 RoundingMode/HALF_UP))]
        (cond-> item
          true (assoc :line_total new-total)
          unit (assoc :unit_price unit)))

      :else
      item)))

(defn- parse-discount-cell
  "Parse inline discount cell patterns like '-50,00%: 5,00'."
  [cell]
  (let [cell (some-> cell text/safe-trim)]
    (when (seq cell)
      (let [[_ pct amount]
            (or
              (re-find #"(?iu)(-?\d[\d,\.]*)\s*%\s*:\s*(-?\d[\d,\.]*)" cell)
              (re-find #"(?iu)(-\d[\d,\.]*)\s*%\s+(-?\d[\d,\.]*)" cell))]
        (when (and pct amount)
          (let [pct (common/parse-money pct)
                amount (common/parse-money amount)]
            (when (and pct amount)
              {:pct pct
               :amount amount})))))))

(defn discount-row
  "Return {:pct .. :amount ..} when `cells` describe a discount row (e.g.
  '-50,00%: 5,00'), otherwise nil."
  [cells]
  (let [cells* (->> cells (map text/safe-trim) (remove nil?) vec)
        norms (map text/normalize-text cells*)
        discount-label?
        (boolean
          (some (fn [n]
                  (and n
                    (re-find #"(?iu)\b(?:popust|pupust|rabat|discount|akcija|snizenje|sniženje)\b" n)))
            norms))
        pct-token?
        (fn [s]
          (let [s (some-> s text/safe-trim)]
            (boolean
              (and s
                (re-matches #"(?iu)^[^\p{L}]*-?\d[\d,\.]*\s*%:?\s*$" s)))))
        pct-cell (some (fn [c]
                         (when (and (string? c) (pct-token? c))
                           c))
                   cells*)
        embedded (some parse-discount-cell cells*)
        pct (or (when pct-cell (common/parse-money pct-cell))
              (:pct embedded))
        amount-cell (some (fn [c]
                            (when (common/parse-money c)
                              c))
                      (->> cells*
                        reverse
                        (remove #(= % pct-cell))))
        amount (if embedded
                 (:amount embedded)
                 (when amount-cell (common/parse-money amount-cell)))
        label-norm (first norms)
        ignore-summary? (and label-norm (summary-label? label-norm) (not discount-label?))
        negative-pct? (boolean (and pct-cell (re-find #"-\d" pct-cell)))
        negative-amount? (boolean (and amount (neg? (bigdec amount))))
        discount-cue? (or discount-label? negative-pct? negative-amount? embedded)]
    (cond
      ignore-summary?
      nil

      (and pct amount discount-cue?)
      {:pct pct
       :amount amount}

      (and amount discount-label?)
      {:pct nil
       :amount amount}

      :else
      nil)))

(defn- qty-row
  [cells]
  (let [cells* (->> cells (map text/safe-trim) (remove nil?) vec)
        token0 (first cells*)
        token1 (second cells*)
        token2 (nth cells* 2 nil)]
    (when (and (string? token0) (re-matches #"(?i)^[0-9][0-9,\.]*x$" token0))
      {:qty (common/parse-money token0)
       :unit_price (common/parse-money token1)
       :line_total (common/parse-money token2)})))

(defn- apply-qty-row
  [{:keys [line_total] :as item} q]
  (let [qty* (common/parse-money (:qty q))
        unit-price* (common/parse-money (:unit_price q))
        q-total (common/parse-money (:line_total q))
        item-total (common/parse-money line_total)
        line-total (or q-total item-total)
        unit-price (or unit-price*
                     (when (and qty* line-total (pos? (.compareTo (bigdec qty*) 0M)))
                       (.divide (bigdec line-total) (bigdec qty*) 4 RoundingMode/HALF_UP)))]
    (cond-> item
      qty* (assoc :qty qty*)
      line-total (assoc :line_total line-total)
      unit-price (assoc :unit_price unit-price))))

(defn- embedded-qty
  [label]
  (when (string? label)
    (->> (re-seq #"(?iu)(\d[\d,\.]*x)\b" label)
      (keep (fn [[_ token]]
              (let [num (some-> token (subs 0 (dec (count token))))]
                (when (and num (re-find #"[\.,]" num))
                  (when-let [q (common/parse-money token)]
                    {:qty q
                     :token token})))))
      last)))

(defn- parse-merged-qty-price
  "Parse a cell where qty and unit-price are merged, e.g. '3,000x 6,60'.
  Returns {:qty .. :unit_price ..} or nil."
  [cell]
  (when-let [cell (text/safe-trim cell)]
    (when-let [[_ qty-str price-str]
               (re-matches #"(?i)([0-9][0-9,\.]*)x\s+([0-9][0-9,\.]*)" cell)]
      (let [qty (common/parse-money qty-str)
            unit-price (common/parse-money price-str)]
        (when (and qty unit-price)
          {:qty qty :unit_price unit-price})))))

(defn- parse-item-row
  [cells mapping]
  (let [{:keys [label-idx qty-idx unit-idx total-idx]} mapping
        label0 (nth cells label-idx nil)
        label (normalize-item-label label0)
        embedded (when (and (nil? qty-idx) label)
                   (embedded-qty label))
        label (cond-> label
                embedded (str/replace #"(?iu)\s+\d[\d,\.]*x\s*$" "")
                true text/safe-trim)
        label-norm (text/normalize-text label)
        qty-cell (when qty-idx (nth cells qty-idx nil))
        qty0 (when qty-idx (common/parse-money qty-cell))
        merged-qty-cell (when (and qty-idx (nil? qty0))
                          (parse-merged-qty-price qty-cell))
        merged-second-cell (when (and (nil? qty-idx)
                                   (nil? unit-idx)
                                   (>= (count cells) 2))
                             (parse-merged-qty-price (nth cells 1 nil)))
        qty (or qty0
              (:qty merged-qty-cell)
              (:qty merged-second-cell)
              (:qty embedded)
              1M)
        unit-price0 (or (when unit-idx (common/parse-money (nth cells unit-idx nil)))
                      (:unit_price merged-qty-cell)
                      (:unit_price merged-second-cell))
        money-cells (->> cells
                      (map-indexed vector)
                      (keep (fn [[idx cell]]
                              (when (not= idx label-idx)
                                (when-let [money (common/parse-money cell)]
                                  {:idx idx
                                   :money money}))))
                      vec)
        line-total0 (or (when total-idx (common/parse-money (nth cells total-idx nil)))
                      (common/parse-money (last cells)))
        line-total (or line-total0
                     (when (= 1 (count money-cells))
                       (:money (first money-cells))))
        unit-price (or unit-price0
                     (when (and qty line-total (pos? (.compareTo (bigdec qty) 0M)))
                       (.divide (bigdec line-total) (bigdec qty) 4 RoundingMode/HALF_UP)))]
    (when (and label (re-find #"\p{L}" label) (not (summary-label? label-norm)) line-total)
      {:raw_label label
       :qty qty
       :unit_price unit-price
       :line_total line-total})))

(defn- table-item->rows
  [{:keys [rows md]}]
  (cond
    (sequential? rows) rows

    (and (string? md) (str/includes? md "|"))
    (->> (str/split-lines md)
      (keep (fn [line]
              (when (and (string? line) (str/starts-with? (str/trim line) "|"))
                (->> (str/split line #"\|")
                  (mapv (fn [c] (or (text/safe-trim c) "")))))))
      vec)

    :else
    nil))

(defn response->table-items
  [resp-json]
  (->> (text/response->all-items resp-json)
    (filter (fn [{:keys [type]}]
              (= "table" (some-> type str/lower-case))))
    vec))

(defn- combine-label-lines
  [a b]
  (let [a (text/safe-trim a)
        b (text/safe-trim b)]
    (cond
      (nil? a) b
      (nil? b) a
      :else
      (let [a* (str/lower-case a)
            b* (str/lower-case b)]
        (cond
          (str/includes? a* b*) a
          (str/includes? b* a*) b
          :else (str a " " b))))))

(defn- tax-table?
  "True for the tax breakdown table (`Oznaka | Naziv | Stopa | Porez`), whose rows are not items."
  [rows]
  (let [tokens (some->> (first rows)
                 (#(when (sequential? %) %))
                 (mapcat #(str/split (or (text/normalize-text (str %)) "") #"[^\p{L}\p{N}]+"))
                 set)]
    (boolean
      (and (some #{"stopa" "стопа"} tokens)
        (some #{"porez" "порез"} tokens)))))

(defn parse-table-items
  [table-items]
  (reduce
    (fn [acc table]
      (let [rows (table-item->rows table)]
        (if (or (not (sequential? rows)) (tax-table? rows))
          acc
          (loop [remaining rows
                 mapping {:label-idx 0 :qty-idx nil :unit-idx nil :total-idx 1}
                 pending-label nil
                 items (:items acc)
                 total-lines (:total-lines acc)]
            (if-not (seq remaining)
              (assoc acc :items items :total-lines total-lines)
              (let [row (first remaining)
                    cells (if (sequential? row) (mapv #(or (some-> % str/trim) "") row) [])
                    as-line (when (seq cells) (str/join " " (remove str/blank? cells)))
                    total-lines (cond-> total-lines
                                  (totals/line->total-candidate as-line) (conj as-line))
                    header? (header-row? cells)
                    unit? (unit-prefix-row? cells)
                    discount (when-not (or header? unit?) (discount-row cells))
                    q (when-not (or header? unit? discount) (qty-row cells))
                    mapping
                    (let [n (count cells)
                          label (normalize-item-label (nth cells (:label-idx mapping) nil))
                          label-norm (text/normalize-text label)
                          qty-cell (nth cells 1 nil)
                          unit-cell (nth cells 2 nil)
                          qty-token? (fn [s]
                                       (boolean
                                         (and (string? (text/safe-trim s))
                                           (re-matches #"(?i)^[0-9][0-9,\.]*x$" (text/safe-trim s)))))]
                      (cond
                        (and (not header?)
                          (not unit?)
                          (nil? discount)
                          (nil? q)
                          (nil? (:qty-idx mapping))
                          (nil? (:unit-idx mapping))
                          (= 1 (:total-idx mapping))
                          (>= n 4)
                          (re-find #"\p{L}" (or label ""))
                          (qty-token? qty-cell)
                          (common/parse-money unit-cell)
                          (common/parse-money (last cells))
                          (not (summary-label? label-norm)))
                        (assoc mapping :qty-idx 1 :unit-idx 2 :total-idx (dec n))

                        (and (not header?)
                          (not unit?)
                          (nil? discount)
                          (nil? q)
                          (nil? (:unit-idx mapping))
                          (= 1 (:total-idx mapping))
                          (>= n 3)
                          (re-find #"\p{L}" (or label ""))
                          (common/parse-money (nth cells 1 nil))
                          (common/parse-money (last cells))
                          (not (summary-label? label-norm)))
                        (assoc mapping :unit-idx 1 :total-idx (dec n))

                        :else
                        mapping))]
                (cond
                  (empty? cells)
                  (recur (rest remaining) mapping pending-label items total-lines)

                  header?
                  (recur (rest remaining) (merge mapping (header->mapping cells)) nil items total-lines)

                  unit?
                  (recur (rest remaining) mapping pending-label items total-lines)

                  discount
                  (if (seq items)
                    (let [last-item (peek items)
                          items (conj (pop items) (apply-discount last-item discount))]
                      (recur (rest remaining) mapping pending-label items total-lines))
                    (recur (rest remaining) mapping pending-label items total-lines))

                  q
                  (cond
                    pending-label
                    (let [qty* (or (:qty q) 1M)
                          unit-price* (:unit_price q)
                          q-total (:line_total q)
                          line-total (or q-total
                                       (when (and qty* unit-price*)
                                         (.multiply (bigdec qty*) (bigdec unit-price*))))
                          unit-price (or unit-price*
                                       (when (and qty* line-total (pos? (.compareTo (bigdec qty*) 0M)))
                                         (.divide (bigdec line-total) (bigdec qty*) 4 RoundingMode/HALF_UP)))
                          item (when (and pending-label line-total)
                                 {:raw_label pending-label
                                  :qty qty*
                                  :unit_price unit-price
                                  :line_total line-total})
                          items (cond-> items item (conj item))]
                      (recur (rest remaining) mapping nil items total-lines))

                    (seq items)
                    (let [last-item (peek items)
                          items (conj (pop items) (apply-qty-row last-item q))]
                      (recur (rest remaining) mapping nil items total-lines))

                    :else
                    (recur (rest remaining) mapping nil items total-lines))

                  (and pending-label
                    (nil? (some-> (nth cells (:label-idx mapping) nil) text/safe-trim not-empty))
                    (common/parse-money (last cells)))
                  (let [line-total (common/parse-money (last cells))
                        item {:raw_label pending-label
                              :qty 1M
                              :unit_price line-total
                              :line_total line-total}]
                    (recur (rest remaining) mapping nil (conj items item) total-lines))

                  :else
                  (let [item0 (parse-item-row cells mapping)
                        item (when item0
                               (if pending-label
                                 (update item0 :raw_label (fn [raw]
                                                            (combine-label-lines pending-label raw)))
                                 item0))
                        items (cond-> items item (conj item))
                        pending-label
                        (cond
                          item
                          nil

                          :else
                          (let [label (normalize-item-label (nth cells (:label-idx mapping) nil))
                                label-norm (text/normalize-text label)]
                            (cond
                              (and label (re-find #"\p{L}" label) (not (summary-label? label-norm)))
                              label

                              :else
                              pending-label)))]
                    (recur (rest remaining) mapping pending-label items total-lines)))))))))
    {:items [] :total-lines []}
    (or table-items [])))
