(ns app.domain.backend.expenses.integrations.cerebras.receipt-refine
  "Prompt + JSON schema for refining receipt OCR markdown into structured extraction.

  Designed for Cerebras (OpenAI-compatible) structured outputs.

  The model-facing schema intentionally uses integer cents to avoid locale issues
  with ',' / '.' decimal separators in OCR output. The app converts the model
  output into the internal receipt extraction shape used by
  `app.domain.backend.expenses.workers.receipt-ocr.extraction`.

  Optional fields are represented as nullable values, so the top-level shape is
  stable even when the model cannot confidently extract a value."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]))

(def receipt-extraction-json-schema
  {"title" "ReceiptExtractionCentsV1"
   "type" "object"
   "additionalProperties" false
   "properties"
   {"merchant" {"anyOf" [{"type" "object"
                          "additionalProperties" false
                          "properties" {"name" {"anyOf" [{"type" "string"}
                                                         {"type" "null"}]}
                                        "address" {"anyOf" [{"type" "string"}
                                                            {"type" "null"}]}
                                        "tax_id" {"anyOf" [{"type" "string"}
                                                           {"type" "null"}]}}
                          "required" ["name" "address" "tax_id"]}
                         {"type" "null"}]}

    "purchased_at" {"anyOf" [{"type" "string"}
                             {"type" "null"}]
                    "description" "ISO-8601 timestamp if present, else null."}

    "currency" {"anyOf" [{"type" "string"}
                         {"type" "null"}]
                "description" "ISO 4217 currency code (e.g. BAM/EUR/USD), else null."}

    "totals" {"type" "object"
              "additionalProperties" false
              "properties" {"subtotal_cents" {"anyOf" [{"type" "integer"}
                                                       {"type" "null"}]}
                            "tax_cents" {"anyOf" [{"type" "integer"}
                                                  {"type" "null"}]}
                            "total_cents" {"anyOf" [{"type" "integer"}
                                                    {"type" "null"}]}}
              "required" ["subtotal_cents" "tax_cents" "total_cents"]}

    "items" {"type" "array"
             "items" {"type" "object"
                      "additionalProperties" false
                      "properties" {"name" {"type" "string"}
                                    "quantity" {"anyOf" [{"type" "number"}
                                                         {"type" "null"}]
                                                "description" "Quantity can be decimal (up to 3 decimals)."}
                                    "unit_price_cents" {"anyOf" [{"type" "integer"}
                                                                 {"type" "null"}]
                                                        "description" "Final per-unit price after discounts."}
                                    "line_total_cents" {"anyOf" [{"type" "integer"}
                                                                 {"type" "null"}]
                                                        "description" "Final line total after discounts."}}
                      "required" ["name" "quantity" "unit_price_cents" "line_total_cents"]}}}
   "required" ["merchant" "purchased_at" "currency" "totals" "items"]})

(def receipt-extraction-system-prompt
  (str
    "You extract structured receipt data from OCR markdown.\n"
    "Return exactly one JSON object that matches the provided JSON Schema.\n"
    "Do not add extra keys.\n"
    "\n"
    "Numeric rules:\n"
    "- All monetary fields are integer cents (e.g. 13.40 -> 1340, 0.89 -> 89).\n"
    "- Use the final price after discounts for unit_price_cents and line_total_cents.\n"
    "- Quantity may be decimal with up to 3 decimal places (e.g. 1.098).\n"
    "\n"
    "Merchant rules:\n"
    "- Merchant name is usually in the header. If the name appears as \"<merchant>\" in quotes, use the text inside quotes.\n"
    "- Ignore non-merchant header lines like fiscal markers, long numeric IDs, cashier lines, and tax/payer labels.\n"
    "\n"
    "Items rules (VERY IMPORTANT):\n"
    "- Only include actual purchased items.\n"
    "- If there is an items table (headers like Naziv/Name/Opis + Cijena/Price + Kol./Qty + Ukupno/Total), start items at the first data row under that header.\n"
    "- Stop items when you reach totals/payment/tax sections.\n"
    "- DO NOT include rows/sections about totals, payment methods, or tax summaries as items.\n"
    "  Examples to exclude: ukupno/total/ukupan iznos, uplaceno/primljeno, kartica/gotovina, povrat/razlika, PDV/VAT/porez.\n"
    "- Remove leading article/product codes from item names when present.\n"
    "- Quantity-only or price-only fragments like '2,000x 7,56' are not valid standalone item names.\n"
    "- Treat embedded size/volume markers like '0,25L', '0.25L', '1L', '330ML', and '500G' as part of the item name, not as prices.\n"
    "- If a product line contains a size marker in the label and a later quantity/price line contains the actual price, keep the size marker in the item name and use the later numeric price values.\n"
    "- Preserve explicit 100% discount rows like '-100,00%: 0,00' as discounts on the preceding item, not as standalone purchased items.\n"
    "\n"
    "Totals rules:\n"
    "- totals.total_cents is the receipt grand total for purchased items (TOTAL / UKUPNO in totals section), not the tendered payment amount.\n"
    "- Never use UPLAĆENO/PRIMLJENO/GOTOVINA/KARTICA/POVRAT as totals.total_cents.\n"
    "- Ignore totals that explicitly say 'without tax' (bez poreza / без пореза) or tax-only totals.\n"
    "- Compare totals.total_cents against the sum of items[].line_total_cents.\n"
    "- If they do not match, first look for a missed item row, bag/deposit/fee row, or discount row before changing totals.\n"
    "- If evidence is inconsistent, do not invent phantom items; use nulls or keep the receipt for review.\n"
    "\n"
    "If a value is not present or not reliable, use null (do not invent values)."))

(defn receipt-extraction-user-prompt
  [markdown]
  (let [md (some-> markdown str)
        md (if (and md (<= (count md) 50000)) md (subs md 0 (min 50000 (count md))))]
    (str
      "Extract receipt data from this OCR markdown:\n\n"
      "```markdown\n"
      md
      "\n```")))

(def ^:private default-hints-resource
  "expenses/receipt_refine_hints.edn")

(defonce ^:private hints-cache*
  (atom {:loaded? false
         :value {}}))

(defn- safe-read-edn-map
  [s]
  (try
    (let [v (edn/read-string (or s ""))]
      (if (map? v) v {}))
    (catch Exception _
      {})))

(defn hints-config
  "Load optional receipt refine hints.

  The hints file is an EDN map. Lookup keys:
  - supplier key: <supplier_key>
  - store fingerprint: <supplier_key>/<store_key>

  Values can be a string or a vector of strings.

  Optional env override:
  - RECEIPT_REFINE_HINTS_PATH

  Returns an empty map when not configured or invalid."
  []
  (let [{:keys [loaded? value]} @hints-cache*]
    (if loaded?
      value
      (let [path (some-> (System/getenv "RECEIPT_REFINE_HINTS_PATH") str str/trim not-empty)
            resource (io/resource default-hints-resource)
            s (cond
                (seq path) (try (slurp path) (catch Exception _ nil))
                resource (try (slurp resource) (catch Exception _ nil))
                :else nil)
            cfg (safe-read-edn-map s)]
        (reset! hints-cache* {:loaded? true
                              :value cfg})
        cfg))))

(defn- normalize-hints
  [x]
  (cond
    (string? x)
    (let [s (some-> x str str/trim not-empty)]
      (cond-> [] (seq s) (conj s)))

    (sequential? x)
    (->> x
      (map (fn [v] (some-> v str str/trim not-empty)))
      (remove nil?)
      vec)

    :else
    []))

(defn- hint-val
  [m k]
  (or (get m k)
    (when (string? k)
      (get m (keyword k)))))

(defn- hints-for-context
  [context]
  (let [cfg (hints-config)
        suppliers-map (when (map? (get cfg :suppliers)) (get cfg :suppliers))
        fingerprints-map (when (map? (get cfg :fingerprints)) (get cfg :fingerprints))
        supplier-key (some-> (:supplier_key context) str str/trim not-empty)
        store-key (some-> (:store_key context) str str/trim not-empty)
        fingerprint (when (and (seq supplier-key) (seq store-key))
                      (str supplier-key "/" store-key))
        supplier-hints (normalize-hints
                         (or (when (map? suppliers-map) (hint-val suppliers-map supplier-key))
                           (hint-val cfg supplier-key)))
        fingerprint-hints (normalize-hints
                            (or (when (map? fingerprints-map) (hint-val fingerprints-map fingerprint))
                              (hint-val cfg fingerprint)))]
    (->> (concat fingerprint-hints supplier-hints)
      (remove nil?)
      distinct
      vec)))

(defn- store-fingerprint
  [context]
  (or (some-> (:store_fingerprint context) str str/trim not-empty)
    (let [supplier-key (some-> (:supplier_key context) str str/trim not-empty)
          store-key (some-> (:store_key context) str str/trim not-empty)]
      (when (and (seq supplier-key) (seq store-key))
        (str supplier-key "/" store-key)))))

(defn- context-system-message
  "Build an additional system message with supplier/store context.

  This message is intended as a *hint*, not a source of truth. The receipt
  markdown remains the only authoritative input."
  [context]
  (when (map? context)
    (let [supplier-key (some-> (:supplier_key context) str str/trim not-empty)
          supplier-name (some-> (:supplier_name context) str str/trim not-empty)
          store-key (some-> (:store_key context) str str/trim not-empty)
          store-name (some-> (:store_display_name context) str str/trim not-empty)
          store-address (some-> (:store_address context) str str/trim not-empty)
          fingerprint (store-fingerprint context)
          pj-key? (and (seq store-key) (str/starts-with? store-key "pj-"))
          hints (hints-for-context context)]
      (when (or (seq supplier-key) (seq supplier-name) (seq store-key) (seq store-name) (seq store-address))
        (str
          "Store context (extra signal; do not invent values):\n"
          (when supplier-key (str "- supplier_key: " supplier-key "\n"))
          (when supplier-name (str "- supplier_name: " supplier-name "\n"))
          (when store-key (str "- store_key: " store-key "\n"))
          (when fingerprint (str "- store_fingerprint: " fingerprint "\n"))
          (when store-name (str "- store_display_name: " store-name "\n"))
          (when store-address (str "- store_address: " store-address "\n"))
          (when pj-key?
            "- Note: store_key starts with `pj-` (stable branch identifier).\n")
          (when (seq hints)
            (str
              "\nFormat hints (apply only when consistent with the markdown):\n"
              (->> hints
                (map (fn [h] (str "- " h)))
                (str/join "\n")))))))))

(defn build-chat-messages
  "Build the OpenAI-style messages payload for receipt extraction.

  When `context` is provided, supplier/store fingerprint context and optional format
  hints are appended to the single system message. Only one system message is sent
  because some OpenAI-compatible chat templates (e.g. Qwen) reject a system message
  that is not the first message.

  Context is a map that may contain:
  {:supplier_key .. :supplier_name .. :store_key .. :store_display_name .. :store_address ..}

  The receipt markdown remains the only authoritative input."
  ([markdown]
   (build-chat-messages markdown nil))
  ([markdown context]
   (let [ctx-msg (context-system-message context)
         system-content (cond-> receipt-extraction-system-prompt
                          (seq ctx-msg) (str "\n\n" ctx-msg))]
     [{:role "system" :content system-content}
      {:role "user" :content (receipt-extraction-user-prompt markdown)}])))

(defn normalize-llm-extraction
  "Normalize a parsed JSON extraction map by trimming strings and dropping empty values.

  This is intentionally conservative and should not invent values."
  [m]
  (when (map? m)
    (letfn [(trim-str [s] (some-> s str str/trim not-empty))
            (normalize-merchant [merchant]
              (when (map? merchant)
                (-> merchant
                  (update "name" trim-str)
                  (update "address" trim-str)
                  (update "tax_id" trim-str))))
            (normalize-item [item]
              (when (map? item)
                (-> item
                  (update "name" trim-str))))]
      (-> m
        (update "merchant" normalize-merchant)
        (update "purchased_at" trim-str)
        (update "currency" trim-str)
        (update "items" (fn [items] (when (sequential? items) (mapv normalize-item items))))))))

(defn cents->money
  "Convert integer cents to a BigDecimal money amount.

  Returns nil for non-numeric inputs."
  [cents]
  (when (number? cents)
    (.divide (bigdec cents) 100M)))

(defn- ->bd
  [n]
  (cond
    (nil? n) nil
    (instance? java.math.BigDecimal n) n
    (number? n) (bigdec n)
    :else nil))

(defn- safe-divide
  "Divide two BigDecimals with rounding.

  BigDecimal division can throw on non-terminating expansions; we round.
  Scale is chosen to preserve receipt precision without exploding digits."
  [a b]
  (when (and a b (not (zero? (.compareTo ^java.math.BigDecimal b 0M))))
    (.divide ^java.math.BigDecimal a ^java.math.BigDecimal b 6 java.math.RoundingMode/HALF_UP)))

(defn- approx-eq-money?
  [a b]
  (when (and a b)
    (<= (double (.abs (.subtract (bigdec a) (bigdec b)))) 0.01)))

(defn- items-line-total
  [items]
  (when (sequential? items)
    (let [amounts (->> items
                    (keep :line_total)
                    (keep ->bd)
                    vec)]
      (when (seq amounts)
        (reduce #(.add ^java.math.BigDecimal %1 ^java.math.BigDecimal %2) 0M amounts)))))

(defn- reconcile-total-from-items
  "When LLM output confuses payment amount with receipt total, repair totals.

  Heuristic:
  - if subtotal matches sum(items)
  - and total does not match sum(items)
  - and tax does not explain the difference
  - and the reported total is materially larger than the purchased-items subtotal
    (more like a tendered payment amount than a missed line)
  then prefer subtotal as grand total.

  Also fills missing :total from items sum when possible."
  [totals items]
  (let [subtotal (->bd (:subtotal totals))
        tax (->bd (:tax totals))
        total (->bd (:total totals))
        items-total (items-line-total items)
        subtotal-matches-items? (and subtotal items-total (approx-eq-money? subtotal items-total))
        total-matches-items? (and total items-total (approx-eq-money? total items-total))
        tax-explains-diff? (and subtotal tax total
                             (approx-eq-money? (.add subtotal tax) total))
        total-gap (when (and subtotal total)
                    (.subtract ^java.math.BigDecimal total ^java.math.BigDecimal subtotal))
        total-gap-ratio (when (and subtotal total-gap (pos? (.compareTo subtotal 0M)))
                          (safe-divide total-gap subtotal))
        likely-payment-total? (boolean
                                (and total-gap
                                  total-gap-ratio
                                  (pos? (.compareTo total-gap 0M))
                                  (>= (.compareTo total-gap 1.00M) 0)
                                  (>= (double total-gap-ratio) 0.20)))]
    (cond-> totals
      (and items-total (nil? total))
      (assoc :total items-total)

      (and items-total subtotal-matches-items? (not total-matches-items?) (not tax-explains-diff?) likely-payment-total?)
      (assoc :total subtotal))))

(defn llm-extraction->receipt-extraction
  "Convert the model-facing cents schema into the app's internal extraction shape.

  Output shape matches `receipt-ocr.extraction/ReceiptExtraction`:
  {:merchant {:name .. :address .. :tax_id ..}
   :purchased_at <string-or-nil>
   :currency <string-or-nil>
   :totals {:subtotal <decimal> :tax <decimal> :total <decimal>}
   :items [{:raw_label <string> :qty <decimal> :unit_price <decimal> :line_total <decimal>} ...]}"
  [llm-extraction]
  (let [m (normalize-llm-extraction llm-extraction)
        merchant (get m "merchant")
        totals (get m "totals")
        items (get m "items")
        parsed-items (when (sequential? items)
                       (->> items
                         (keep
                           (fn [item]
                             (let [name (some-> (get item "name") str str/trim not-empty)
                                   qty (->bd (get item "quantity"))
                                   unit-price (cents->money (get item "unit_price_cents"))
                                   line-total (cents->money (get item "line_total_cents"))
                                   unit-price (or unit-price (safe-divide line-total qty))
                                   line-total (or line-total (when (and unit-price qty)
                                                               (.multiply ^java.math.BigDecimal unit-price ^java.math.BigDecimal qty)))]
                               (when (and name line-total)
                                 (cond-> {:raw_label name
                                          :line_total line-total}
                                   qty (assoc :qty qty)
                                   unit-price (assoc :unit_price unit-price))))))
                         vec))
        parsed-totals (when (map? totals)
                        (-> (cond-> {}
                              (some? (get totals "subtotal_cents"))
                              (assoc :subtotal (cents->money (get totals "subtotal_cents")))

                              (some? (get totals "tax_cents"))
                              (assoc :tax (cents->money (get totals "tax_cents")))

                              (contains? totals "total_cents")
                              (assoc :total (cents->money (get totals "total_cents"))))
                          (reconcile-total-from-items parsed-items)))]
    (cond-> {}
      (and (map? merchant) (seq (get merchant "name")))
      (assoc :merchant (cond-> {:name (get merchant "name")}
                         (get merchant "address") (assoc :address (get merchant "address"))
                         (get merchant "tax_id") (assoc :tax_id (get merchant "tax_id"))))

      (contains? m "purchased_at")
      (assoc :purchased_at (get m "purchased_at"))

      (contains? m "currency")
      (assoc :currency (get m "currency"))

      (map? parsed-totals)
      (assoc :totals parsed-totals)

      (some? parsed-items)
      (assoc :items parsed-items))))
