(ns app.domain.backend.expenses.integrations.llamaparse.receipt-extraction
  "LlamaParse-specific receipt extraction."
  (:require
    [app.domain.backend.expenses.integrations.llamaparse.http :as http]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.merchant :as merchant]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.table-items :as table-items]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.text :as text]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.text-items :as text-items]
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.totals :as totals]
    [app.domain.backend.expenses.workers.receipt-ocr.common :as common]
    [app.domain.backend.expenses.workers.receipt-ocr.markdown.items :as markdown-items]
    [clojure.string :as str]))

(def ^:private confidence-diff-threshold 0.01)
(def ^:private low-confidence-total-threshold 0.8)
(def ^:private high-line-total-reliability-threshold 0.85)
(def ^:private small-total-diff-threshold 0.05)

(defn- abs-decimal-diff
  [a b]
  (when (and a b)
    (double (.abs (.subtract (bigdec a) (bigdec b))))))

(defn- items-match-total?
  [items total]
  (let [items-sum (totals/items-total items)]
    (boolean
      (and items-sum total
        (<= (abs-decimal-diff items-sum total) confidence-diff-threshold)))))

(defn- average
  [xs]
  (when (seq xs)
    (/ (reduce + xs) (double (count xs)))))

(defn- block-confidence
  [{:keys [bbox items]}]
  (or (some->> bbox
        (keep :confidence)
        average)
    (some->> items
      (keep block-confidence)
      average)))

(defn- response->items-table-confidence
  [resp-json]
  (some->> (table-items/response->table-items resp-json)
    (keep (fn [table]
            (when (seq (:items (table-items/parse-table-items [table])))
              (block-confidence table))))
    seq
    (apply max)))

(defn- row->text-snippet
  [row]
  (when (sequential? row)
    (->> row
      (keep text/safe-trim)
      (str/join " ")
      text/safe-trim)))

(defn- block->text-snippets
  [{:keys [md value csv rows]}]
  (->> (concat (mapcat str/split-lines (keep text/safe-trim [md value csv]))
         (keep row->text-snippet rows))
    (map text/safe-trim)
    (remove nil?)
    distinct))

(defn- response->total-candidates
  [resp-json]
  (->> (text/response->all-items resp-json)
    (mapcat (fn [block]
              (let [confidence (block-confidence block)
                    block-type (some-> (:type block) str/lower-case keyword)]
                (keep (fn [line]
                        (totals/line->total-candidate
                          line
                          {:confidence confidence
                           :block_type block-type
                           :line line}))
                  (block->text-snippets block)))))
    vec))

(defn- item-line-total-reliability
  [{:keys [raw_label qty unit_price line_total]}]
  (let [label? (boolean (some-> raw_label text/safe-trim))
        qty* (some-> qty common/parse-money)
        unit-price* (some-> unit_price common/parse-money)
        line-total* (some-> line_total common/parse-money)
        expected-total (when (and qty* unit-price* (pos? (.compareTo (bigdec qty*) 0M)))
                         (.multiply (bigdec qty*) (bigdec unit-price*)))
        abs-diff (abs-decimal-diff expected-total line-total*)
        completeness (average [(if label? 1.0 0.0)
                               (if qty* 1.0 0.0)
                               (if unit-price* 1.0 0.0)
                               (if line-total* 1.0 0.0)])
        arithmetic-score (cond
                           (nil? line-total*)
                           0.0

                           (and expected-total abs-diff (<= abs-diff 0.01))
                           1.0

                           (and expected-total abs-diff (<= abs-diff 0.02))
                           0.95

                           (and expected-total abs-diff (<= abs-diff 0.05))
                           0.75

                           expected-total
                           0.2

                           :else
                           0.5)]
    (-> (+ (* 0.7 arithmetic-score)
          (* 0.3 (or completeness 0.0)))
      (max 0.0)
      (min 1.0))))

(defn- items->line-total-reliability
  [items]
  (some->> (seq (or items []))
    (map item-line-total-reliability)
    average))

(defn- amount->cents-string
  [amount]
  (when-let [m (common/parse-money amount)]
    (format "%.0f" (* 100.0 (double (bigdec m))))))

(defn- single-digit-total-difference?
  [a b]
  (let [a* (amount->cents-string a)
        b* (amount->cents-string b)]
    (boolean
      (and a*
        b*
        (= (count a*) (count b*))
        (= 1 (count (filter not (map = a* b*))))))))

(defn- confidence-prefers-items-total?
  [picked-candidate items-total line-total-reliability]
  (let [picked-total (some-> picked-candidate :amount)
        total-confidence (some-> picked-candidate :confidence)
        abs-diff (abs-decimal-diff picked-total items-total)]
    (and picked-total
      items-total
      line-total-reliability
      (single-digit-total-difference? picked-total items-total)
      (> abs-diff confidence-diff-threshold)
      (<= abs-diff small-total-diff-threshold)
      (>= (double line-total-reliability) high-line-total-reliability-threshold)
      (or (nil? total-confidence)
        (and (<= (double total-confidence) low-confidence-total-threshold)
          (< (double total-confidence) (double line-total-reliability)))))))

