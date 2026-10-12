(ns app.domain.backend.expenses.integrations.cerebras.receipt-refine-test
  (:require
    [app.domain.backend.expenses.integrations.cerebras.receipt-refine :as receipt-refine]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]))

(defn- schema-nodes
  "Return a lazy seq of all nodes (maps + scalars) in a JSON-schema-shaped structure."
  [x]
  (letfn [(branch? [v]
            (or (map? v)
              (sequential? v)))
          (children [v]
            (cond
              (map? v) (vals v)
              (sequential? v) v
              :else nil))]
    (tree-seq branch? children x)))

(deftest receipt-extraction-schema-uses-anyof-not-type-lists
  (testing "Cerebras structured outputs does not accept `type: [..]` (list-of-types)"
    (let [schema receipt-refine/receipt-extraction-json-schema
          maps (filter map? (schema-nodes schema))
          offending (->> maps
                      (keep (fn [m]
                              (when (sequential? (get m "type"))
                                m)))
                      vec)]
      (is (empty? offending)
        (str "Found schema nodes using list-of-types under `type`: " (pr-str offending))))))

(deftest receipt-extraction-schema-avoids-minimum
  (testing "Cerebras structured outputs rejects numeric constraints like `minimum`"
    (let [schema receipt-refine/receipt-extraction-json-schema
          maps (filter map? (schema-nodes schema))
          offending (->> maps
                      (keep (fn [m]
                              (when (contains? m "minimum")
                                m)))
                      vec)]
      (is (empty? offending)
        (str "Found schema nodes using unsupported `minimum`: " (pr-str offending))))))

(deftest receipt-extraction-schema-has-anyof-for-known-nullables
  (testing "Sanity-check: top-level known nullable fields have anyOf"
    (let [schema receipt-refine/receipt-extraction-json-schema
          props (get schema "properties")]
      (is (contains? (get props "merchant") "anyOf"))
      (is (contains? (get props "purchased_at") "anyOf"))
      (is (contains? (get props "currency") "anyOf")))))

(deftest build-chat-messages-merges-context-into-single-system-message
  (testing "When context is provided, it is appended to the one leading system message"
    (let [markdown "# receipt"
          ctx {:supplier_key "konzum"
               :supplier_name "Konzum"
               :store_key "pj-66"
               :store_display_name "Konzum PJ 66"
               :store_address "Somewhere"
               :store_fingerprint "konzum/pj-66"}
          msgs (receipt-refine/build-chat-messages markdown ctx)]
      (is (= 2 (count msgs)))
      (is (= ["system" "user"] (map :role msgs)))
      (let [content (:content (nth msgs 0))]
        (is (str/starts-with? content receipt-refine/receipt-extraction-system-prompt))
        (is (re-find #"supplier_key: konzum" content))
        (is (re-find #"store_key: pj-66" content))
        (is (re-find #"store_fingerprint: konzum/pj-66" content))
        (is (re-find #"pj-" content))))))

(deftest build-chat-messages-appends-hints-when-configured
  (testing "Hints are appended when a matching supplier/store fingerprint is present"
    (with-redefs [receipt-refine/hints-config
                  (fn []
                    {"konzum" ["Currency is usually BAM."]
                     "konzum/pj-66" ["Items table starts after 'Naziv' header."]})]
      (let [markdown "# receipt"
            ctx {:supplier_key "konzum"
                 :store_key "pj-66"}
            msgs (receipt-refine/build-chat-messages markdown ctx)
            content (:content (nth msgs 0))]
        (is (re-find #"Format hints" content))
        (is (re-find #"Items table starts" content))
        (is (re-find #"Currency is usually" content))))))

(deftest system-prompt-includes-total-reconciliation-guidance
  (let [prompt receipt-refine/receipt-extraction-system-prompt]
    (is (re-find #"sum of items\[\]\.line_total_cents" prompt))
    (is (re-find #"missed item row, bag/deposit/fee row" prompt))
    (is (re-find #"100% discount rows" prompt))
    (is (re-find #"-100,00%: 0,00" prompt))
    (is (re-find #"Quantity-only or price-only fragments like '2,000x 7,56' are not valid standalone item names" prompt))
    (is (re-find #"0,25L" prompt))
    (is (re-find #"do not invent phantom items" prompt))))

(deftest llm-extraction-reconciles-payment-total-to-items-total
  (testing "When subtotal matches items but total looks like tendered payment, prefer subtotal as total"
    (let [raw {"merchant" {"name" "New Yorker BH" "address" nil "tax_id" nil}
               "purchased_at" "2026-02-10T17:25:00"
               "currency" nil
               "totals" {"subtotal_cents" 995 "tax_cents" nil "total_cents" 2000}
               "items" [{"name" "Amisu Dzemper/Pullove"
                         "quantity" 1.0
                         "unit_price_cents" 995
                         "line_total_cents" 995}]}
          ex (receipt-refine/llm-extraction->receipt-extraction raw)]
      (is (= 9.95M (bigdec (get-in ex [:totals :subtotal]))))
      (is (= 9.95M (bigdec (get-in ex [:totals :total]))))
      (is (= 9.95M (bigdec (get-in ex [:items 0 :line_total])))))))

(deftest llm-extraction-keeps-small-total-gap-when-it-looks-like-a-missed-line
  (testing "When subtotal matches items but the total is only slightly larger, keep the reported total for review"
    (let [raw {"merchant" {"name" "Konzum" "address" nil "tax_id" nil}
               "purchased_at" "2026-04-07T19:35:00"
               "currency" "BAM"
               "totals" {"subtotal_cents" 1445 "tax_cents" nil "total_cents" 1550}
               "items" [{"name" "SOK COCA COLA"
                         "quantity" 1.0
                         "unit_price_cents" 25
                         "line_total_cents" 25}
                        {"name" "BOMB MALINA&KUPI 100"
                         "quantity" 1.0
                         "unit_price_cents" 220
                         "line_total_cents" 220}
                        {"name" "SOK TONIC WATER 1L"
                         "quantity" 1.0
                         "unit_price_cents" 210
                         "line_total_cents" 210}
                        {"name" "STR SMOKVA XXL 400G"
                         "quantity" 1.0
                         "unit_price_cents" 595
                         "line_total_cents" 595}
                        {"name" "BOMB HAR JAG 175G"
                         "quantity" 1.0
                         "unit_price_cents" 395
                         "line_total_cents" 395}]}
          ex (receipt-refine/llm-extraction->receipt-extraction raw)]
      (is (= 14.45M (bigdec (get-in ex [:totals :subtotal]))))
      (is (= 15.50M (bigdec (get-in ex [:totals :total]))))
      (is (= 14.45M (->> ex :items (map :line_total) (reduce +) bigdec))))))

(deftest llm-extraction-keeps-total-when-tax-explains-difference
  (testing "When subtotal + tax explains total, keep total unchanged"
    (let [raw {"merchant" {"name" "Konzum" "address" nil "tax_id" nil}
               "purchased_at" "2026-02-10T17:25:00"
               "currency" "BAM"
               "totals" {"subtotal_cents" 850 "tax_cents" 145 "total_cents" 995}
               "items" [{"name" "Amisu Dzemper/Pullove"
                         "quantity" 1.0
                         "unit_price_cents" 995
                         "line_total_cents" 995}]}
          ex (receipt-refine/llm-extraction->receipt-extraction raw)]
      (is (= 8.50M (bigdec (get-in ex [:totals :subtotal]))))
      (is (= 1.45M (bigdec (get-in ex [:totals :tax]))))
      (is (= 9.95M (bigdec (get-in ex [:totals :total])))))))

