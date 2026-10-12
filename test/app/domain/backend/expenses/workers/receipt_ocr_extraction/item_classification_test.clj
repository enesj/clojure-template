(ns app.domain.backend.expenses.workers.receipt-ocr-extraction.item-classification-test
  (:require
    [app.domain.backend.expenses.services.article-aliases :as article-aliases]
    [app.domain.backend.expenses.services.receipts.queries :as receipt-queries]
    [app.domain.backend.expenses.services.receipts.status :as receipt-status]
            [app.domain.backend.expenses.services.stores :as stores]
    [app.domain.backend.expenses.services.supplier-aliases :as supplier-aliases]
    [app.domain.backend.expenses.services.suppliers :as suppliers]
    [app.domain.backend.expenses.workers.receipt-ocr.extraction :as extraction]
    [clojure.test :refer [deftest is]]))

(deftest non-item-reason-keeps-legitimate-item-label-with-br-fino
  (let [non-item-reason #'extraction/non-item-reason
        ctx {:items-count 10
             :grand-total 41.94M}
        item {:raw_label "MILERAM BR&FINO 400G"
              :qty 1
              :unit_price 3.30
              :line_total 3.30}]
    (is (nil? (non-item-reason ctx item)))))

(deftest non-item-reason-keeps-item-with-leading-header-token
  (let [non-item-reason #'extraction/non-item-reason
        ctx {:items-count 10
             :grand-total 49.92M}
        item {:raw_label "Artikal BOMBONJERA 230G RAFFAELLO FER"
              :qty 1
              :unit_price 9.90
              :line_total 9.90}]
    (is (nil? (non-item-reason ctx item)))))

(deftest non-item-reason-filters-br-colon-reference-as-metadata
  (let [non-item-reason #'extraction/non-item-reason
        ctx {:items-count 10
             :grand-total 41.94M}
        item {:raw_label "br: 12345/AB"
              :qty 1
              :unit_price 1.00
              :line_total 1.00}]
    (is (= :metadata (non-item-reason ctx item)))))

(deftest non-item-reason-keeps-american-product-but-drops-american-express
  (let [non-item-reason #'extraction/non-item-reason
        ctx {:items-count 6
             :grand-total 19.76M}]
    (is (nil? (non-item-reason ctx {:raw_label "KEKS COKOLADA I LJESNJAK AMERICAN 135G"
                                    :qty 1 :unit_price 1.50 :line_total 1.50})))
    (is (= :payment (non-item-reason ctx {:raw_label "American Express"
                                          :qty 1 :unit_price 19.76 :line_total 19.76})))))

(deftest persist-extract-result-filters-cyrillic-summary-rows-from-items
  (let [receipt-id (java.util.UUID/randomUUID)
        mapped-supplier-id (java.util.UUID/randomUUID)
        alias-id (java.util.UUID/randomUUID)
        stored (atom nil)
        calls (atom {:article-aliases 0
                     :labels []})]
    (with-redefs [receipt-queries/get-receipt (fn [_db _rid]
                                                {:id receipt-id
                                                 :status "uploaded"})
                  receipt-status/store-extraction-results!
                  (fn [_db _rid payload]
                    (reset! stored payload)
                    nil)
                  receipt-status/update-status! (fn [& _] nil)
                  supplier-aliases/find-or-create-alias! (fn [_db _raw-label]
                                                           {:id alias-id
                                                            :supplier_id mapped-supplier-id})
                  suppliers/resolve-or-create-supplier-with-places! (fn [& _]
                                                                      (throw (ex-info "Should not be called" {})))
                  stores/resolve-store-from-merchant (fn [& _] nil)
                  article-aliases/find-or-create-alias!
                  (fn [_db _supplier-id raw-label & _]
                    (swap! calls (fn [m]
                                   (-> m
                                     (update :article-aliases inc)
                                     (update :labels conj raw-label))))
                    {:id (java.util.UUID/randomUUID)})]
      (let [extract-result {:parsed-markdown ""
                            :extraction {:merchant {:name "TROPIC MALOPRODAJA"}
                                         :totals {:total 6.78}
                                         :items [{:raw_label "ITEM A" :qty 1 :unit_price 2.99 :line_total 2.99}
                                                 {:raw_label "ITEM B" :qty 1 :unit_price 3.79 :line_total 3.79}
                                                 {:raw_label "Примљено средстава" :qty 1 :unit_price 6.78 :line_total 6.78}
                                                 {:raw_label "Платна картица" :qty 1 :unit_price 6.78 :line_total 6.78}
                                                 {:raw_label "Укупан износ без пореза" :qty 1 :unit_price 5.79 :line_total 5.79}
                                                 {:raw_label "Укупан износ пореза" :qty 1 :unit_price 0.99 :line_total 0.99}
                                                 {:raw_label "Укупан промет (Е)" :qty 1 :unit_price 6.78 :line_total 6.78}]}}
            res (extraction/persist-extract-result!
                  ::db
                  receipt-id
                  extract-result
                  {:default-currency "BAM"
                   :places-cfg {}
                   :user-region "BA"
                   :defer-refine? true})
            stored-items (get-in @stored [:raw_extract_json :extraction :items])
            post-processing (get-in @stored [:raw_extract_json :post_processing])]
        (is (= receipt-id (:receipt-id res)))
        (is (= "extracted" (:status res)))
        (is (= "extracted" (:effective-status res)))
        (is (= 2 (count stored-items)))
        (is (= #{"ITEM A" "ITEM B"}
              (set (map :raw_label stored-items))))
        (is (= 2 (:article-aliases @calls)))
        (is (= #{"ITEM A" "ITEM B"}
              (set (:labels @calls))))
        (is (= 5 (:dropped-count post-processing)))))))