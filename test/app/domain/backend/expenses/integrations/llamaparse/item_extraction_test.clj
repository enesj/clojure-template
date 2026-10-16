(ns app.domain.backend.expenses.integrations.llamaparse.item-extraction-test
  (:require
    [app.domain.backend.expenses.integrations.llamaparse.receipt-extraction :as receipt-extract]
    [clojure.string :as str]
    [clojure.test :refer [deftest is]]))

(deftest receipt-extraction-parses-discount-and-qty-rows
  (let [resp {:text {:pages [{:text (str "====E=EE=5SE\n"
                                      "\"Pepco B-H\" d.o.o.\n"
                                      "2.12.2025. 19:46\n"
                                      "TOTAL: 14,60\n")}]}
              :items {:pages [{:items [{:type "header"
                                        :md (str "====E=EE=5SE\n"
                                              "\"Pepco B-H\" d.o.o.\n"
                                              "2.12.2025. 19:46\n")}
                                       {:type "table"
                                        :rows [["62778401 Item A" "10,00E"]
                                               ["t/pc" ""]
                                               ["-50,00%:" "5,00"]
                                               ["ITEM B" "9,60E"]
                                               ["2,000x" "4,80"]]}]}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= "Pepco B-H" (get-in extraction [:merchant :name])))
    (is (= "2025-12-02T19:46:00" (:purchased_at extraction)))
    (is (= 14.60M (bigdec (get-in extraction [:totals :total]))))
    (is (= 2 (count items)))
    (is (= "Item A" (:raw_label (first items))))
    (is (= 1M (bigdec (get-in items [0 :qty]))))
    (is (= 5M (bigdec (get-in items [0 :unit_price]))))
    (is (= 5M (bigdec (get-in items [0 :line_total]))))
    (is (= "ITEM B" (:raw_label (second items))))
    (is (= 2M (bigdec (get-in items [1 :qty]))))
    (is (= 4.80M (bigdec (get-in items [1 :unit_price]))))
    (is (= 9.60M (bigdec (get-in items [1 :line_total]))))))

(deftest receipt-extraction-parses-no-header-four-column-table-rows
  (let [resp {:items {:pages [{:items [{:type "table"
                                        :rows [["CIG DUNHILL ESSENCE BRONZE" "3,000x" "6,60" "19,80E"]
                                               ["CHIPSY XCUT SALTED 140G" "" "" "3,60E"]]}]}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 2 (count items)))
    (is (= {:raw_label "CIG DUNHILL ESSENCE BRONZE"
            :qty 3.000M
            :unit_price 6.60M
            :line_total 19.80M}
          (first items)))
    (is (= {:raw_label "CHIPSY XCUT SALTED 140G"
            :qty 1M
            :unit_price 3.60M
            :line_total 3.60M}
          (second items)))
    (is (= 23.40M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-parses-inline-discount-unit-row
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md (str "\"Pepco B-H\" d.o.o.\n"
                                              "Podružnica Sarajevo 2\n"
                                              "ul. Kolodvorska br.12\n"
                                              "71000 Sarajevo\n")}
                                       {:type "table"
                                        :rows [["62778401 Mirisna svijeca u staklu Premium Collec" "10,00E" ""]
                                               ["t/pc" "" "-50,00%: 5,00"]
                                               ["62778401 Mirisna svijeca u staklu Premium Collec" "10,00E" ""]
                                               ["t/pc" "" "-50,00%: 5,00"]]}]}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 2 (count items)))
    (is (= ["Mirisna svijeca u staklu Premium Collec"
            "Mirisna svijeca u staklu Premium Collec"]
          (mapv :raw_label items)))
    (is (= [5M 5M]
          (mapv (comp bigdec :line_total) items)))
    (is (= [5M 5M]
          (mapv (comp bigdec :unit_price) items)))
    (is (= 10M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-prefers-structured-text-and-rejects-qty-price-fragment-labels
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md (str "APOTEKE SARAJEVO\n"
                                              "12.02.2026. 15:05\n")}
                                       {:type "text"
                                        :value (str "2,000x 7,56 15,12E\n"
                                                 "-100,00%: 0,00\n"
                                                 "GALAS_ČAJ_UROLOŠKI_100_G_210f 7,40E\n"
                                                 "2PVC_KESA_SREDNJA_6f94 0,10E\n"
                                                 "TOTAL: 7,50")}]}]}
              :text {:pages [{:text (str "APOTEKE SARAJEVO\n"
                                      "12.02.2026. 15:05\n"
                                      "2,000x         7,56         15,12E\n"
                                      "-100,00%:      0,00\n"
                                      "GALAS_CAJ_UROLOSKI_100_G_210f7,40E\n"
                                      "2PVC_KESA_SREDNJA_6f94       0,10E\n"
                                      "TOTAL:               7,50\n")}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 2 (count items)))
    (is (= ["GALAS ČAJ UROLOŠKI 100 G 210f"
            "2PVC KESA SREDNJA 6f94"]
          (mapv :raw_label items)))
    (is (= [7.40M 0.10M]
          (mapv (comp bigdec :line_total) items)))
    (is (= 7.50M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-prefers-structured-code-block-over-noisy-page-text
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md (str "\"Pepco B-H\" d.o.o.\n"
                                              "Podružnica Sarajevo 2\n"
                                              "ul. Kolodvorska br.12\n"
                                              "71000 Sarajevo\n")}
                                       {:type "code"
                                        :value (str "62778401 Mirisna svijeca u staklu Premium Collec\n"
                                                 "t/pc                                      10,00E\n"
                                                 "                                          -50,00%:   5,00\n"
                                                 "62778401 Mirisna svijeca u staklu Premium Collec\n"
                                                 "t/pc                                      10,00E\n"
                                                 "                                          -50,00%:   5,00\n"
                                                 "TOTAL:                                    10,00")}]}]}
              :text {:pages [{:text (str "Pepco B-H d.0.o.\n"
                                      "25.12.2025. 19:46\n"
                                      "62778401 Mirisna svijeca u stak lu Premium Collec\n"
                                      "t/pc              -50,00%:                 10,00E\n"
                                      "                                            TyooL\n"
                                      "                                             5,00\n"
                                      "62778401 Mirisna svijeca u staklu Premium Collec\n"
                                      "t/pc              -50,00%:                 10,00E\n"
                                      "                                             5,00\n"
                                      "TOTAL:            10,00\n"
                                      "unaPr             )\n"
                                      "                                             0,00")}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 2 (count items)))
    (is (= ["Mirisna svijeca u staklu Premium Collec"
            "Mirisna svijeca u staklu Premium Collec"]
          (mapv :raw_label items)))
    (is (= [5M 5M]
          (mapv (comp bigdec :line_total) items)))
    (is (= 10M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-parses-structured-text-label-followed-by-qty-line
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md (str "\"KONZUM\" d.o.o. Sarajevo\n"
                                              "Podružnica br. 66\n"
                                              "Prodavnica br. 90 Sarajevo\n"
                                              "Braće Begić 3\n"
                                              "71101 SARAJEVO CENTAR\n")}
                                       {:type "text"
                                        :value (str "BF: 394987\n"
                                                 "06.01.2026. 15:53\n"
                                                 "SECER BRAZILAS 1KG\n"
                                                 "2,000x 1,50 3,00E\n"
                                                 "SECER SMEDI 800G\n"
                                                 "1,000x 3,25 3,25E\n"
                                                 "TOTAL: 6,25")}]}]}
              :text {:pages [{:text (str "KONZUM\n"
                                      "06.01.2026. 15:53\n"
                                      "TOTAL: 6,25\n")}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 2 (count items)))
    (is (= ["SECER BRAZILAS 1KG"
            "SECER SMEDI 800G"]
          (mapv :raw_label items)))
    (is (= [2M 1M]
          (mapv (comp bigdec :qty) items)))
    (is (= [1.50M 3.25M]
          (mapv (comp bigdec :unit_price) items)))
    (is (= [3.00M 3.25M]
          (mapv (comp bigdec :line_total) items)))
    (is (= 6.25M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-parses-structured-text-split-qty-and-price-lines
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md (str "JU \"APOTEKE SARAJEVO\" SARAJEVO\n"
                                              "APOTEKA \"KOSEVSKO BRDO\"\n"
                                              "BRACE BEGIC br.4\n"
                                              "71000 Sarajevo\n")}
                                       {:type "text"
                                        :value (str "BF: 234080\n"
                                                 "06.01.2026. 15:48\n"
                                                 "CASA_ZA_URIN_KLIK_125_ML_ROMED_48d7\n"
                                                 "2,000x\n"
                                                 "0,55 1,10E\n"
                                                 "TOPLOMJER_DIGITALNI_UEBE_TH1_COLOR_CVRST_17e0 7,15E\n"
                                                 "TOTAL: 8,25")}]}]}
              :text {:pages [{:text (str "APOTEKE SARAJEVO\n"
                                      "06.01.2026. 15:48\n"
                                      "TOTAL: 8,25\n")}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 2 (count items)))
    (is (= ["CASA ZA URIN KLIK 125 ML ROMED 48d7"
            "TOPLOMJER DIGITALNI UEBE TH1 COLOR CVRST 17e0"]
          (mapv :raw_label items)))
    (is (= [2M 1M]
          (mapv (comp bigdec :qty) items)))
    (is (= [0.55M 7.15M]
          (mapv (comp bigdec :unit_price) items)))
    (is (= [1.10M 7.15M]
          (mapv (comp bigdec :line_total) items)))
    (is (= 8.25M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-keeps-identical-repeated-item-rows
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md "\"CM-COSMETIC MARKET\" d.o.o. Vitez\n"}
                                       {:type "text"
                                        :md "BF: 337\n17.12.2025"}
                                       {:type "text"
                                        :md "A10150772 Snala za kosu BH231226\n1,95E"}
                                       {:type "text"
                                        :md "A10150772 Snala za kosu BH231226\n1,95E"}
                                       {:type "text"
                                        :md "TOTAL: 3,90"}]}]}
              :text {:pages [{:text (str "CM-COSMETIC MARKET\n"
                                      "17.12.2025\n"
                                      "TOTAL: 3,90\n")}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)
        {:keys [parsed-markdown]} (receipt-extract/response->receipt resp)]
    (is (= 2 (count items)) "two identical rows on the receipt are two items")
    (is (= [1.95M 1.95M] (mapv (comp bigdec :line_total) items)))
    (is (= 3.90M (bigdec (get-in extraction [:totals :total]))))
    (is (= 2 (count (re-seq #"Snala za kosu" parsed-markdown)))
      "refine markdown must keep both rows")))

(deftest receipt-extraction-applies-discount-lines-in-structured-text
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md "\"Pepco B-H\" d.o.o.\n"}
                                       {:type "text"
                                        :md "25.12.2025. 19:46"}
                                       {:type "text"
                                        :md "62778401 Mirisna svijeca u staklu Premium Collec\nt/pc\t10,00E"}
                                       {:type "text"
                                        :md "-50,00%:\t5,00"}
                                       {:type "text"
                                        :md "62778401 Mirisna svijeca u staklu Premium Collec\nt/pc\t10,00E"}
                                       {:type "text"
                                        :md "-50,00%:\t5,00"}
                                       {:type "text"
                                        :md "**TOTAL**:\t10,00"}]}]}
              :text {:pages [{:text (str "Pepco B-H\n"
                                      "25.12.2025.19:46\n"
                                      "TOTAL: 10,00\n")}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 2 (count items)))
    (is (= [5M 5M] (mapv (comp bigdec :line_total) items))
      "50% discount line reduces each item's line total")
    (is (= [5M 5M] (mapv (comp bigdec :unit_price) items)))
    (is (= 10M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-accepts-numeric-product-code-label-before-qty-line
  (let [resp {:items {:pages [{:items [{:type "text"
                                        :md (str "\"New Yorker BH\" d.o.o. Sarajevo\n"
                                              "Poslovna jedinica 1 Sarajevo\n"
                                              "BULEVAR FRANCA LEHARA 2\n"
                                              "71000 Sarajevo")}
                                       {:type "text" :md "BF: 446392"}
                                       {:type "text" :md "10.02.2026. 17:25"}
                                       {:type "text" :md "27150 Amisu Dzemper/Pullove"}
                                       {:type "text" :md "1,000x 9,95 9,95E"}
                                       {:type "text" :md "TOTAL: 9,95"}]}]}
              :text {:pages [{:text (str "New Yorker BH\n"
                                      "10.02.2026. 17:25\n"
                                      "TOTAL: 9,95\n")}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 1 (count items)) "5-digit product code must not be mistaken for a postal code")
    (is (= "Amisu Dzemper/Pullove" (:raw_label (first items))))
    (is (= [9.95M] (mapv (comp bigdec :line_total) items)))
    (is (= 9.95M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-applies-percent-discount-with-final-price-amount
  (let [resp {:items {:pages [{:items [{:type "text" :md "BINGO d.o.o. Tuzla\nMaršala Tita br. 1\n71000 Sarajevo"}
                                       {:type "text" :md "BF: 1"}
                                       {:type "text" :md "10.02.2026. 17:25"}
                                       {:type "text" :md "D20155 PECIVO 160G LEPINA"}
                                       {:type "text" :md "1,000x 0,70 0,70E"}
                                       {:type "text" :md "-30%: 0,49"}
                                       {:type "text" :md "TOTAL: 0,49"}]}]}
              :text {:pages [{:text "BINGO\n10.02.2026. 17:25\nTOTAL: 0,49\n"}]}}
        items (:items (receipt-extract/response->extraction resp))]
    (is (= [0.49M] (mapv (comp bigdec :line_total) items))
        "'-30%: 0,49' must not be parsed as a -300,49 discount amount")))

(deftest receipt-extraction-applies-split-line-discount-and-wrapped-label
  (let [resp {:items {:pages [{:items [{:type "text" :md "JU \"APOTEKE SARAJEVO\" SARAJEVO\nAPOTEKA \"MARIJIN DVOR\"\nMARŠALA TITA BR.1\n71000 Sarajevo"}
                                       {:type "text" :md "BF: 38910"}
                                       {:type "text" :md "19.02.2026. 17:36"}
                                       {:type "text" :md "EUTHYROX_TABLETE_100_MCG_A_100_2f5"}
                                       {:type "text" :md "2"}
                                       {:type "text" :md "7,75E"}
                                       {:type "text" :md "PREXANIL_COMBI_A_FILM_TABLETE__10_"}
                                       {:type "text" :md "__2_5__2b3c"}
                                       {:type "text" :md "18,02E"}
                                       {:type "text" :md "-100,00%:"}
                                       {:type "text" :md "0,00"}
                                       {:type "text" :md "NEBILET_TABLETE_5_MG_A_28_1205"}
                                       {:type "text" :md "6,88E"}
                                       {:type "text" :md "-100,00%:"}
                                       {:type "text" :md "0,00"}
                                       {:type "text" :md "TOTAL:"}
                                       {:type "text" :md "7,75"}]}]}
              :text {:pages [{:text "APOTEKE SARAJEVO\n19.02.2026. 17:36\nTOTAL: 7,75\n"}]}}
        items (:items (receipt-extract/response->extraction resp))]
    (is (= [7.75M 0M 0M] (mapv (comp bigdec :line_total) items)))
    (is (= 3 (count items)))
    (is (re-find #"(?i)PREXANIL.*2b3c" (:raw_label (second items)))
        "wrapped label tail must be joined to the product name")))

(deftest receipt-extraction-reads-qty-line-marked-as-heading
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md "\"KONZUM\" d.o.o. Sarajevo\nPodružnica br. 66\n71101 SARAJEVO CENTAR\n17.03.2026. 14:28"}
                                       {:type "text" :md "BF: 26370"}
                                       {:type "text" :md "KAPS ESPRE 112G DG"}
                                       {:type "heading" :level 2
                                        :md "## 1,000x 10,45 10,45E"
                                        :value "1,000x 10,45 10,45E"}
                                       {:type "text" :md "VE: 17,00%"}
                                       {:type "text" :md "TOTAL: 10,45"}]}]}
              :text {:pages [{:text "KONZUM\n17.03.2026. 14:28\nTOTAL: 10,45\n"}]}}
        items (:items (receipt-extract/response->extraction resp))]
    (is (= 1 (count items)))
    (is (= "KAPS ESPRE 112G DG" (:raw_label (first items))))
    (is (= [10.45M] (mapv (comp bigdec :line_total) items)))))

