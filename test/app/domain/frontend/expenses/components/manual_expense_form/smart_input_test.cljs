(ns app.domain.frontend.expenses.components.manual-expense-form.smart-input-test
  (:require
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.components :as smart-input-components]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.constants :as smart-input-constants]
    [app.domain.frontend.expenses.components.manual-expense-form.smart-input.helpers :as smart-input]
    [cljs.test :refer [deftest is testing]]
    [clojure.string :as str]))

(deftest focused-search-types-drop-selected-context-types
  (testing "selected context types are removed from the search focus, while article remains"
    (is (= [:supplier :store :category :article]
          (smart-input/focused-search-types {} false)))
    (is (= [:store :category :article]
          (smart-input/focused-search-types {:supplier {:id "sup-1"}} false)))
    (is (= [:category :article]
          (smart-input/focused-search-types {:supplier {:id "sup-1"}
                                             :store {:id "store-1"}} false)))
    (is (= [:article]
          (smart-input/focused-search-types {:supplier {:id "sup-1"}
                                             :store {:id "store-1"}
                                             :category {:id "cat-1"}} false)))))

(deftest items-phase-quick-pick-types-keep-category-visible-from-blank-state
  (testing "blank initial state keeps category visible while preserving article quick picks"
    (is (= [:category :article]
          (smart-input/items-phase-quick-pick-types
            [:supplier :store :category :article]
            {}
            false))))
  (testing "narrowed state keeps all remaining context types visible"
    (is (= [:store :category :article]
          (smart-input/items-phase-quick-pick-types
            [:store :category :article]
            {:supplier {:id "sup-1"}}
            false))))
  (testing "article mode suppresses context quick picks"
    (is (= []
          (smart-input/items-phase-quick-pick-types
            [:category :article]
            {}
            true)))))

(deftest items-phase-quick-pick-layout-pins-category-near-the-chip-slot
  (let [groups [{:entity-type :store :items [{:id "store-1"}]}
                {:entity-type :category :items [{:id "cat-1"}]}
                {:entity-type :article :items [{:id "article-1"}]}]]
    (testing "missing category is pulled into the top slot"
      (let [{:keys [top-groups inline-groups]}
            (smart-input/items-phase-quick-pick-layout groups {:supplier {:id "sup-1"}})]
        (is (= [:category] (mapv :entity-type top-groups)))
        (is (= [:store :article] (mapv :entity-type inline-groups)))))
    (testing "selected category leaves all groups inline"
      (let [{:keys [top-groups inline-groups]}
            (smart-input/items-phase-quick-pick-layout groups {:category {:id "cat-1"}})]
        (is (empty? top-groups))
        (is (= [:store :category :article] (mapv :entity-type inline-groups)))))))

(deftest phase-two-missing-context-types-keep-category-available-after-dismiss
  (testing "defaults sub-stage re-offers category and optional expense context until selected"
    (is (= [:category :expense-context]
          (smart-input/phase-two-missing-context-types {} :defaults)))
    (is (= [:expense-context]
          (smart-input/phase-two-missing-context-types
            {:category {:id "cat-1"}}
            :defaults)))
    (is (= []
          (smart-input/phase-two-missing-context-types
            {:category {:id "cat-1"}
             :expense-context {:id "ctx-1"}}
            :defaults))))
  (testing "store-search keeps category and expense context alongside supplier/store gaps"
    (is (= [:store :category :expense-context]
          (smart-input/phase-two-missing-context-types
            {:supplier {:id "sup-1"}}
            :store-search)))
    (is (= [:category :expense-context]
          (smart-input/phase-two-missing-context-types
            {:supplier {:id "sup-1"}
             :store {:id "store-1"}}
            :store-search)))))

(deftest context-phase-initial-sub-stage-prefers-explicit-entry
  (testing "an explicit requested sub-stage wins even before context exists"
    (is (= :store-search
          (smart-input/context-phase-initial-sub-stage {} :store-search))))
  (testing "existing supplier/store context still defaults into store-search"
    (is (= :store-search
          (smart-input/context-phase-initial-sub-stage
            {:supplier {:id "sup-1"}}
            nil)))
    (is (= :store-search
          (smart-input/context-phase-initial-sub-stage
            {:store {:id "store-1"}}
            nil))))
  (testing "plain context review still starts in defaults"
    (is (= :defaults
          (smart-input/context-phase-initial-sub-stage {} nil)))))

