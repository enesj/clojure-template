(ns app.domain.backend.expenses.services.article-resolution.verify
  "Second opinion from Jev (TypeSafe System One) on a Claude proposal. Pure: builds the
  questions and turns the typed answers into item changes and problems. Off by default
  (`:verifier {:enabled? false}` in :article-resolver).

  Questions (one request per group; they run in parallel and do not see each other):
  - :category        Choice over DB categories + \"none\" - fills a null category and flags disagreement
  - :same-product    Noul - is the proposed canonical name the product the label describes?
  - :manufacturer-ok Noul - only asked when Claude named a manufacturer")

(def none-option "none")

(defn state-for
  "State sent to Jev: the receipt line, its context and Claude's proposal."
  [group item]
  {:label (:raw-label group)
   :unit (:unit group)
   :stores (vec (:stores group))
   :hint (some-> (:hint group) (select-keys [:category :subcategory :manufacturer]))
   :proposal {:canonical_name (:canonical_name item)
              :category (:category item)
              :subcategory (:subcategory item)
              :manufacturer (:manufacturer item)}})

(declare full-questions)

(defn needs-verification?
  "In :fill-category mode only items whose category is missing or not a DB category are verified;
  in :full mode every article item is."
  [{:keys [mode]} category-index item]
  (and (= "article" (:kind item))
    (or (not= :fill-category mode)
      (not (contains? category-index (:category item))))))

(defn questions
  "Typed questions for one group. `category-index` supplies the Choice options (sorted).
  With opts {:mode :fill-category} only the category question is asked."
  ([category-index item] (questions category-index item nil))
  ([category-index item {:keys [mode]}]
   (cond-> (full-questions category-index item)
     (= :fill-category mode) (select-keys [:category]))))

(defn- full-questions
  [category-index item]
  (cond->
    {:category
     {:type "choice"
      :instructions (str "Which shopping category does the product on receipt line `label` belong to? "
                      "`hint` is a guess from keyword rules and `proposal` a guess from another model; either may be wrong.")
      :criteria (into (sorted-map none-option "The line is not a product, or no listed category fits it")
                  (map (fn [[n {:keys [subcategories]}]]
                         [n (if (seq subcategories)
                              (str "Includes: " (clojure.string/join ", " (sort (map :name subcategories))))
                              nil)]))
                  category-index)}

     :same-product
     {:type "noul"
      :instructions "Is `proposal.canonical_name` the same product (same brand, variant and size) as the Bosnian receipt line `label`?"
      :criteria {:true "Same product; only spelling, capitalization or OCR noise differ"
                 :false "A different product, brand, variant or size, or the name invents details not in the label"}}}

    (:manufacturer item)
    (assoc :manufacturer-ok
      {:type "noul"
       :instructions "Is `proposal.manufacturer` the company or brand that makes the product on receipt line `label`?"
       :criteria {:true "The manufacturer or its well-known parent company"
                  :false "A product word, descriptor, store, or an unrelated company"}})))

(defn apply-answers
  "Merge Jev's answers into a Claude item. Returns {:item item' :problems [kw] :summary map}.
  - null/invalid category + confident Jev category -> category filled (item re-validated by the caller)
  - Claude's category differs from a confident Jev category -> :category-disagreement
  - same-product below threshold -> :name-mismatch
  - manufacturer-ok below threshold -> manufacturer dropped (never blocks)"
  [{:keys [min-category-confidence min-same-product min-manufacturer]
    :or {min-category-confidence 0.7 min-same-product 0.5 min-manufacturer 0.5}}
   category-index item answers]
  (let [{cat-choice :choice cat-conf :confidence} (:category answers)
        jev-cat      (when (and cat-choice (not= none-option cat-choice) (contains? category-index cat-choice)) cat-choice)
        confident?   (and jev-cat (>= (or cat-conf 0) min-category-confidence))
        claude-cat   (when (contains? category-index (:category item)) (:category item))
        same         (get-in answers [:same-product :noul])
        mfr          (get-in answers [:manufacturer-ok :noul])
        item'        (cond-> item
                       (and confident? (nil? claude-cat)) (assoc :category jev-cat)
                       (and (:manufacturer item) mfr (< mfr min-manufacturer)) (assoc :manufacturer nil))
        problems     (cond-> []
                       (and confident? claude-cat (not= claude-cat jev-cat)) (conj :category-disagreement)
                       (and same (< same min-same-product)) (conj :name-mismatch))]
    {:item item'
     :problems problems
     :summary {:category cat-choice :category_confidence cat-conf
               :category_filled (boolean (and confident? (nil? claude-cat)))
               :same_product same :manufacturer_ok mfr
               :manufacturer_dropped (boolean (and (:manufacturer item) (nil? (:manufacturer item'))))}}))