(deftest receipt-extraction-prefers-text-items-matching-total-over-partial-table
  (let [resp {:items {:pages [{:items [{:type "text" :md "TROPIC MALOPRODAJA D.O.O.\nP.J. \"TROPIC MALOPRODAJA 61\"\nKolodvorska br.12\n71000 Sarajevo"}
                                       {:type "text"
                                        :md (str "BF: 70034\n25.03.2026. 13:29")}
                                       {:type "table"
                                        :md "| 0,280x 7,95 | 2,23E |\n| Kukuruza 210 tk RK /KG | 1,35E |"
                                        :rows [["0,280x 7,95" "2,23E"]
                                               ["Kukuruza 210 tk RK /KG" "1,35E"]]}
                                       {:type "text" :md "TOTAL: 23,58"}]}]}
              :text {:pages [{:text (str "TROPIC MALOPRODAJA D.O.O.\n"
                                      "BF: 70034           FISKALNI RACUN\n"
                                      "   25.03.2026.13:29\n"
                                      "   HLJEB KARINGTON SA Z/KG             3,50E\n"
                                      "   VEGETARIAN PAELLA   /KG\n"
                                      "   0,226x                    24,95     5,64E\n"
                                      "   TELECA DZIGERICA     /KG\n"
                                      "   0,298x                    24,95      7,44E\n"
                                      "   Salata RK 1kg RK     /KG\n"
                                      "   0,344x                 9,95          3,42E\n"
                                      "  Krompir kuvani 1kg K/KG\n"
                                      "  0,280x                  7,95          2,23E\n"
                                      "  Kukuruza 210 tk RK /KG                1,35E\n"
                                      " VE: 17,00%\n"
                                      "TOTAL:                                 23,58\n")}]}}
        items (:items (receipt-extract/response->extraction resp))]
    (is (= [3.50M 5.64M 7.44M 3.42M 2.23M 1.35M] (mapv (comp bigdec :line_total) items)))
    (is (= "HLJEB KARINGTON SA Z/KG" (:raw_label (first items)))
        "date line without a space before the time must still end the header")))

