(ns articles.research.heuristics
  "Local heuristic resolution: taxonomy loading, brand rules, supplier hints,
  keyword matching, and canonical name building."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str])
  (:import
    [java.text Normalizer Normalizer$Form]))

;; ============================================================
;; Taxonomy file I/O
;; ============================================================

(def ^:private taxonomy-dir "resources/articles/taxonomy")

(defn load-taxonomy
  "Load an EDN taxonomy file from the taxonomy directory. Returns nil if missing."
  [filename]
  (let [f (io/file taxonomy-dir filename)]
    (when (.exists f)
      (edn/read-string (slurp f)))))

(defn save-taxonomy!
  "Write an EDN value back to a taxonomy file."
  [filename data]
  (spit (io/file taxonomy-dir filename)
    (with-out-str (pprint/pprint data))))

(defn strip-diacritics [s]
  (-> (Normalizer/normalize (str s) Normalizer$Form/NFD)
    (str/replace #"\p{InCombiningDiacriticalMarks}+" "")))

;; ============================================================
;; Supplier hints
;; ============================================================

;; Supplier display_name patterns → default category
(def supplier-hints
  (or (load-taxonomy "supplier-hints.edn")
    [["(?i)apoteka|pharmacy|ljekarna"       "Zdravlje i apoteka"]
     ["(?i)mesnica|mesar"                    "Meso, morski plodovi i delikatesi"]
     ["(?i)pekara|bakery"                    "Pekara i deserti"]
     ["(?i)cevab|fast.?food|kafic|caffe|cafe|bistro|pizzeria|grill|slasticarna|restoran|konoba"
      "Pekara i deserti"]]))

;; ============================================================
;; Brand rules
;; ============================================================

;; Brand prefix → [manufacturer-name category-name subcategory-or-nil]
;; Loaded once, then sanitized at startup to prune stale/bad auto-learned rules.
(def ^:private brand-rules-raw
  (or (load-taxonomy "brand-rules.edn")
    [["(?i)^coca.?cola" "The Coca-Cola Company" "Pakovana hrana i pića" "Bezalkoholna pića"]]))

;; Common Bosnian product/category nouns that must NEVER become brand patterns.
;; These are generic first words in OCR labels (e.g. "sok", "keks", "suho").
;; Auto-learned rules matching these will be pruned on every run.
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
    ;; "pasteta" = generic for pâté. Already present: pecenica, pecenje on meat line.
    "pasteta"})

(defn generic-pattern?
  "Return true if a brand-rule pattern was auto-generated from a generic product word.
  Matches \\Q-quoted patterns whose inner word is in the blocklist.
  Checks both the original word and its diacritic-stripped form."
  [[pattern _ _ _]]
  (when-let [[_ word] (re-matches #"(?i)\(\?i\)\^\\Q(.+?)\\E\\b" pattern)]
    (let [lower (str/lower-case word)]
      (or (contains? generic-first-words lower)
        (contains? generic-first-words (strip-diacritics lower))))))

(defn bad-manufacturer?
  "Return true if the manufacturer in a brand-rule is a known non-brand artifact."
  [[_ mfr _ _]]
  (when mfr
    (let [lower (str/lower-case (str/trim mfr))]
      (or
        ;; Packaging/bulk terms mistaken for brands
        (re-find #"(?i)\brinfuz" lower)
        (re-find #"(?i)\bbrik\b" lower)
        ;; OCR artifact manufacturers
        (re-find #"(?i)^(sar|bas|kot|dj ass|g\.?a ex ra)$" lower)))))

(defn sanitize-brand-rules!
  "Prune auto-learned brand rules that use generic product words as patterns,
  or have OCR artifact manufacturers. Persists cleaned rules to brand-rules.edn.
  Returns [cleaned-rules removed-count]."
  [rules]
  (let [bad-rules (filterv #(or (generic-pattern? %) (bad-manufacturer? %)) rules)
        cleaned (vec (remove (set bad-rules) rules))]
    (when (seq bad-rules)
      (save-taxonomy! "brand-rules.edn" cleaned)
      (binding [*out* *err*]
        (println "  Brand rules sanitized:" (count bad-rules) "bad rules removed")))
    [cleaned (count bad-rules)]))

(def brand-rules
  (first (sanitize-brand-rules! brand-rules-raw)))

;; ============================================================
;; Keyword category hints
;; ============================================================

;; Product keyword → [category subcategory]
(def ^:private keyword-category-hints
  (or (load-taxonomy "keyword-category-hints.edn")
    {"mlijeko" ["Mliječni proizvodi i jaja" "Mlijeko"]
     "kafa"    ["Pakovana hrana i pića"     "Kafa"]}))

;; Bosnian meat-related keywords
(def ^:private meat-words
  (or (load-taxonomy "meat-words.edn")
    #{"juneci" "teleci" "pileci" "govedina" "sunka" "salama"}))

;; ============================================================
;; Matching functions
;; ============================================================

(defn- label-words-ascii
  "Extract lowercase ASCII words from a raw label (diacritics stripped)."
  [raw-label]
  (when raw-label
    (->> (str/split (-> raw-label str/lower-case strip-diacritics) #"[^a-z]+")
      (remove str/blank?)
      set)))

(defn- match-supplier-hint [supplier-name]
  (when supplier-name
    (some (fn [[pattern category]]
            (when (re-find (re-pattern pattern) supplier-name)
              {:category category :source :supplier-hint}))
      supplier-hints)))

(defn- match-brand [raw-label]
  (when raw-label
    (some (fn [[pattern mfr cat subcat]]
            (when (re-find (re-pattern pattern) raw-label)
              (cond-> {:category cat :source :brand-rule}
                mfr    (assoc :manufacturer mfr)
                subcat (assoc :subcategory subcat))))
      brand-rules)))

(defn- match-keywords [raw-label]
  (let [words (label-words-ascii raw-label)]
    (when words
      (let [coffee-context? (some #{"barista" "intenso" "espresso" "dolce" "lungo" "kafa" "nescafe"} words)]
        ;; Check meat words first
        (if (some meat-words words)
          {:category "Meso, morski plodovi i delikatesi" :subcategory "Svježe meso" :source :keyword-meat}
          ;; Then check keyword hints, with kapsule context-awareness
          (some (fn [w]
                  (when-let [[cat subcat] (get keyword-category-hints w)]
                    ;; "kapsule" in coffee context → Kafa, not pharmacy
                    (if (and (= w "kapsule") coffee-context?)
                      {:category "Pakovana hrana i pića" :subcategory "Kafa" :source :keyword-hint}
                      {:category cat :subcategory subcat :source :keyword-hint})))
            words))))))

;; ============================================================
;; Canonical name building
;; ============================================================

(defn build-canonical-name
  "Build a reasonable canonical name from a raw OCR label.
  Title-cases words, preserves size/weight tokens as uppercase."
  [raw-label]
  (-> raw-label
    str/trim
      ;; Remove trailing OCR artifact hex codes (3-4 chars, e.g. "6f93", "a73")
    (str/replace #"\s+[0-9a-f]{3,4}$" "")
      ;; Normalize spaces
    (str/replace #"\s+" " ")
    str/trim
      ;; Title-case
    (#(str/join " "
        (map (fn [w]
               (cond
                   ;; Size/weight tokens stay uppercase
                 (re-matches #"(?i)\d+[\.,]?\d*(g|gr|kg|ml|l|cl|dl|mg|kom|tbl)?" w) (str/upper-case w)
                   ;; Otherwise title-case
                 (not (str/blank? w))
                 (str (str/upper-case (subs w 0 1))
                   (when (> (count w) 1) (str/lower-case (subs w 1))))
                 :else w))
          (str/split % #"\s+"))))))

;; ============================================================
;; Local resolution
;; ============================================================

(defn resolve-locally
  "Try to resolve an alias group using local heuristics.
  Returns a resolution map or nil if unresolvable."
  [raw-label supplier-name]
  (let [brand-match   (match-brand raw-label)
        supplier-hint (match-supplier-hint supplier-name)
        keyword-match (match-keywords raw-label)
        ;; Priority: brand > keyword > supplier
        category    (or (:category brand-match)
                      (:category keyword-match)
                      (:category supplier-hint))
        subcategory (or (:subcategory brand-match)
                      (:subcategory keyword-match))
        manufacturer (:manufacturer brand-match)
        source       (or (:source brand-match)
                       (:source keyword-match)
                       (:source supplier-hint))]
    (when category
      {:canonical-name  (build-canonical-name raw-label)
       :manufacturer-name manufacturer
       :category-name   category
       :subcategory-name (or subcategory "Opste")
       :confidence       (if brand-match :high :medium)
       :resolution       :local
       :resolution-source source})))
