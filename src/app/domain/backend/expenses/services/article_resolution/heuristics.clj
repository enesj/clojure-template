(ns app.domain.backend.expenses.services.article-resolution.heuristics
  "Local heuristic hints for the article resolver (pure, read-only).
  Port of scripts/bb/articles/research/heuristics.clj without any file I/O: the
  taxonomy map is passed in (see `article-resolution.taxonomy/current`)."
  (:require
    [clojure.string :as str])
  (:import
    [java.text Normalizer Normalizer$Form]))

(defn strip-diacritics
  "NFD-decompose `s` and drop combining marks (Š -> S). Note: Đ/đ are not decomposable."
  [s]
  (-> (Normalizer/normalize (str s) Normalizer$Form/NFD)
    (str/replace #"\p{InCombiningDiacriticalMarks}+" "")))

;; Common Bosnian product/category nouns that must NEVER become brand patterns.
(def generic-first-words
  #{"sok" "keks" "bombonjera" "bombone" "bomboni" "bombon"
    "paradajz" "passata" "biber" "suho" "sjemenke" "sjemenka"
    "salata" "mlijeko" "jogurt" "sir" "meso" "riza" "riža"
    "brasno" "brašno" "secer" "šećer" "ulje" "voda" "caj" "čaj"
    "hljeb" "hleb" "pecivo" "pita" "somun" "supa" "juha"
    "tjestenina" "testenina" "kvasac" "pivo" "vino"
    "desert" "kolac" "kolač" "torta" "sendvič" "sendvic"
    "namaz" "maslac" "kajmak" "mileram" "vrhnje" "pavlaka"
    "krompir" "limun" "ananas" "narandza" "narandža" "jabuka"
    "kafa" "espresso" "superiore" "marcaffe"
    "pasta" "flasteri" "mivolis" "galas"
    "arf" "folija" "folja" "folije" "kesa" "vrecica" "vrećica"
    "vreća" "vreca" "toalet" "salveta" "salvete" "spužva" "spuzva"
    "upaljac" "upaljač" "odmacivac" "odmačivač"
    "cokoladne" "čokoladne" "voćar" "vocar"
    "kuhinjski" "mirisna" "ukrasni" "svijeca" "svijeća"
    "kikiriki" "flips" "krekeri" "krekere"
    "teleci" "teleći" "juneci" "juneći" "pileci" "pileći"
    "govedina" "sunka" "šunka" "pecenje" "pečenje" "pecenica" "pećenica"
    "pastrmka" "losos" "file" "filet"
    "cigare" "cigarete" "cig" "duhan"
    "aspirin" "vizol" "esenbak"
    "casa" "čaša" "toplomjer"
    "herbifit" "herbiko" "apipol"
    "micro" "premium" "poklon" "žele" "želée" "zele"
    "amisu" "tuborg" "violeta" "profissimo"
    "domaća" "domaca" "strani" "strange"
    "logilink"
    "pasteta"})

(defn generic-pattern?
  "True if a brand-rule `[pattern mfr cat subcat]` was auto-generated from a generic product word.
  Matches \\Q-quoted patterns whose inner word is in the blocklist (diacritics-insensitive)."
  [[pattern _ _ _]]
  (when-let [[_ word] (re-matches #"(?i)\(\?i\)\^\\Q(.+?)\\E\\b" (str pattern))]
    (let [lower (str/lower-case word)]
      (or (contains? generic-first-words lower)
        (contains? generic-first-words (strip-diacritics lower))))))

(defn bad-manufacturer?
  "True if the manufacturer in a brand-rule `[pattern mfr cat subcat]` is a known non-brand artifact."
  [[_ mfr _ _]]
  (when mfr
    (let [lower (str/lower-case (str/trim mfr))]
      (or
        (re-find #"(?i)\brinfuz" lower)
        (re-find #"(?i)\bbrik\b" lower)
        (re-find #"(?i)^(sar|bas|kot|dj ass|g\.?a ex ra)$" lower)))))

(defn bad-brand-rule?
  "True if a raw brand-rule vector must be ignored (generic pattern or artifact manufacturer)."
  [rule]
  (boolean (or (generic-pattern? rule) (bad-manufacturer? rule))))

(def ^:private hint-banned-subcategories
  "Lower-cased generic subcategory names a hint must not carry."
  #{"opste" "opšte" "ostalo" "razno" "general" "generic" "other"})

(defn label-words-ascii
  "Lowercase ASCII words of a raw label (diacritics stripped), as a set."
  [raw-label]
  (when raw-label
    (->> (str/split (-> raw-label str/lower-case strip-diacritics) #"[^a-z]+")
      (remove str/blank?)
      set)))

(defn match-supplier-hint
  "Supplier-name based category default, or nil."
  [{:keys [supplier-hints]} supplier-name]
  (when supplier-name
    (some (fn [{:keys [re category]}]
            (when (re-find re supplier-name)
              {:category category :source :supplier-hint}))
      supplier-hints)))

(defn match-brand
  "First matching brand rule for the label, or nil."
  [{:keys [brand-rules]} raw-label]
  (when raw-label
    (some (fn [{:keys [re manufacturer category subcategory]}]
            (when (re-find re raw-label)
              (cond-> {:category category :source :brand-rule}
                manufacturer (assoc :manufacturer manufacturer)
                subcategory  (assoc :subcategory subcategory))))
      brand-rules)))

(defn match-keywords
  "Meat words and keyword category hints, or nil."
  [{:keys [meat-words keyword-category-hints]} raw-label]
  (let [words (label-words-ascii raw-label)]
    (when words
      (let [coffee-context? (some #{"barista" "intenso" "espresso" "dolce" "lungo" "kafa" "nescafe"} words)]
        (if (some meat-words words)
          {:category "Meso, morski plodovi i delikatesi" :subcategory "Svježe meso" :source :keyword-meat}
          (some (fn [w]
                  (when-let [[cat subcat] (get keyword-category-hints w)]
                    ;; "kapsule" in coffee context -> Kafa, not pharmacy
                    (if (and (= w "kapsule") coffee-context?)
                      {:category "Pakovana hrana i pića" :subcategory "Kafa" :source :keyword-hint}
                      {:category cat :subcategory subcat :source :keyword-hint})))
            words))))))

(defn resolve-hint
  "Heuristic hint for Claude, or nil. Drops generic subcategories (\"Opste\" ...); returns nil when the
  hint's category is not in `category-names` (e.g. a stale category from brand-rules.edn).
  Priority matches bb resolve-locally: brand > keyword > supplier. The subcategory comes from the
  rule that supplied the category, so a hint never mixes categories."
  [taxonomy category-names raw-label supplier-name]
  (let [brand    (match-brand taxonomy raw-label)
        kw       (match-keywords taxonomy raw-label)
        supplier (match-supplier-hint taxonomy supplier-name)
        winner   (or (when (:category brand) brand)
                   (when (:category kw) kw)
                   (when (:category supplier) supplier))
        category (:category winner)
        subcat   (or (:subcategory winner)
                   ;; brand rule without subcategory may borrow the keyword one when both agree on category
                   (when (and (identical? winner brand) (= category (:category kw)))
                     (:subcategory kw)))
        subcat   (when (and (not (str/blank? subcat))
                         (not (hint-banned-subcategories (str/lower-case subcat))))
                   subcat)]
    (when (and category (contains? (set category-names) category))
      {:category     category
       :subcategory  subcat
       :manufacturer (:manufacturer brand)
       :source       (:source winner)})))