(deftest receipt-extraction-ignores-payment-method-line-as-item
  (let [resp {:items {:pages [{:items [{:type "text" :md "\"GREEN OIL\" d.o.o.\nMARKA MARULIĆA 4\n71120 NOVO SARAJEVO"}
                                       {:type "text" :md "07.04.2026. 17:37"}
                                       {:type "text"
                                        :md (str "PREMIUM bezolovni benzin 95. BAS\n20,070x 3,00 60,21E\n-3,33%: 58,21\n"
                                              "Taksa naftnih derivata\n20,070x 0,01 0,20M")}
                                       {:type "text" :md "VE: 17,00%\nVM: 0,00%"}
                                       {:type "text" :md "TOTAL: 58,41"}
                                       {:type "text" :md "UPLAĆENO:"}
                                       {:type "text" :md "Ček: 58,41"}]}]}
              :text {:pages [{:text "GREEN OIL\n07.04.2026. 17:37\nTOTAL: 58,41\n"}]}}
        items (:items (receipt-extract/response->extraction resp))]
    (is (= [58.21M 0.20M] (mapv (comp bigdec :line_total) items))
        "'VM:' tax line and 'Ček:' payment line must not become an item")))

(deftest receipt-extraction-does-not-take-volume-suffix-as-price
  (let [resp {:items {:pages [{:items [{:type "text" :md "\"KONZUM\" d.o.o. Sarajevo\nZMAJA OD BOSNE 7-7 A\n71000 Sarajevo"}
                                       {:type "text" :md "07.04.2026. 19:35"}
                                       {:type "text"
                                        :md (str "SOK COCA COLA 0,25L\n1,000x 1,30 1,30E\n"
                                              "SOK TONIC WATER 1L\n1,000x 2,10 2,10E")}
                                       {:type "text" :md "TOTAL: 3,40"}]}]}
              :text {:pages [{:text "KONZUM\n07.04.2026. 19:35\nTOTAL: 3,40\n"}]}}
        items (:items (receipt-extract/response->extraction resp))]
    (is (= [1.30M 2.10M] (mapv (comp bigdec :line_total) items)))
    (is (= "SOK COCA COLA 0,25L" (:raw_label (first items))))))

