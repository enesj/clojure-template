(ns app.domain.backend.expenses.services.article-resolution.validation-test
  (:require
    [app.domain.backend.expenses.services.article-resolution.validation :as v]
    [clojure.test :refer [deftest is testing]]))

(def ^:private cfg
  {:auto-apply-threshold 80 :noise-threshold 90 :allow-new-subcategories? true})

(def ^:private ctx
  {:category-index {"Pakovana hrana i pića" {:id 1 :subcategories [{:id 10 :name "Bezalkoholna pića"}
                                                                      {:id 11 :name "Kafa"}]}
                    "Lična njega"           {:id 2 :subcategories []}}
   :taxonomy {:brand-parent-mappings {"Milka" "Mondelez International"}}
   :cfg cfg})

(def ^:private group {:candidates [{:key "meggle-mlijeko-2-8-1l" :name "Meggle mlijeko 2,8% 1L"}]})

(defn- item
  [m]
  (merge {:id "g1" :kind "article" :canonical_name "Coca-Cola 1,25L" :category "Pakovana hrana i pića"
          :subcategory "Bezalkoholna pića" :manufacturer nil :existing_article_key nil
          :confidence 92 :reason "x"}
    m))

(defn- check [m] (v/validate-llm-item ctx group (item m)))

(deftest validate-llm-item-test
  (testing "clean article is create without problems"
    (let [{:keys [decision problems]} (check {})]
      (is (= :create (:action decision)))
      (is (empty? problems))
      (is (= "Bezalkoholna pića" (:subcategory decision)))
      (is (false? (:subcategory-is-new decision)))))
  (testing "noise: confident vs not"
    (is (= {:action :noise} (select-keys (:decision (check {:kind "noise" :confidence 95})) [:action])))
    (is (empty? (:problems (check {:kind "noise" :confidence 95}))))
    (is (= [:low-confidence] (:problems (check {:kind "noise" :confidence 60})))))
  (testing "unknown"
    (let [{:keys [decision problems]} (check {:kind "unknown"})]
      (is (= :none (:action decision)))
      (is (= [:unknown] problems))))
  (testing "existing key must be an offered candidate"
    (let [ok (check {:existing_article_key "meggle-mlijeko-2-8-1l"})
          bad (check {:existing_article_key "hallucinated-key"})]
      (is (= :map-existing (:action (:decision ok))))
      (is (empty? (:problems ok)))
      (is (some #{:unknown-existing-key} (:problems bad)))))
  (testing "category"
    (is (some #{:unknown-category} (:problems (check {:category nil}))))
    (is (some #{:unknown-category} (:problems (check {:category "Subscriptions"})))))
  (testing "subcategory"
    (is (some #{:missing-subcategory} (:problems (check {:subcategory nil}))))
    (is (some #{:missing-subcategory} (:problems (check {:subcategory "  "}))))
    (is (some #{:missing-subcategory} (:problems (check {:subcategory "Opšte"}))))
    (is (some #{:missing-subcategory} (:problems (check {:subcategory "OSTALO"}))))
    (testing "case-insensitive match reuses the existing name"
      (is (= "Bezalkoholna pića" (:subcategory (:decision (check {:subcategory "bezalkoholna pića"}))))))
    (testing "new subcategory"
      (let [{:keys [decision problems]} (check {:subcategory "Energetska pića"})]
        (is (true? (:subcategory-is-new decision)))
        (is (empty? problems)))
      (let [r (v/validate-llm-item (assoc-in ctx [:cfg :allow-new-subcategories?] false) group
                (item {:subcategory "Energetska pića"}))]
        (is (some #{:new-subcategory-not-allowed} (:problems r))))))
  (testing "bad name"
    (is (some #{:bad-name} (:problems (check {:canonical_name "ab"}))))
    (is (some #{:bad-name} (:problems (check {:canonical_name nil})))))
  (testing "confidence is clamped and thresholded"
    (is (= 100 (:confidence (:decision (check {:confidence 250})))))
    (is (= 0 (:confidence (:decision (check {:confidence -5})))))
    (is (= 0 (:confidence (:decision (check {:confidence "high"})))))
    (is (some #{:low-confidence} (:problems (check {:confidence 79}))))
    (is (empty? (:problems (check {:confidence 80}))))))

(deftest clean-canonical-name-test
  (is (= "Mlijeko meggle 1l" (v/clean-canonical-name "mlijeko meggle 1l 6f93")))
  (is (= "Meggle mlijeko" (v/clean-canonical-name "Meggle   mlijeko")) "collapses spaces")
  (is (= "Kafa cafe" (v/clean-canonical-name "Kafa cafe")) "pure-letter hex-like words are real words")
  (is (= "Paracetamol 500" (v/clean-canonical-name "Paracetamol 500")) "pure-digit sizes are not OCR hex")
  (is (= "Lavazza Crema e Gusto 1000" (v/clean-canonical-name "Lavazza Crema e Gusto 1000")))
  (is (= "Sok" (v/clean-canonical-name "Sok a73")) "mixed hex tokens are stripped")
  (is (nil? (v/clean-canonical-name "ab")))
  (is (nil? (v/clean-canonical-name nil))))

(deftest normalize-manufacturer-test
  (let [t {:brand-parent-mappings {"Milka" "Mondelez International"}}]
    (is (= "Mondelez International" (v/normalize-manufacturer t "MILKA")))
    (is (nil? (v/normalize-manufacturer t "INTENSO")))
    (is (= "Meggle" (v/normalize-manufacturer t "Meggle")))
    (is (nil? (v/normalize-manufacturer t "KOT")))
    (is (nil? (v/normalize-manufacturer t nil)))))

(deftest near-duplicates-test
  (is (= 1 (count (v/detect-near-duplicates [{:canonical-name "Meggle mlijeko 1L"}
                                              {:canonical-name "Meggle mlijeko 2L"}
                                              {:canonical-name "Sir"}])))))

(deftest short-brands-survive-sanitizing-test
  (is (= "dm" (v/sanitize-manufacturer "dm")))
  (is (= "dm" (v/normalize-manufacturer {:brand-parent-mappings {"Mivolis" "dm"}} "Mivolis")))
  (is (= "BAT" (v/sanitize-manufacturer "BAT")))
  (is (nil? (v/sanitize-manufacturer "ab"))))
