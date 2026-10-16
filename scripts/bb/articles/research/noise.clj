(ns articles.research.noise
  "OCR noise detection heuristics for raw receipt labels.
  Keep in sync with src/app/domain/backend/expenses/services/article_resolution/noise.clj (same rules)."
  (:require
    [clojure.string :as str]))
(def ^:private unit-words
  "Tokens that are units or quantity markers, never a product on their own."
  #{"kom" "kg" "g" "gr" "l" "ml" "x" "km" "kn" "km." "kom." "pak" "kut"})

(defn- tokens [s] (remove str/blank? (str/split s #"[\s/]+")))

(defn- letters [s] (count (re-seq #"\p{L}" s)))

(defn- word? [t] (>= (letters t) 3))

(defn- mixed-hex?
  "OCR/store code such as \"172e\", \"3a26\", \"577f\": 3-4 hex chars with both a digit and a letter a-f."
  [t]
  (let [t (str/lower-case t)]
    (boolean (and (re-matches #"[0-9a-f]{3,4}" t) (re-find #"\d" t) (re-find #"[a-f]" t)))))

(defn ocr-noise-reason
  "Heuristic OCR-noise detector. Takes a map with snake_case keys
  {:raw_label :raw_label_normalized}. Returns a reason string or nil."
  [{:keys [raw_label raw_label_normalized]}]
  (let [raw (or raw_label "")
        norm (or raw_label_normalized "")
        trimmed (str/trim raw)
        trimmed-norm (str/trim norm)
        alnum (count (re-seq #"[\p{L}\d]" trimmed))
        toks (tokens trimmed)
        words (filter word? toks)]
    (cond
      (str/blank? trimmed)       "blank"
      (str/blank? trimmed-norm)  "blank-normalized"
      (re-matches #"(?i)^(x+|\*+|\-+|\.+|,+|_+)$" trimmed) "punctuation-only"
      (re-matches #"^[\d\s.,:;x×*+\-=%/]+$" trimmed) "digits-only"
      (< alnum 3) "too-few-alnum"
      ;; "na" is a common Bosnian preposition ("on/for") - only flag "unknown" and "n/a"
      (re-find #"(?i)^(unknown|n/?a)$" trimmed) "placeholder"
      ;; No token with 3+ letters: "MG 1 A 3a26", "A 30 3b52", "X 10 3470", "KO"
      (empty? words) "no-word"
      ;; Only unit words besides numbers: "kom 1", "KG 0,5"
      (every? #(unit-words (str/lower-case %)) words) "unit-only"
      ;; One short word next to a store/OCR hex code: "0 SAP 172e", "ČVRST 17e0", "SNALIJ 577f"
      (and (= 1 (count words))
        (<= (letters (first words)) 6)
        (some mixed-hex? toks))
      "word-with-code"
      ;; Hex suffix pattern - OCR artifacts like "6f93", "4f92", "2b3c".
      ;; Only flag when the hex IS the label or the label is very short with hex.
      (and (<= alnum 12)
        (re-find #"\b[0-9a-f]{4}\b" (str/lower-case trimmed))
        ;; Don't flag real labels that happen to contain 4-char hex-like sequences
        (or (re-find #"\b\d+[a-f][0-9a-f]{2,3}\b" (str/lower-case trimmed))
          (< alnum 6)))
      "hex-ocr-artifact"
      :else nil)))