(deftest receipt-extraction-accepts-barcode-only-item-label
  (let [resp {:items {:pages [{:items [{:type "text" :md "\"CORTIX\" d.o.o. Sarajevo\nul. Vrbanja 1.\n71 000 Sarajevo"}
                                       {:type "text" :md "02.04.2026. 19:58"}
                                       {:type "text" :md "69 60936564905\t22,95E"}
                                       {:type "text" :md "VE: 17,00%"}
                                       {:type "text" :md "TOTAL:\t22,95"}]}]}
              :text {:pages [{:text "CORTIX\n02.04.2026. 19:58\nTOTAL: 22,95\n"}]}}
        items (:items (receipt-extract/response->extraction resp))]
    (is (= [22.95M] (mapv (comp bigdec :line_total) items)))
    (is (= "69 60936564905" (:raw_label (first items))))))

(deftest receipt-extraction-joins-wrapped-label-ending-in-dimensions-and-barcode-label
  (let [resp {:items {:pages [{:items [{:type "text" :md "\"PENNY PLUS\" d.o.o.\nPRODAVNICA br. 1 Sarajevo\n71000 SARAJEVO"}
                                       {:type "text" :md "09.05.2026. 14:39"}
                                       {:type "text"
                                        :md (str "ORMAR ZA PRVU POMOĆ 220x125x300mm\nBIJELI EB-001\t24,95E\n"
                                              "VRECICA PVC CRVENA 280+(2x80)x550x\n0021 10025480\t0,10E")}
                                       {:type "text" :md "TOTAL:\t25,05"}]}]}
              :text {:pages [{:text "PENNY PLUS\n09.05.2026. 14:39\nTOTAL: 25,05\n"}]}}
        items (:items (receipt-extract/response->extraction resp))]
    (is (= [24.95M 0.10M] (mapv (comp bigdec :line_total) items)))
    (is (str/starts-with? (:raw_label (first items)) "ORMAR ZA PRVU POMOĆ 220x125x300mm")
        "wrapped first half of the label is kept")))