(deftest search-placeholder-reflects-remaining-search-targets
  (let [t (fn [k & args]
            (case k
              :smart-expense/article-mode-ph "Search article..."
              :smart-expense/search-prefix "Search "
              :smart-expense/start-with-prefix "Start with "
              :smart-expense/or-connector " or "
              :smart-expense/entity-supplier "supplier"
              :smart-expense/entity-store "store"
              :smart-expense/entity-category "category"
              :smart-expense/entity-article "article"
              (first args)))]
    (testing "placeholder text stops mentioning already selected context types"
      (is (= "Start with supplier, store, category or article..."
            (smart-input/search-placeholder t {} false false)))
      (is (= "Search store, category or article..."
            (smart-input/search-placeholder t {:supplier {:id "sup-1"}} true false)))
      (is (= "Search category or article..."
            (smart-input/search-placeholder t {:supplier {:id "sup-1"}
                                               :store {:id "store-1"}} true false)))
      (is (= "Search article..."
            (smart-input/search-placeholder t {:supplier {:id "sup-1"}
                                               :store {:id "store-1"}
                                               :category {:id "cat-1"}} true false))))))

(deftest current-related-context-prefers-store-over-supplier
  (testing "focused quick picks use store-related data when a store is already selected"
    (is (= {:entity-type :supplier :entity-id "sup-1"}
          (smart-input/current-related-context {:supplier {:id "sup-1"}})))
    (is (= {:entity-type :store :entity-id "store-1"}
          (smart-input/current-related-context {:supplier {:id "sup-1"}
                                                :store {:id "store-1"}})))))

(deftest supplier-color-map-links-supplier-and-store-quick-picks
  (let [palette [{:supplier "supplier-a" :store "store-a"}
                 {:supplier "supplier-b" :store "store-b"}]]
    (testing "valid suppliers get stable first-seen palette slots"
      (is (= {"sup-1" {:supplier "supplier-a" :store "store-a"}
              "sup-2" {:supplier "supplier-b" :store "store-b"}}
            (smart-input/build-supplier-color-map
              [{:id "sup-1"}
               {:id nil}
               {:id "sup-2"}
               {:id "sup-1"}]
              palette))))

    (testing "supplier and store quick-picks share the same hue, while unrelated types stay untouched"
      (let [supplier-color-map (smart-input/build-supplier-color-map
                                 [{:id "sup-1"}
                                  {:id "sup-2"}]
                                 palette)
            groups [{:entity-type :supplier
                     :items [{:id "sup-1" :label "Bingo"}
                             {:id "sup-2" :label "Konzum"}]}
                    {:entity-type :store
                     :items [{:id "store-1" :label "PJ 91" :entity {:supplier_id "sup-1"}}
                             {:id "store-2" :label "Branch 47" :supplier_id "sup-2"}
                             {:id "store-3" :label "Unknown"}]}
                    {:entity-type :category
                     :items [{:id "cat-1" :label "Household"}]}]
            colorized (smart-input/colorize-quick-pick-groups groups supplier-color-map)]
        (is (= ["supplier-a" "supplier-b"]
              (mapv :chip-class (get-in colorized [0 :items]))))
        (is (= ["store-a" "store-b" nil]
              (mapv :chip-class (get-in colorized [1 :items]))))
        (is (= [nil]
              (mapv :chip-class (get-in colorized [2 :items]))))))))

(deftest visible-quick-pick-color-map-prioritizes-displayed-suppliers
  (let [palette [{:supplier "supplier-a" :store "store-a"}
                 {:supplier "supplier-b" :store "store-b"}
                 {:supplier "supplier-c" :store "store-c"}]
        groups [{:entity-type :supplier
                 :items [{:id "sup-9" :label "Visible Nine"}
                         {:id "sup-3" :label "Visible Three"}]}
                {:entity-type :store
                 :items [{:id "store-7" :label "Branch Seven" :entity {:supplier_id "sup-7"}}]}]
        supplier-color-map (smart-input/build-quick-pick-supplier-color-map groups palette)
        colorized (smart-input/colorize-quick-pick-groups groups supplier-color-map)]
    (testing "displayed suppliers get the first distinct palette slots regardless of hidden global ordering"
      (is (= {"sup-9" {:supplier "supplier-a" :store "store-a"}
              "sup-3" {:supplier "supplier-b" :store "store-b"}
              "sup-7" {:supplier "supplier-c" :store "store-c"}}
            supplier-color-map)))
    (testing "visible supplier and store chips receive the visible-order palette classes"
      (is (= ["supplier-a" "supplier-b"]
            (mapv :chip-class (get-in colorized [0 :items]))))
      (is (= ["store-c"]
            (mapv :chip-class (get-in colorized [1 :items])))))))

