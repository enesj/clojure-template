(ns app.domain.backend.expenses.services.article-resolution.prompt-test
  (:require
    [app.domain.backend.expenses.services.article-resolution.prompt :as prompt]
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [clojure.walk :as walk]))

(def ^:private entries
  [["Pakovana hrana i pića" {:subcategories [{:id 1 :name "Kafa"} {:id 2 :name "Čaj"}]}]
   ["Lična njega" {:subcategories []}]
   ["Mliječni proizvodi i jaja" {:subcategories [{:id 3 :name "Mlijeko"}]}]])

(def ^:private taxonomy-entries
  [["Milka" "Mondelez International"] ["Oreo" "Mondelez International"] ["Thomy" "Nestlé"]])

(defn- shuffled-taxonomy
  []
  {:brand-parent-mappings (into (array-map) (shuffle taxonomy-entries))
   :self-named-brands (shuffle ["Zbregov" "Cekin" "Haribo"])})

(deftest system-prompt-is-deterministic-test
  (let [prompts (set (for [_ (range 8)]
                       (prompt/system-prompt (into (array-map) (shuffle entries)) (shuffled-taxonomy))))]
    (is (= 1 (count prompts)) "identical for shuffled inputs")
    (let [p (first prompts)]
      (is (not (str/includes? p "{{")) "all placeholders rendered")
      (is (str/includes? p "- Lična njega: (none yet)"))
      (is (str/includes? p "- Pakovana hrana i pića: Kafa, Čaj"))
      (is (str/includes? p "- Mondelez International: Milka, Oreo"))
      (is (str/includes? p "Self-named brands: Cekin, Haribo, Zbregov"))
      (testing "categories not in the index never appear"
        (is (not (str/includes? p "Subscriptions")))
        (is (not (str/includes? p "Utilities")))))))

(deftest system-blocks-test
  (let [[stable dynamic :as blocks] (prompt/system-blocks (into (array-map) entries) (shuffled-taxonomy))]
    (is (= 2 (count blocks)))
    (testing "taxonomy only in the last block; the stable block does not depend on it"
      (is (str/includes? dynamic "- Pakovana hrana i pića: Kafa, Čaj"))
      (is (not (str/includes? stable "Kafa, Čaj")))
      (is (= stable (first (prompt/system-blocks {"Other" {:subcategories []}} (shuffled-taxonomy))))))
    (is (str/includes? stable "Self-named brands: Cekin, Haribo, Zbregov"))))

(deftest response-schema-test
  (let [index  (into (array-map) (shuffle entries))
        schema (prompt/response-schema index)
        objects (atom [])
        keys*   (atom #{})]
    (walk/postwalk (fn [x]
                     (when (map? x)
                       (swap! keys* into (keys x))
                       (when (= "object" (:type x)) (swap! objects conj x)))
                     x)
      schema)
    (is (seq @objects))
    (is (every? #(false? (:additionalProperties %)) @objects))
    (is (empty? (filter #{:minimum :maximum :minLength} @keys*)))
    (is (= (sort (map first entries))
          (get-in schema [:properties :items :items :properties :category :anyOf 0 :enum])))))

(deftest user-message-test
  (let [msg (json/parse-string
              (prompt/user-message [{:raw-label "MLIJEKO MEGGLE 1L" :unit "kom" :stores ["B" "A" "A"]
                                     :hint {:category "x" :subcategory nil :manufacturer nil}
                                     :candidates [{:key "k" :name "n" :score 0.9}]}
                                    {:raw-label "SIR" :unit "kg" :stores [] :hint nil :candidates []}])
              true)]
    (is (= ["g1" "g2"] (map :id msg)))
    (is (= ["A" "B"] (:stores (first msg))) "stores distinct + sorted")
    (is (= [{:key "k" :name "n"}] (:candidates (first msg))) "score is not sent")
    (is (nil? (:hint (second msg))))))

(deftest response-schema-id-enum-test
  (let [index  (into (array-map) entries)
        id-enum #(get-in % [:properties :items :items :properties :id :enum])]
    (is (nil? (id-enum (prompt/response-schema index))) "no enum without id-count")
    (is (= ["g1" "g2" "g3"] (id-enum (prompt/response-schema index 3))))
    (is (= 50 (count (id-enum (prompt/response-schema index 50)))))))
