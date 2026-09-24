(ns app.domain.backend.expenses.services.expenses.parsing
  (:require
    [app.domain.backend.expenses.services.articles :as articles]
    [app.domain.backend.expenses.services.exchange-rates :as exchange-rates]
    [clojure.string :as str]
    [taoensso.timbre :as log])
  (:import
    [java.math RoundingMode]
    [java.time Instant LocalDate LocalDateTime OffsetDateTime ZoneId ZoneOffset]
    [java.util Date UUID]))

(defn require-keys!
  [m ks]
  (doseq [k ks]
    (when-not (get m k)
      (throw (ex-info (str (name k) " is required")
               {:status 400
                :field k
                :data m})))))

(defn blank->nil
  [v]
  (cond
    (nil? v) nil
    (and (string? v) (str/blank? v)) nil
    :else v))

(defn parse-uuid!
  [field v]
  (let [v (blank->nil v)]
    (cond
      (nil? v) nil
      (instance? UUID v) v
      :else
      (try
        (UUID/fromString (str v))
        (catch IllegalArgumentException _
          (throw (ex-info (str "Invalid " (name field))
                   {:status 400
                    :field field
                    :value v})))))))

(defn parse-bigdec!
  [field v]
  (let [v (blank->nil v)]
    (cond
      (nil? v) nil
      (instance? java.math.BigDecimal v) v
      (number? v) (bigdec v)
      (string? v)
      (try
        (bigdec v)
        (catch Exception _
          (throw (ex-info (str "Invalid " (name field))
                   {:status 400
                    :field field
                    :value v}))))
      :else
      (throw (ex-info (str "Invalid " (name field))
               {:status 400
                :field field
                :value v})))))

(defn parse-instant!
  [field v]
  (let [v (blank->nil v)]
    (cond
      (nil? v) nil
      (instance? Instant v) v
      (instance? Date v) (.toInstant ^Date v)
      (number? v) (Instant/ofEpochMilli (long v))
      (string? v)
      (or
        (try
          (Instant/parse v)
          (catch Exception _ nil))
        (try
          (-> (OffsetDateTime/parse v) .toInstant)
          (catch Exception _ nil))
        (try
          (-> (LocalDateTime/parse v)
            (.atZone (ZoneId/systemDefault))
            .toInstant)
          (catch Exception _ nil))
        (try
          (-> (LocalDate/parse v) (.atStartOfDay ZoneOffset/UTC) .toInstant)
          (catch Exception _ nil))
        (throw (ex-info (str "Invalid " (name field))
                 {:status 400
                  :field field
                  :value v})))
      :else
      (throw (ex-info (str "Invalid " (name field))
               {:status 400
                :field field
                :value v})))))

(defn update-if-present
  [m k f]
  (if (contains? m k)
    (update m k f)
    m))

(defn derive-unit-price
  [qty line-total]
  (cond
    (nil? line-total) nil
    (nil? qty) line-total
    (pos? qty) (.divide ^java.math.BigDecimal line-total
                 ^java.math.BigDecimal qty
                 2
                 RoundingMode/HALF_UP)
    :else (do
            (log/warn "derive-unit-price: non-positive qty; unit price will be nil"
              {:qty qty :line-total line-total})
            nil)))

(defn inconsistent-item-unit-price?
  [{:keys [qty unit_price line_total]}]
  (let [expected (when (and qty unit_price)
                   (.multiply ^java.math.BigDecimal qty
                     ^java.math.BigDecimal unit_price))
        diff (when (and expected line_total)
               (.abs (.subtract ^java.math.BigDecimal expected
                       ^java.math.BigDecimal line_total)))]
    (and qty
      unit_price
      line_total
      (pos? (.signum ^java.math.BigDecimal qty))
      diff
      (pos? (.compareTo ^java.math.BigDecimal diff (bigdec "0.01"))))))