(deftest default-category-chip-to-preselect-runs-only-while-enabled
  (let [expense-categories [{:id "cat-1" :name "Kućanstvo" :is-default true}
                            {:id "cat-2" :name "Hrana"}]]
    (testing "returns a normalized default chip before the user interacts"
      (is (= {:id "cat-1" :label "Kućanstvo"}
            (smart-input/default-category-chip-to-preselect expense-categories {} true nil))))
    (testing "does not re-preselect after the chip was manually dismissed"
      (is (nil? (smart-input/default-category-chip-to-preselect expense-categories {} false nil))))
    (testing "does not override an already selected category"
      (is (nil? (smart-input/default-category-chip-to-preselect
                  expense-categories
                  {}
                  true
                  {:id "cat-2" :label "Hrana"}))))))

(deftest build-quick-pick-groups-uses-top-10-for-single-missing-type
  (testing "a single remaining entity type shows up to 10 local candidates"
    (let [stores (mapv (fn [n]
                         {:id (str "store-" n)
                          :display-name (str "Store " n)
                          :supplier_id "sup-1"})
                   (range 12))
          groups (smart-input-components/build-quick-pick-groups
                   [:store]
                   []
                   stores
                   []
                   []
                   []
                   "sup-1")]
      (is (= 1 (count groups)))
      (is (= :store (:entity-type (first groups))))
      (is (= 10 (count (:items (first groups))))))))

(deftest build-quick-pick-groups-uses-top-5-per-type-and-filters-stores-by-selected-supplier
  (testing "multiple remaining entity types show 5 each and store picks respect selected supplier"
    (let [stores [{:id "store-1" :display-name "Bingo One" :supplier_id "sup-1"}
                  {:id "store-2" :display-name "Bingo Two" :supplier_id "sup-1"}
                  {:id "store-3" :display-name "Other Supplier" :supplier_id "sup-2"}]
          categories (mapv (fn [n]
                             {:id (str "cat-" n)
                              :name (str "Category " n)})
                       (range 7))
          articles (mapv (fn [n]
                           {:id (str "article-" n)
                            :canonical-name (str "Article " n)})
                     (range 7))
          groups (smart-input-components/build-quick-pick-groups
                   [:store :category :article]
                   []
                   stores
                   categories
                   []
                   articles
                   "sup-1")]
      (is (= [:store :category :article] (mapv :entity-type groups)))
      (is (= ["store-1" "store-2"] (mapv :id (get-in groups [0 :items]))))
      (is (= 5 (count (get-in groups [1 :items]))))
      (is (= 5 (count (get-in groups [2 :items])))))))

(deftest build-quick-pick-groups-can-surface-expense-contexts
  (testing "expense contexts participate in local quick picks when requested"
    (let [expense-contexts [{:id "ctx-1" :name "Utilities"}
                            {:id "ctx-2" :name "Subscriptions"}]
          groups (smart-input-components/build-quick-pick-groups
                   [:expense-context]
                   []
                   []
                   []
                   expense-contexts
                   []
                   nil)]
      (is (= [:expense-context] (mapv :entity-type groups)))
      (is (= ["ctx-1" "ctx-2"] (mapv :id (get-in groups [0 :items])))))))

(deftest phase-two-quick-pick-groups-falls-back-per-missing-type
  (testing "history suggestions apply per entity type so categories and expense contexts still fall back to local picks"
    (let [context-suggestions {:suppliers [{:id "sup-9"
                                            :label "Suggested Supplier"}]
                               :stores []
                               :categories []
                               :expense-contexts []}
          categories (mapv (fn [n]
                             {:id (str "cat-" n)
                              :name (str "Category " n)})
                       (range 7))
          expense-contexts [{:id "ctx-1" :name "Utilities"}
                            {:id "ctx-2" :name "Subscriptions"}]
          groups (smart-input-components/phase-two-quick-pick-groups
                   [:supplier :category :expense-context]
                   context-suggestions
                   []
                   []
                   categories
                   expense-contexts
                   []
                   nil)]
      (is (= [:supplier :category :expense-context] (mapv :entity-type groups)))
      (is (= ["sup-9"] (mapv :id (get-in groups [0 :items]))))
      (is (= 7 (count (get-in groups [1 :items])))
        "categories are not capped — all local categories appear")
      (is (= ["cat-0" "cat-1" "cat-2" "cat-3" "cat-4" "cat-5" "cat-6"]
            (mapv :id (get-in groups [1 :items])))))))