(defn- build-provider-confidence
  [picked-candidate items-total items-table-confidence line-total-reliability used-items-total?]
  (let [provider-confidence (cond-> {}
                              items-table-confidence
                              (assoc :items_table_confidence items-table-confidence)

                              (some? line-total-reliability)
                              (assoc :line_total_reliability line-total-reliability)

                              (some? items-total)
                              (assoc :items_total items-total)

                              (some-> picked-candidate :confidence some?)
                              (assoc :selected_total_confidence (:confidence picked-candidate))

                              (some-> picked-candidate :kind some?)
                              (assoc :selected_total_kind (some-> picked-candidate :kind name))

                              (some-> picked-candidate :block_type some?)
                              (assoc :selected_total_source (some-> picked-candidate :block_type name))

                              (some-> picked-candidate :line some?)
                              (assoc :selected_total_line (:line picked-candidate))

                              used-items-total?
                              (assoc :reconciliation_basis :items_total_high_line_total_reliability))]
    (not-empty provider-confidence)))

(defn response->header-text
  [resp-json]
  (text/response->header-text resp-json))

(defn response->combined-text
  [resp-json]
  (text/response->combined-text resp-json))

(defn response->extraction
  "Build a ReceiptExtraction map from a LlamaParse result response JSON."
  [resp-json]
  (let [header (response->header-text resp-json)
        structured-text (text/response->structured-text resp-json)
        structured-code-text (text/response->structured-code-text resp-json)
        structured-markdown-items (some-> structured-code-text markdown-items/candidates not-empty)
        text-content (http/response->text resp-json)
        combined (response->combined-text resp-json)
        date-line (text/text->date-line combined)
        purchased-at (text/date-line->iso date-line)
        merchant (merchant/text->merchant-context header text-content)
        table-items (table-items/response->table-items resp-json)
        {:keys [items total-lines]} (table-items/parse-table-items table-items)
        picked-candidate (some-> resp-json response->total-candidates totals/pick-best-total-candidate)
        text-candidates (fn []
                          (->> [structured-markdown-items
                                (text-items/parse-text-items structured-text)
                                (text-items/parse-text-items text-content)
                                (text-items/parse-text-items combined)]
                            (remove empty?)))
        items (cond
                (empty? items)
                (or structured-markdown-items
                  (text-items/parse-text-items structured-text)
                  (text-items/parse-text-items text-content)
                  (text-items/parse-text-items combined)
                  [])

                ;; A partial table (e.g. only the last rows of the receipt) is replaced by a
                ;; text parse whose items add up to the printed total.
                (and (:amount picked-candidate)
                  (not (items-match-total? items (:amount picked-candidate))))
                (or (->> (text-candidates)
                      (filter #(items-match-total? % (:amount picked-candidate)))
                      first)
                  items)

                :else
                items)
        total-lines* (concat (when combined (str/split-lines combined)) total-lines)
        items-total (totals/items-total items)
        items-table-confidence (response->items-table-confidence resp-json)
        line-total-reliability (items->line-total-reliability items)
        extracted-total (or (totals/extract-total total-lines* items)
                          items-total)
        use-items-total? (confidence-prefers-items-total? picked-candidate items-total line-total-reliability)
        total (if use-items-total? items-total extracted-total)
        provider-confidence (build-provider-confidence picked-candidate
                              items-total
                              items-table-confidence
                              line-total-reliability
                              use-items-total?)]
    {:merchant (not-empty merchant)
     :purchased_at purchased-at
     :currency nil
     :totals (when total {:total total})
     :provider_confidence provider-confidence
     :items (vec (or items []))}))

(defn extraction->markdown
  "Build a stable, receipt-like markdown representation from an extraction.

  `date-line` is the original receipt date line (e.g. '2.12.2025. 19:46')."
  [{:keys [merchant totals items]} {:keys [date-line]}]
  (let [merchant-name (some-> merchant :name text/safe-trim)
        date-line (text/safe-trim date-line)
        total (some-> totals :total common/parse-money)
        format-money (fn [m]
                       (when-let [m (common/parse-money m)]
                         (format "%.2f" (double (bigdec m)))))
        fmt-row (fn [row]
                  (str "| " (str/join " | " row) " |"))
        rows (->> (or items [])
               (mapv (fn [{:keys [raw_label qty unit_price line_total]}]
                       [(or (text/safe-trim raw_label) "")
                        (or (some-> qty common/parse-money str) "")
                        (or (format-money unit_price) "")
                        (or (format-money line_total) "")])))
        table (when (seq rows)
                (let [header ["Label" "Qty" "Unit" "Total"]
                      sep ["---" "---" "---" "---"]]
                  (str/join "\n" (concat [(fmt-row header)
                                          (fmt-row sep)]
                                   (map fmt-row rows)))))
        blocks (cond-> []
                 merchant-name (conj merchant-name)
                 date-line (conj date-line)
                 table (conj table)
                 total (conj (str "TOTAL: " (format "%.2f" (double (bigdec total))))))]
    (->> blocks
      (map text/safe-trim)
      (remove nil?)
      (str/join "\n\n")
      not-empty)))

(defn response->receipt
  "Return {:extraction .. :parsed-markdown .. :date-line ..} from LlamaParse response."
  [resp-json]
  (let [combined (response->combined-text resp-json)
        date-line (text/text->date-line combined)
        extraction (response->extraction resp-json)
        md (extraction->markdown extraction {:date-line date-line})]
    {:extraction extraction
     :parsed-markdown md
     :date-line date-line}))
