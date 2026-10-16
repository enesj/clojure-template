(ns app.domain.backend.expenses.services.article-resolution.noise
  "OCR noise detection heuristics for raw receipt labels (pure).
  Port of scripts/bb/articles/research/noise.clj."
  (:require
    [clojure.string :as str]))

(defn ocr-noise-reason
  "Heuristic OCR-noise detector. Takes a map with snake_case keys
  {:raw_label :raw_label_normalized}. Returns a reason string or nil."
  [{:keys [raw_label raw_label_normalized]}]
  (let [raw (or raw_label "")
        norm (or raw_label_normalized "")
        trimmed (str/trim raw)
        trimmed-norm (str/trim norm)
        alnum (count (re-seq #"[A-Za-z0-9]" trimmed))]
    (cond
      (str/blank? trimmed)       "blank"
      (str/blank? trimmed-norm)  "blank-normalized"
      (re-matches #"(?i)^(x+|\*+|\-+|\.+|,+|_+)$" trimmed) "punctuation-only"
      (re-matches #"^\d+$" trimmed) "digits-only"
      (< alnum 3) "too-few-alnum"
      ;; "na" is a common Bosnian preposition ("on/for") - only flag "unknown" and "n/a"
      (re-find #"(?i)^(unknown|n/?a)$" trimmed) "placeholder"
      ;; Hex suffix pattern - OCR artifacts like "6f93", "4f92", "2b3c".
      ;; Only flag when the hex IS the label or the label is very short with hex.
      (and (<= alnum 12)
        (re-find #"\b[0-9a-f]{4}\b" (str/lower-case trimmed))
        ;; Don't flag real labels that happen to contain 4-char hex-like sequences
        (or (re-find #"\b\d+[a-f][0-9a-f]{2,3}\b" (str/lower-case trimmed))
          (< alnum 6)))
      "hex-ocr-artifact"
      :else nil)))