(deftest phase-two-quick-pick-groups-filters-store-suggestions-by-selected-supplier
  (testing "store suggestions from article history must respect the selected supplier"
    (let [context-suggestions {:suppliers []
                               :stores [{:id "store-bingo" :label "Bingo Marka" :supplier_id "sup-1"}
                                        {:id "store-petrol" :label "Petrol BH" :supplier_id "sup-2"}]
                               :categories []}
          groups (smart-input-components/phase-two-quick-pick-groups
                   [:store]
                   context-suggestions
                   []
                   []
                   []
                   []
                   []
                   "sup-1")]
      (is (= [:store] (mapv :entity-type groups)))
      (is (= ["store-bingo"] (mapv :id (get-in groups [0 :items]))))))
  (testing "when all store suggestions belong to other suppliers, fall back to local stores filtered by supplier"
    (let [context-suggestions {:suppliers []
                               :stores [{:id "store-petrol" :label "Petrol BH" :supplier_id "sup-2"}]
                               :categories []}
          local-stores [{:id "local-bingo-1" :display-name "Bingo PJ 91" :supplier_id "sup-1"}
                        {:id "local-petrol" :display-name "Petrol Local" :supplier_id "sup-2"}]
          groups (smart-input-components/phase-two-quick-pick-groups
                   [:store]
                   context-suggestions
                   []
                   local-stores
                   []
                   []
                   []
                   "sup-1")]
      (is (= [:store] (mapv :entity-type groups)))
      (is (= ["local-bingo-1"] (mapv :id (get-in groups [0 :items])))))))

(deftest phase-two-quick-pick-groups-merges-store-history-with-supplier-pool
  (testing "store quick-picks show history-ranked picks first, then fill from the supplier pool"
    (let [context-suggestions {:suppliers []
                               :stores [{:id "store-history" :label "Bingo PJ 91" :supplier_id "sup-1"}]
                               :categories []}
          local-stores [{:id "store-a" :display-name "Bingo Branch A" :supplier_id "sup-1"}
                        {:id "store-history" :display-name "Bingo PJ 91" :supplier_id "sup-1"}
                        {:id "store-b" :display-name "Bingo Branch B" :supplier_id "sup-1"}
                        {:id "store-other" :display-name "Petrol" :supplier_id "sup-2"}]
          groups (smart-input-components/phase-two-quick-pick-groups
                   [:store]
                   context-suggestions
                   []
                   local-stores
                   []
                   []
                   []
                   "sup-1")]
      (is (= [:store] (mapv :entity-type groups)))
      (is (= ["store-history" "store-a" "store-b"]
            (mapv :id (get-in groups [0 :items])))
        "history-ranked store appears first; remaining supplier stores fill the rest; non-supplier stores excluded")))
  (testing "merged store list is capped at the limit (10 when store is the only missing type)"
    (let [history (mapv (fn [n]
                          {:id (str "hist-" n)
                           :label (str "History " n)
                           :supplier_id "sup-1"})
                    (range 4))
          local (mapv (fn [n]
                        {:id (str "local-" n)
                         :display-name (str "Local " n)
                         :supplier_id "sup-1"})
                  (range 12))
          context-suggestions {:suppliers [] :stores history :categories []}
          groups (smart-input-components/phase-two-quick-pick-groups
                   [:store]
                   context-suggestions
                   []
                   local
                   []
                   []
                   []
                   "sup-1")
          ids (mapv :id (get-in groups [0 :items]))]
      (is (= 10 (count ids)))
      (is (= ["hist-0" "hist-1" "hist-2" "hist-3"] (subvec ids 0 4))
        "history items appear at the head")
      (is (= ["local-0" "local-1" "local-2" "local-3" "local-4" "local-5"] (subvec ids 4 10))
        "remaining slots are filled from the supplier-scoped local pool"))))