(deftest receipt-extraction-prefers-total-line-over-rounded-tendered-amount
  (let [resp {:items {:pages [{:items [{:type "text" :md "\"PENNY PLUS\" d.o.o.\nPRODAVNICA br. 1 Sarajevo\n71000 SARAJEVO"}
                                       {:type "text" :md "11.05.2026. 20:28"}
                                       {:type "text" :md "ITEM A\t50,00E\nITEM B\t43,69E"}
                                       {:type "text" :md "**TOTAL**: **93,69**"}
                                       {:type "text" :md "**UPLAĆENO**: 93,67"}
                                       {:type "text" :md "Ukupno:\t93,67"}]}]}
              :text {:pages [{:text "PENNY PLUS\n11.05.2026. 20:28\nTOTAL: 93,69\nUPLAĆENO: 93,67\n"}]}}
        extraction (receipt-extract/response->extraction resp)]
    (is (= 93.69M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-takes-first-amount-from-multi-amount-total-line
  (let [resp {:items {:pages [{:items [{:type "text" :md "\"KONZUM\" d.o.o. Sarajevo\nZMAJA OD BOSNE 7-7 A\n71000 Sarajevo"}
                                       {:type "text" :md "07.04.2026. 19:35"}
                                       {:type "text" :md "ITEM A\t6,00E\nITEM B\t4,87E"}
                                       {:type "text" :md " TOTAL:                   10,87               10.87"}
                                       {:type "text" :md " UPLACENO:            3/2                      10,87"}]}]}
              :text {:pages [{:text "KONZUM\n07.04.2026. 19:35\n"}]}}
        extraction (receipt-extract/response->extraction resp)]
    (is (= 10.87M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-does-not-treat-product-percent-as-discount
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md (str "\"KONZUM\" d.o.o. Sarajevo\n"
                                              "Podružnica br. 66\n"
                                              "Prodavnica br. 90 Sarajevo\n"
                                              "Braće Begić 3\n"
                                              "71101 SARAJEVO CENTAR\n")}
                                       {:type "table"
                                        :rows [["MLIJEKO MEGGLE 3,2% 657" "6,75E" ""]
                                               ["3,000x" "" "2,25"]]}]}]}
              :text {:pages [{:text (str "VE: 17,00%\n"
                                      "OSN. E: 5,77\n"
                                      "PDV E: 0,98\n"
                                      "PDV: 0,98\n")}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 1 (count items)))
    (is (= "MLIJEKO MEGGLE 3,2% 657" (:raw_label (first items))))
    (is (= 3M (bigdec (get-in items [0 :qty]))))
    (is (= 2.25M (bigdec (get-in items [0 :unit_price]))))
    (is (= 6.75M (bigdec (get-in items [0 :line_total]))))
    (is (= 6.75M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-ignores-cyrillic-tax-summary-table
  (let [resp {:items {:pages [{:items [{:type "text"
                                        :value (str "ФИСКАЛНИ РАЧУН\n"
                                                 "PEPCO B-H DOO Подружница Сарајево 4\n"
                                                 "Спасовданска 20\n"
                                                 "Ist. Novo Sarajevo\n")}
                                       {:type "table"
                                        :rows [["Назив" "Цијена" "Кол." "Укупно"]
                                               ["2200663305162 63305105 Mirisna svijeca u staklu Premium Collec (E)" "10,00" "1" "10,00"]
                                               ["2217931679665 31679635 Svijeca \"silver & gold\" s poklopcem 13.5 (E)" "10,00" "1" "10,00"]
                                               ["2200833176462 33176405 jaja 6cm, 12 kom_ONE_Dark beige (E)" "2,50" "1" "2,50"]]}
                                       {:type "table"
                                        :rows [["Ознака" "Назив" "Стопа" "Порез"]
                                               ["E" "ПДВ" "17%" "3,27"]]}]}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)
        egg-item (nth items 2)]
    (is (= 3 (count items)))
    (is (= "PEPCO B-H" (get-in extraction [:merchant :name])))
    (is (= 22.50M (bigdec (get-in extraction [:totals :total]))))
    (is (= [10.00M 10.00M 2.50M]
          (mapv (comp bigdec :line_total) items)))
    (is (= "33176405 jaja 6cm, 12 kom ONE Dark beige (E)" (:raw_label egg-item)))
    (is (= 2.50M (bigdec (:unit_price egg-item))))
    (is (= 2.50M (bigdec (:line_total egg-item))))
    (is (every? (fn [{:keys [unit_price line_total]}]
                  (and (not (neg? (bigdec unit_price)))
                    (not (neg? (bigdec line_total)))))
          items))))

(deftest receipt-extraction-prefers-header-over-body-items
  (let [resp {:items {:pages [{:items [{:type "header"
                                        :md (str "\"UNI-EXPERT\" d.o.o.\n"
                                              "Trg Djece Sarajeva broj 1\n"
                                              "71000 Sarajevo\n"
                                              "JIB: 4245018500121\n")}
                                       {:type "table"
                                        :rows [["Logilink Baterije AAA Alkaline" "2,70E"]]}]}]}
              :text {:pages [{:text (str "\"UNI-EXPERT\" d.o.o.\n"
                                      "Trg Djece Sarajeva broj 1\n"
                                      "71000 Sarajevo\n"
                                      "JIB: 4245018500121\n"
                                      "FISKALNI RACUN\n"
                                      "BF: 28933\n"
                                      "24.01.2026. 12:30\n"
                                      "Logilink Baterije AAA Alkaline\n"
                                      "TOTAL: 6,70\n")}]}}
        extraction (receipt-extract/response->extraction resp)]
    (is (= "UNI-EXPERT" (get-in extraction [:merchant :name])))))


(deftest receipt-extraction-ignores-tax-breakdown-table-rows
  (let [resp {:items {:pages [{:items [{:type "text" :md "ESIR vreme: 27.05.2026. 13:43:03"}
                                       {:type "table"
                                        :rows [["Naziv" "Cijena" "Kol." "Ukupno"]
                                               ["00000000 Bitter lemon (E)" "3,50" "1,000" "3,50"]
                                               ["00000000 Kafa (E)" "2,30" "1,000" "2,30"]]}
                                       {:type "text" :md "Ukupan iznos: 5,80"}
                                       {:type "table"
                                        :rows [["Oznaka" "Naziv" "Stopa" "Porez"]
                                               ["E" "PDV" "17%" "0,84"]]}]}]}
              :text {:pages [{:text "ESIR vreme: 27.05.2026. 13:43:03\nUkupan iznos: 5,80\n"}]}}
        extraction (receipt-extract/response->extraction resp)]
    (is (= ["Bitter lemon (E)" "Kafa (E)"] (mapv :raw_label (:items extraction))))
    (is (= 5.80M (bigdec (get-in extraction [:totals :total]))))))

(deftest receipt-extraction-parses-cyrillic-price-qty-total-text-rows
  (let [text (str "ФИСКАЛНИ РАЧУН\n\n"
               "JYSK д.о.о. Сарајево\n\n"
               "Артикли\n\n"
               "Назив Цијена Кол. Укупно\n\n"
               "00000006611544 Neklizajuća podloga BUR OT 120x180 bež/KOM (E)\n\n"
               "15,00 1,000 15,00\n\n"
               "00000004922012 Kutija PROBOX 70L sa po klopcem prozirna/KOM (E)\n\n"
               "40,00 1,000 40,00\n\n"
               "Укупан износ: 55,00\n\n"
               "Платна картица: 55,00\n\n"
               "Ознака Назив Стопа Порез\n\n"
               "E ПДВ 17% 7,99\n\n"
               "Укупан износ пореза: 7,99\n\n"
               "ПФР време: 27.05.2026. 13:28:20\n")
        resp {:items {:pages [{:items [{:type "text" :md text}]}]}
              :text {:pages [{:text text}]}}
        extraction (receipt-extract/response->extraction resp)
        items (:items extraction)]
    (is (= 2 (count items)))
    (is (= [15.00M 40.00M] (mapv (comp bigdec :line_total) items)))
    (is (= 55.00M (bigdec (get-in extraction [:totals :total]))))))

(defn- text-labels
  [text]
  (mapv :raw_label (:items (receipt-extract/response->extraction
                             {:items {:pages [{:items [{:type "text" :md text}]}]}
                              :text {:pages [{:text text}]}}))))

(deftest receipt-extraction-joins-wrapped-label-tails
  (is (= ["FLASTER MICROTAPE 25 MM X 9 1 M A 12 3a40"
          "KOMPRESE STERILNE 5 CM X 5 CM 12 slojev 7369"]
        (text-labels (str "APOTEKE SARAJEVO\n06.05.2026.17:10\n"
                       "FLASTER_MICROTAPE_25_MM_X_9_1_M_A_\n12_3a40\t3,70E\n"
                       "KOMPRESE_STERILNE_5_CM_X_5_CM__12_\nslojev_7369\n2,000x\t1,00\t2,00E\n"
                       "TOTAL:\t5,70\n")))
    "pharmacy name line + code tail (inline price, and qty line after the tail)")
  (is (= ["RIONDO PROSECCO DOC SPUMANTE 200 KO" "FINI MINI JUHA OD RAJC. I MOZZ 2/ KO"]
        (text-labels (str "SAMON PROMET\n14.05.2026.19:29\n"
                       "RIONDO PROSECCO DOC SPUMANTE 200_\nKO 5,60E\n"
                       "FINI MINI JUHA OD RAJC. I MOZZ 2/\nKO\n2,000x 1,00 2,00E\n"
                       "TOTAL: 7,60\n")))
    "unit stub 'KO' on its own line")
  (is (= ["CIGARETE DUNHILL ESSENCE BRONZE/KOM"]
        (text-labels (str "Robot General Trading Co\n01.04.2026. 18:13\n"
                       "CIGARETE DUNHILL ESSENCE BRONZE/KO\nM\n\n5,000x 6,60 33,00E\n"
                       "TOTAL: 33,00\n")))
    "'/KO' + 'M' is re-joined into '/KOM'")
  (is (= ["Espresso kafa"]
        (text-labels (str "Caffe Bar\n27.02.2026. 14:35\n"
                       "Espresso kafa\n1,000x 2,50 2,50A\n\nVA: 0,00% 2,50\n"
                       "TOTAL: 2,50\n")))
    "a VAT rate line is not an item"))

(deftest receipt-extraction-skips-ocr-misread-tax-lines
  (is (= ["CIG DUNHIL ESSEN BR"]
        (text-labels (str "KONZUM\n13.02.2026. 13:17\n"
                       "CIG DUNHIL ESSEN BR\n3,000x 6,60 19,80E\n"
                       "UE: 17,00%\nOSN. E: 12,90\nPDU E: 16,92\nPDU: 2,88\n"
                       "TOTAL: 19,80\n")))
    "'PDU'/'UE' are OCR misreadings of 'PDV'/'VE'"))
