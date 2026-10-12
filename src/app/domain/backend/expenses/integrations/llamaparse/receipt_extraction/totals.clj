(ns app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.totals
  (:require
    [app.domain.backend.expenses.workers.receipt-ocr.common :as common]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.text :as text]
    [clojure.string :as str]))

(def total-preferred-prefixes
  ["total" "ukupan iznos" "ukupna" "ukupan" "za uplatu" "za plac" "za pla" "uplaceno" "primljeno"
   "укупан износ" "укупна" "укупан" "за уплату" "уплаћено" "примљено"])

;; Tendered amounts can differ from the printed total (cash rounding), so they only
;; win when no real total line exists.
(def total-tendered-prefixes
  ["uplaceno" "primljeno" "уплаћено" "примљено"])

(def total-fallback-prefixes
  ["ukupno" "укупно" "gotovina" "kartica" "saldo"])

(def total-exclude-substrings
  ["bez porez" "без порез" "porez" "порез" "pdv" "пдв" "vat" "tax"])

(defn- first-of-multiple-amounts
  "When a line carries several whole-token amounts (e.g. 'TOTAL: 10,87 10.87'),
  return the first one; parse-money would otherwise glue the digits together."
  [line]
  (let [amounts (->> (str/split (or line "") #"\s+")
                  (filter #(re-matches #"-?\d{1,9}(?:[.,]\d{3})*[.,]\d{2}" %)))]
    (when (>= (count amounts) 2)
      (first amounts))))

(defn line->total-candidate
  ([line]
   (line->total-candidate line nil))
  ([line attrs]
   (let [line (some-> line text/safe-trim)
         norm (text/normalize-text line)
         kind (cond
                (and norm (some #(str/starts-with? norm %) total-preferred-prefixes)) :preferred
                (and norm (some #(str/starts-with? norm %) total-fallback-prefixes)) :fallback
                :else nil)
         amount (common/parse-money (or (first-of-multiple-amounts line) line))]
     (when (and kind amount norm
             (not (some #(str/includes? norm %) total-exclude-substrings)))
       (merge {:kind kind
               :amount (bigdec amount)
               :tendered? (boolean (some #(str/starts-with? norm %) total-tendered-prefixes))}
         attrs)))))

(defn pick-best-total-candidate
  [candidates]
  (let [non-zero? (fn [m]
                    (and (some? m)
                      (not (zero? (.compareTo (bigdec m) 0M)))))
        pick (fn [rows]
               (let [amounts (mapv :amount rows)
                     amount (or (last (filter non-zero? amounts))
                              (last amounts))]
                 (some (fn [row]
                         (when (and amount
                                 (zero? (.compareTo (bigdec (:amount row)) (bigdec amount))))
                           row))
                   (reverse rows))))
        preferred (filterv #(= :preferred (:kind %)) candidates)
        preferred (let [primary (vec (remove :tendered? preferred))]
                    (if (seq primary) primary preferred))]
    (when (seq candidates)
      (pick (if (seq preferred) preferred candidates)))))

(defn- pick-best-total
  [candidates]
  (some-> (pick-best-total-candidate candidates) :amount))

(defn extract-total
  ([lines]
   (when (seq lines)
     (->> lines
       (keep line->total-candidate)
       vec
       pick-best-total)))
  ([lines items]
   (when (seq lines)
     (let [candidates (->> lines
                        (keep line->total-candidate)
                        vec)
           picked-total (pick-best-total candidates)
           items-total*
           (when (sequential? items)
             (let [amounts (->> items
                             (keep (fn [{:keys [line_total]}]
                                     (common/parse-money line_total)))
                             (map bigdec)
                             vec)]
               (when (seq amounts)
                 (reduce #(.add %1 %2) 0M amounts))))
           fallback-matches-items-count
           (if items-total*
             (->> candidates
               (filter (fn [{:keys [kind amount]}]
                         (and (= :fallback kind)
                           (zero? (.compareTo (bigdec amount) (bigdec items-total*))))))
               count)
             0)
           prefer-items-total?
           (and picked-total
             items-total*
             (>= fallback-matches-items-count 2)
             (> (.abs (.subtract (bigdec picked-total) (bigdec items-total*))) 0.05M))]
       (cond
         prefer-items-total?
         items-total*

         (seq candidates)
         picked-total

         :else
         nil)))))

(defn items-total
  [items]
  (when (sequential? items)
    (let [amounts (->> items
                    (keep (fn [{:keys [line_total]}]
                            (common/parse-money line_total)))
                    (map bigdec)
                    vec)]
      (when (seq amounts)
        (reduce #(.add %1 %2) 0M amounts)))))