(defn normalize-expense-item
  [item]
  (let [item* (-> item
                (update-if-present :id #(parse-uuid! :id %))
                (update-if-present :alias_id #(parse-uuid! :alias_id %))
                (update-if-present :article_id #(parse-uuid! :article_id %))
                (update-if-present :raw_label #(some-> % str str/trim))
                (update-if-present :unit #(some-> % str str/trim str/lower-case blank->nil))
                (update-if-present :qty #(parse-bigdec! :qty %))
                (update-if-present :unit_price #(parse-bigdec! :unit_price %))
                (update-if-present :line_total #(parse-bigdec! :line_total %)))
        derived-unit-price (when (nil? (:unit_price item*))
                             (derive-unit-price (:qty item*) (:line_total item*)))
        repaired-unit-price (when (and (:unit_price item*)
                                    (inconsistent-item-unit-price? item*))
                              (derive-unit-price (:qty item*) (:line_total item*)))]
    (cond-> item*
      derived-unit-price (assoc :unit_price derived-unit-price)
      repaired-unit-price (assoc :unit_price repaired-unit-price))))

(def min-alias-normalized-length
  2)

(defn valid-alias-label?
  [raw-label]
  (let [raw-label* (some-> raw-label str str/trim)
        normalized (articles/normalize-alias-label raw-label*)]
    (and (not (str/blank? raw-label*))
      (>= (count (or normalized "")) min-alias-normalized-length))))

(defn normalize-expense-data
  [expense-data]
  (-> expense-data
    (update-if-present :supplier_id #(parse-uuid! :supplier_id %))
    (update-if-present :payer_id #(parse-uuid! :payer_id %))
    (update-if-present :subject_ref blank->nil)
    (update-if-present :created_by_subject_ref blank->nil)
    (update-if-present :receipt_id #(parse-uuid! :receipt_id %))
    (update-if-present :store_id #(parse-uuid! :store_id %))
    (update-if-present :expense_category_id #(parse-uuid! :expense_category_id %))
    (update-if-present :article_id #(parse-uuid! :article_id %))
    (update-if-present :purchased_at #(parse-instant! :purchased_at %))
    (update-if-present :total_amount #(parse-bigdec! :total_amount %))
    (update-if-present :currency #(some-> % str str/trim blank->nil))
    (update-if-present :notes blank->nil)))

(defn expense-rate-date
  [{:keys [purchased_at]}]
  (if (instance? Instant purchased_at)
    (LocalDate/ofInstant purchased_at ZoneOffset/UTC)
    (LocalDate/now)))

(defn compute-bam-amount
  [total-amount rate]
  (some-> (.multiply ^java.math.BigDecimal total-amount
            ^java.math.BigDecimal rate)
    (.setScale 2 RoundingMode/HALF_UP)))

(defn normalize-expense-amounts
  ([db expense-data]
   (normalize-expense-amounts db expense-data nil))
  ([db expense-data {:keys [app-config ensure-rates?]}]
   (let [total-amount (:total_amount expense-data)
         currency (or (:currency expense-data) "BAM")]
     (if-not total-amount
       (assoc expense-data :currency currency)
       (if (= "BAM" currency)
         (assoc expense-data
           :currency currency
           :original_amount total-amount
           :bam_amount total-amount
           :exchange_rate nil
           :rate_fetched_at nil)
         (let [rate-date (expense-rate-date expense-data)
               _ (when (and ensure-rates? app-config)
                   (try
                     (exchange-rates/ensure-daily-rates!
                       db
                       (exchange-rates/build-config app-config))
                     (catch Exception e
                       (log/warn e "Failed to ensure daily rates"
                         {:currency currency
                          :rate-date (str rate-date)}))))
               rate (or (:exchange_rate expense-data)
                      (exchange-rates/get-conversion-rate db currency rate-date))
               bam-amount (or (when rate
                                (compute-bam-amount total-amount rate))
                            total-amount)]
           (assoc expense-data
             :currency currency
             :original_amount total-amount
             :bam_amount bam-amount
             :exchange_rate rate
             :rate_fetched_at (when rate
                                (or (:rate_fetched_at expense-data)
                                  (Instant/now))))))))))