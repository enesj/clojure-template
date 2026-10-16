(ns app.domain.backend.expenses.services.article-resolution.validation
  "Pure validation of Claude results and manufacturer/name cleanup for the article resolver.
  sanitize-manufacturer, normalize-for-dedup and detect-near-duplicates are ports of
  scripts/bb/articles/research/validation.clj."
  (:require
    [app.domain.backend.expenses.services.articles.normalization :as normalization]
    [clojure.string :as str]))

(def banned-subcategory-names
  "Lower-cased generic subcategory names the resolver must never create or accept."
  #{"opste" "opšte" "ostalo" "razno" "general" "generic" "other"})

(def descriptor-words
  "Product-line words that are not manufacturers."
  #{"intenso" "classic" "light" "plus" "soft" "blue" "forte" "espresso" "prženi" "przeni" "biofit" "rinfuza"})

(def ^:private short-brand-re
  "Known valid short brands. Checked before the length rules, which would otherwise drop
  \"dm\" (the parent of Balea, Mivolis, Denkmit ...). The bb original has the same ordering bug."
  #"(?i)^(BAT|INA|OMO|ACE|AXE|UHU|dm)$")

(defn sanitize-manufacturer
  "Reject OCR artifact manufacturer names. Returns cleaned name or nil."
  [mfr-name]
  (when mfr-name
    (let [trimmed (str/trim mfr-name)]
      (cond
        (str/blank? trimmed) nil
        (re-matches short-brand-re trimmed) trimmed
        (<= (count trimmed) 2) nil
        ;; Starts with / (label suffix fragment)
        (re-find #"^/" trimmed) nil
        ;; Ends with unit suffix like /KO, /KG, /KOM, /pc, /L
        (re-find #"(?i)/(ko|kg|kom|pc|l)$" trimmed) nil
        ;; All-caps <= 4 chars with no vowels (OCR fragment - e.g. PODR, HOC, KOT)
        (and (<= (count trimmed) 4)
          (= trimmed (str/upper-case trimmed))
          (not (re-find #"[AEIOU]" trimmed))) nil
        ;; All-caps 3-4 chars that are likely OCR fragments (known valid short brands excepted)
        (and (<= (count trimmed) 4)
          (= trimmed (str/upper-case trimmed))
          (not (re-find #"(?i)^(BAT|INA|OMO|ACE|AXE|UHU|dm)$" trimmed))) nil
        ;; Packaging/bulk terms mistaken for brand names
        (re-find #"(?i)\brinfuz" trimmed) nil
        (re-find #"(?i)^brik$" trimmed) nil
        ;; Known OCR artifact "manufacturers"
        (re-find #"(?i)^(SAR|BAS|KOT|DJ ASS|G\.?A EX RA)$" trimmed) nil
        :else (let [cleaned (str/trim (str/replace trimmed #"(?i)\s+rinfuz\w*$" ""))]
                (when-not (str/blank? cleaned) cleaned))))))

(defn normalize-for-dedup
  "Aggressively normalize a canonical name for duplicate detection. Strips size/weight, punctuation, lowercases."
  [name]
  (when name
    (-> name
      str/lower-case
      (str/replace #"\d+[\.,]?\d*\s*(g|gr|kg|ml|l|cl|dl|mg|kom|tbl)\b" "")
      (str/replace #"[^a-z0-9 ]+" " ")
      (str/replace #"\s+" " ")
      str/trim)))

(defn detect-near-duplicates
  "Find groups of articles with similar normalized names.
  Returns a vec of {:group-key :count :articles} for groups with 2+ members."
  [articles]
  (->> articles
    (group-by #(normalize-for-dedup (:canonical-name %)))
    (filter (fn [[_ items]] (>= (count items) 2)))
    (mapv (fn [[group-key items]]
            {:group-key group-key
             :count (count items)
             :articles (mapv :canonical-name items)}))
    (sort-by (comp - :count))))

(defn clean-canonical-name
  "Trim, collapse spaces, strip a trailing OCR hex token (3-4 hex chars with at least one digit AND one a-f letter, so sizes like \"500\" and words like \"cafe\" survive),
  capitalize the first char if the whole name is lowercase. Returns nil when shorter than 3 chars."
  [s]
  (when (string? s)
    (let [t (-> s
              str/trim
              (str/replace #"\s+" " ")
              (str/replace #"\s+(?=[0-9a-f]{3,4}$)(?=[0-9a-f]*\d)(?=[0-9a-f]*[a-f])[0-9a-f]{3,4}$" "")
              str/trim)
          t (if (and (seq t) (= t (str/lower-case t)))
              (str (str/upper-case (subs t 0 1)) (subs t 1))
              t)]
      (when (>= (count t) 3) t))))

(defn normalize-manufacturer
  "Map through brand-parent-mappings (case-insensitive key match), then sanitize-manufacturer,
  then nil if the lowercased value is a descriptor word."
  [taxonomy s]
  (when-not (str/blank? s)
    (let [lower   (str/lower-case (str/trim s))
          mapped  (or (some (fn [[brand parent]]
                              (when (= lower (str/lower-case brand)) parent))
                        (:brand-parent-mappings taxonomy))
                    s)
          cleaned (sanitize-manufacturer mapped)]
      (when (and cleaned (not (descriptor-words (str/lower-case cleaned))))
        cleaned))))

(defn- clamp-confidence
  [c]
  (if (number? c)
    (-> c long (max 0) (min 100))
    0))

(defn- match-existing-subcategory
  "Existing {:name ..} of the category index entry matching `sub` (exact, case-insensitive, catalog key)."
  [entry sub]
  (let [subs  (:subcategories entry)
        lower (str/lower-case sub)
        k     (normalization/normalize-catalog-key sub)]
    (or (some #(when (= sub (:name %)) %) subs)
      (some #(when (= lower (str/lower-case (:name %))) %) subs)
      (some #(when (and k (= k (normalization/normalize-catalog-key (:name %)))) %) subs))))

(defn validate-llm-item
  "Returns {:decision decision :problems [kw ...]} for one Claude result and its group.
  decision = {:action :create|:map-existing|:noise|:none ...cleaned fields...}.
  ctx = {:category-index .. :taxonomy .. :cfg ..}; group has :candidates [{:key :name}]."
  [{:keys [category-index taxonomy cfg]} group item]
  (let [kind        (:kind item)
        confidence  (clamp-confidence (:confidence item))
        existing    (let [k (:existing_article_key item)] (when-not (str/blank? k) k))
        cand-keys   (set (map :key (:candidates group)))
        base        {:kind kind :confidence confidence :reason (:reason item)}]
    (case kind
      "noise"
      {:decision (assoc base :action :noise)
       :problems (cond-> [] (< confidence (:noise-threshold cfg)) (conj :low-confidence))}

      "unknown"
      {:decision (assoc base :action :none) :problems [:unknown]}

      ;; "article" (and anything unexpected)
      (let [map-existing? (and existing (contains? cand-keys existing))
            category      (:category item)
            entry         (get category-index category)
            sub-raw       (:subcategory item)
            sub-blank?    (or (str/blank? sub-raw)
                            (contains? banned-subcategory-names (str/lower-case (str sub-raw))))
            sub-match     (when (and entry (not sub-blank?)) (match-existing-subcategory entry sub-raw))
            name*         (clean-canonical-name (:canonical_name item))
            mfr           (normalize-manufacturer taxonomy (:manufacturer item))
            create?       (not map-existing?)
            problems      (cond-> []
                            (and existing (not map-existing?)) (conj :unknown-existing-key)
                            (and create? (or (nil? category) (nil? entry))) (conj :unknown-category)
                            (and create? sub-blank?) (conj :missing-subcategory)
                            (and create? (not sub-blank?) entry (nil? sub-match)
                              (not (:allow-new-subcategories? cfg))) (conj :new-subcategory-not-allowed)
                            (and create? (nil? name*)) (conj :bad-name)
                            (< confidence (:auto-apply-threshold cfg)) (conj :low-confidence))]
        {:decision (assoc base
                     :action (if map-existing? :map-existing :create)
                     :canonical-name name*
                     :category category
                     :subcategory (cond sub-blank? nil sub-match (:name sub-match) :else (str/trim sub-raw))
                     :subcategory-is-new (boolean (and (not sub-blank?) entry (nil? sub-match)))
                     :manufacturer mfr
                     :manufacturer-raw (:manufacturer item)
                     :existing-article-key existing)
         :problems problems}))))