(deftest phase-two-quick-pick-groups-dedupes-visible-store-collisions-and-backfills-suppliers
  (let [context-suggestions {:suppliers [{:id "sup-bingo"
                                          :label "BINGO"}]
                             :stores [{:id "store-ap-1"
                                       :label "BRAĆE BEGIĆ br.4, 71000 Sarajevo"
                                       :supplier_id "sup-apo"
                                       :supplier_display_name "APOTEKE SARAJEVO"}
                                      {:id "store-ap-2"
                                       :label "BRAĆE BEGIĆ br.4, 71000 Sarajevo"
                                       :supplier_id "sup-apo"
                                       :supplier_display_name "APOTEKE SARAJEVO"}
                                      {:id "store-bk-1"
                                       :label "Kranjčevićeva-Tepebašina 1, 71000 Sarajevo"
                                       :supplier_id "sup-bk"
                                       :supplier_display_name "B&K"}]
                             :categories []}
        suppliers [{:id "sup-bingo" :display-name "BINGO"}
                   {:id "sup-apo" :display-name "APOTEKE SARAJEVO"}
                   {:id "sup-bk" :display-name "B&K"}
                   {:id "sup-konzum" :display-name "KONZUM"}]
        local-stores [{:id "store-konzum-1"
                       :display-name "MARŠALA TITA BR.1, 71000 Sarajevo"
                       :supplier_id "sup-konzum"}
                      {:id "store-bingo-1"
                       :display-name "SALKE LAGUMDŽIJE br.15, 71000 SARAJEVO"
                       :supplier_id "sup-bingo"}]
        groups (smart-input-components/phase-two-quick-pick-groups
                 [:supplier :store]
                 context-suggestions
                 suppliers
                 local-stores
                 []
                 []
                 []
                 nil)
        groups-by-type (into {} (map (juxt :entity-type identity) groups))
        supplier-items (get-in groups-by-type [:supplier :items])
        store-items (get-in groups-by-type [:store :items])]
    (testing "duplicate store records that collapse to the same visible address only render once"
      (is (= ["store-ap-1" "store-bk-1"]
            (mapv :id store-items)))
      (is (= 1
            (count (filter #(= "BRAĆE BEGIĆ br.4, 71000 Sarajevo" (:label %))
                     store-items)))
        "same supplier + same visible label should not show as duplicate chips")
      (is (not-any? #{"store-konzum-1" "store-bingo-1"}
            (mapv :id store-items))
        "when no supplier is selected, article-context store suggestions should not be padded with the general store pool"))
    (testing "supplier quick-picks backfill only the owners of the visible stores"
      (is (= ["sup-bingo" "sup-apo" "sup-bk"]
            (mapv :id supplier-items)))
      (is (= ["BINGO" "APOTEKE SARAJEVO" "B&K"]
            (mapv :label supplier-items))))))

(deftest supplier-color-palette-shares-border-language-within-slot
  (let [class-tokens (fn [klass]
                       (str/split klass #" "))
        border-style-token (fn [klass]
                             (some #{"border-solid" "border-dashed" "border-dotted" "border-double"}
                               (class-tokens klass)))
        background-family (fn [klass]
                            (some (fn [token]
                                    (second (re-matches #"bg-([a-z]+)-\d+" token)))
                              (class-tokens klass)))
        border-family (fn [klass]
                        (some (fn [token]
                                (second (re-matches #"border-([a-z]+)-\d+" token)))
                          (class-tokens klass)))
        supplier-classes (mapv :supplier smart-input-constants/supplier-color-palette)
        store-classes (mapv :store smart-input-constants/supplier-color-palette)]
    (testing "every supplier/store slot uses a solid border"
      (doseq [klass (concat supplier-classes store-classes)]
        (is (= "border-solid" (border-style-token klass))
          (str "expected a solid border in: " klass))))
    (testing "supplier chips are visually unique from one another"
      (is (= (count supplier-classes)
            (count (distinct supplier-classes)))
        "each supplier slot should have a unique full class signature")
      (is (= (count supplier-classes)
            (count (distinct (map background-family supplier-classes))))
        "each supplier slot should have a unique background family")
      (is (= (count supplier-classes)
            (count (distinct (map border-family supplier-classes))))
        "each supplier slot should have a unique border family"))
    (testing "supplier and store chips share the same border hue family per slot"
      (doseq [{:keys [supplier store]} smart-input-constants/supplier-color-palette]
        (is (= (border-family supplier)
              (border-family store))
          (str "expected matching border hues for slot: " supplier " | " store))))
    (testing "border accents intentionally differ from the fill hue"
      (doseq [klass (concat supplier-classes store-classes)]
        (is (some? (background-family klass))
          (str "expected background class in: " klass))
        (is (some? (border-family klass))
          (str "expected border color class in: " klass))
        (is (not= (background-family klass)
              (border-family klass))
          (str "expected contrasting border hue in: " klass))))))
