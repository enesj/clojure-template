(ns app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.text
  (:require
    [app.domain.backend.expenses.integrations.llamaparse.http :as http]
    [clojure.string :as str]))

(defn safe-trim
  [s]
  (some-> s str str/trim not-empty))

(defn normalize-text
  [s]
  (some-> s safe-trim str/lower-case (str/replace #"\s+" " ") str/trim not-empty))

(def ba-datetime-line-re
  #"(?iu)^(\d{1,2})\.(\d{1,2})\.(\d{2,4})\.?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?$")

(def ba-date-line-re
  #"(?iu)^(\d{1,2})\.(\d{1,2})\.(\d{2,4})\.?$")

(defn- normalize-year
  [year-str]
  (let [year-str (some-> year-str str/trim)]
    (cond
      (nil? year-str) nil
      (= 4 (count year-str)) year-str
      (= 3 (count year-str)) (str "2" year-str)
      (= 2 (count year-str)) (str "20" year-str)
      :else year-str)))

(defn text->date-line
  [text]
  (when (string? text)
    (->> (str/split-lines text)
      (map safe-trim)
      (remove nil?)
      (filter (fn [line]
                (or (re-matches ba-datetime-line-re line)
                  (re-matches ba-date-line-re line))))
      first)))

(defn date-line->iso
  [date-line]
  (when-let [date-line (safe-trim date-line)]
    (or
      (when-let [[_ day month year hour minute sec] (re-matches ba-datetime-line-re date-line)]
        (let [year (normalize-year year)
              sec (or sec "00")]
          (when (and year (re-matches #"\d{4}" year))
            (format "%s-%02d-%02dT%02d:%02d:%02d"
              year
              (parse-long month)
              (parse-long day)
              (parse-long hour)
              (parse-long minute)
              (parse-long sec)))))
      (when-let [[_ day month year] (re-matches ba-date-line-re date-line)]
        (let [year (normalize-year year)]
          (when (and year (re-matches #"\d{4}" year))
            (format "%s-%02d-%02d" year (parse-long month) (parse-long day))))))))

(defn response->pages
  [resp-json]
  (let [pages (get-in resp-json [:items :pages])]
    (if (sequential? pages) pages [])))

(defn- flatten-items
  [items]
  (letfn [(walk [item]
            (lazy-seq
              (cons item
                (when (sequential? (:items item))
                  (mapcat walk (:items item))))))]
    (->> (or items [])
      (mapcat walk)
      (remove nil?)
      vec)))

(defn response->all-items
  [resp-json]
  (->> (response->pages resp-json)
    (mapcat :items)
    flatten-items
    vec))

(def ^:private synthetic-merchant-field-line-re
  #"(?iu)^merchant\.(?:name|store[_ ]?name|address|raw[_ ]?address)\s*:")

(defn- synthetic-merchant-field-line?
  [line]
  (boolean
    (and (string? line)
      (re-find synthetic-merchant-field-line-re (str/trim line)))))

(defn- sanitize-header-item-text
  [s]
  (when-let [s (safe-trim s)]
    (->> (str/split-lines s)
      (remove synthetic-merchant-field-line?)
      (map safe-trim)
      (remove nil?)
      (str/join "\n")
      safe-trim)))

(defn response->header-text
  [resp-json]
  (let [items (response->all-items resp-json)
        pre-table-text-items
        (->> items
          (take-while (fn [{:keys [type]}]
                        (not= "table" (some-> type str/lower-case))))
          (filter (fn [{:keys [type]}]
                    (contains? #{"header" "heading" "text"} (some-> type str/lower-case)))))
        header-items
        (filter (fn [{:keys [type]}]
                  (= "header" (some-> type str/lower-case)))
          items)]
    (->> (concat pre-table-text-items header-items)
      (keep (fn [item]
              (sanitize-header-item-text
                (or (safe-trim (:value item))
                  (safe-trim (:md item))))))
      (remove str/blank?)
      distinct
      (str/join "\n\n")
      not-empty)))

(def ^:private heading-qty-line-re
  #"(?iu)^(?:#+\s*)?(\d[\d,\.]*\s*x\s+\d[\d,\.]*(?:\s+\d[\d,\.]*)?\s*[A-Z]?)$")

(defn- heading-qty-line
  "Return the quantity/price line of a heading block such as
  '## 1,000x 10,45 10,45E' (OCR sometimes marks item lines as headings)."
  [item]
  (when (= "heading" (some-> (:type item) str/lower-case))
    (some->> (or (safe-trim (:value item)) (safe-trim (:md item)))
      (re-matches heading-qty-line-re)
      second)))

(defn- response->structured-block-text
  "Join the text of structured blocks of `allowed-types`.

  Blocks are intentionally not de-duplicated: a receipt can list the same
  product on two identical rows, and dropping the repeat understates the items.
  With `include-heading-qty-lines?`, heading blocks that are really quantity
  lines are included with the markdown heading marker removed."
  ([resp-json allowed-types]
   (response->structured-block-text resp-json allowed-types false))
  ([resp-json allowed-types include-heading-qty-lines?]
   (->> (response->all-items resp-json)
     (keep (fn [{:keys [type] :as item}]
             (if (contains? allowed-types (some-> type str/lower-case))
               (sanitize-header-item-text
                 (or (safe-trim (:value item))
                   (safe-trim (:md item))))
               (when include-heading-qty-lines?
                 (heading-qty-line item)))))
     (remove str/blank?)
     (str/join "\n\n")
     not-empty)))

(defn response->structured-text
  [resp-json]
  (response->structured-block-text resp-json #{"text"} true))

(defn response->structured-code-text
  [resp-json]
  (response->structured-block-text resp-json #{"code"}))

(defn response->combined-text
  [resp-json]
  (let [header (response->header-text resp-json)
        text (http/response->text resp-json)]
    (->> [header text]
      (map safe-trim)
      (remove nil?)
      (str/join "\n\n")
      not-empty)))
