(ns app.domain.frontend.expenses.admin.adapters.normalize
  "Normalization helpers for expenses entities.

   These functions convert raw API responses to normalized entities
   for the template entity store."
  (:require
    [app.template.frontend.shared.utils.entity :as entity-utils]
    [clojure.string :as str]))

;; =============================================================================
;; Normalization helpers
;; =============================================================================

(defn- ->number
  "Best-effort coercion to number for comparing amounts from API payloads.

  Accepts numbers and strings (supports comma decimals). Returns nil when
  coercion fails."
  [value]
  (cond
    (number? value) value
    (string? value) (let [n (js/parseFloat (str/replace value "," "."))]
                      (when-not (js/isNaN n) n))
    :else nil))

(defn- fmt-amount
  "Render an amount for table display.

  Keeps trailing zeros with 2 decimals when the amount is numeric; otherwise
  falls back to stringification."
  [amount]
  (cond
    (nil? amount) nil
    (number? amount) (.toFixed (js/Number. amount) 2)
    (string? amount) (let [n (->number amount)]
                       (if (some? n)
                         (.toFixed (js/Number. n) 2)
                         amount))
    :else (str amount)))

(defn- amounts-different?
  "Compare amounts with a small tolerance to avoid float noise."
  [a b]
  (let [a* (->number a)
        b* (->number b)]
    (and (some? a*)
      (some? b*)
      (> (js/Math.abs (- a* b*)) 0.009))))

(defn expense->template-entity
  [expense]
  (entity-utils/normalize-entity
    expense
    {:entity-ns :expenses
     :id-keys [:id]
     ;; Stringify foreign-key identifiers so they can be used as
     ;; stable string IDs in the shared template entity store.
     :stringify-keys [:supplier_id :payer_id :expense_category_id :receipt_id]
     ;; Alias commonly used fields to their kebab-case counterparts so
     ;; vector-config / entity-specs (which are based on models.edn) can
     ;; resolve them by the expected IDs like :supplier-id, :payer-id, etc.
     :alias-keys {:supplier_display_name [:supplier-display-name]
                  :store_display_name [:store-display-name]
                  :supplier_normalized_key [:supplier-normalized-key]
                  :payer_label [:payer-label]
                  :payer_type [:payer-type]
                  :expense_category_name [:expense-category-name]
                  :total_amount [:total-amount]
                  :purchased_at [:purchased-at]
                  :created_at [:created-at]
                  :updated_at [:updated-at]
                  ;; FK id aliases used by list-view generated specs
                  :supplier_id [:supplier-id]
                  :payer_id [:payer-id]
                  :expense_category_id [:expense-category-id]

                  :receipt_id [:receipt-id]
                  ;; Expand feature: number of line items for this expense
                  :item_count [:item-count]}}))

(defn expense-item->template-entity
  [item]
  (entity-utils/normalize-entity
    item
    {:entity-ns :expense-items
     :id-keys [:id]
     :stringify-keys [:expense_id :alias_id]
     :alias-keys {:expense_id [:expense-id]
                  :alias_id [:alias-id]
                  :raw_label [:raw-label]
                  :raw_label_normalized [:raw-label-normalized]
                  :qty [:qty]
                  :unit [:unit]
                  :unit_price [:unit-price]
                  :line_total [:line-total]
                  :expense_purchased_at [:expense-purchased-at]
                  :article_canonical_name [:article-canonical-name]
                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn receipt->template-entity
  [receipt]
  (entity-utils/normalize-entity
    receipt
    {:entity-ns :receipts
     :id-keys [:id]
     :alias-keys {:original_filename [:original-filename]
                  :supplier_guess [:supplier-guess]
                  :status [:status]
                  :created_at [:created-at]}
     :post-transform (fn [m]
                       (let [total (:total-amount-guess m)
                             lines-total (:lines-total-amount-guess m)
                             currency (:currency-guess m)
                             total-str (fmt-amount total)
                             lines-str (fmt-amount lines-total)
                             currency-str (when (and (string? currency) (not (str/blank? currency))) currency)
                             suffix (when currency-str (str " " currency-str))
                             total-display (cond
                                             (nil? total-str) nil
                                             (and (some? lines-str)
                                               (amounts-different? total lines-total))
                                             (str total-str suffix " (lines " lines-str ")")
                                             :else
                                             (str total-str suffix))]
                         (cond-> m
                           (some? total-display) (assoc :total-display total-display))))}))

(defn supplier->template-entity
  [supplier]
  (entity-utils/normalize-entity
    supplier
    {:entity-ns :suppliers
     :id-keys [:id]
     :alias-keys {:display_name [:display-name]
                  :normalized_key [:normalized-key]
                  :address [:address]
                  :created_at [:created-at]
                  :updated_at [:updated-at]
                  ;; Expand feature: number of stores for this supplier
                  :store_count [:store-count]}}))

(defn manufacturer->template-entity
  [manufacturer]
  (entity-utils/normalize-entity
    manufacturer
    {:entity-ns :manufacturers
     :id-keys [:id]
     :alias-keys {:display_name [:display-name]
                  :normalized_key [:normalized-key]
                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn category->template-entity
  [category]
  (entity-utils/normalize-entity
    category
    {:entity-ns :categories
     :id-keys [:id]
     :alias-keys {:created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn expense-category->template-entity
  [expense-category]
  (entity-utils/normalize-entity
    expense-category
    {:entity-ns :expense-categories
     :id-keys [:id]
     :alias-keys {:created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn expense-context->template-entity
  [expense-context]
  (entity-utils/normalize-entity
    expense-context
    {:entity-ns :expense-contexts
     :id-keys [:id]
     :alias-keys {:is_active [:is-active]
                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn city->template-entity
  [city]
  (entity-utils/normalize-entity
    city
    {:entity-ns :cities
     :id-keys [:id]
     :alias-keys {:normalized_key [:normalized-key]
                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn country->template-entity
  [country]
  (entity-utils/normalize-entity
    country
    {:entity-ns :countries
     :id-keys [:id]
     :alias-keys {:created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn subcategory->template-entity
  [subcategory]
  (entity-utils/normalize-entity
    subcategory
    {:entity-ns :subcategories
     :id-keys [:id]
     :stringify-keys [:category_id]
     :alias-keys {:category_id [:category-id]
                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn payer->template-entity
  [payer]
  (entity-utils/normalize-entity
    payer
    {:entity-ns :payers
     :id-keys [:id]
     :alias-keys {:payer_type [:payer-type :payer-type-label]
                  :type [:type :payer-type :payer-type-label]
                  :is_default [:is-default]
                  :is_active [:is-active]
                  :related_expense_count [:related-expense-count]
                  :created_at [:created-at]
                  :user_email [:user-email]
                  :user_full_name [:user-full-name]}}))

(defn article->template-entity
  [article]
  (entity-utils/normalize-entity
    article
    {:entity-ns :articles
     :id-keys [:id]
     :stringify-keys [:manufacturer_id :subcategory_id]
     :alias-keys {:canonical_name [:canonical-name]
                  :normalized_key [:normalized-key]
                  :unit [:unit]
                  :category [:category]
                  :link [:link]
                  ;; manufacturer FK + display
                  :manufacturer_id [:manufacturer-id]
                  :manufacturer_display_name [:manufacturer-display-name]
                  ;; subcategory FK
                  :subcategory_id [:subcategory-id]
                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn article-alias->template-entity
  [alias]
  (entity-utils/normalize-entity
    alias
    {:entity-ns :article-aliases
     :id-keys [:id]
     :stringify-keys [:supplier_id :article_id]
     :alias-keys {:supplier_display_name [:supplier-display-name]
                  :article_canonical_name [:article-canonical-name]
                  :raw_label [:raw-label]
                  :raw_label_normalized [:raw-label-normalized]
                  :unit [:unit]
                  :created_at [:created-at]
                  ;; FK id aliases used by list-view generated specs / table-columns.edn
                  :supplier_id [:supplier-id]
                  :article_id [:article-id]}}))

(defn supplier-alias->template-entity
  [alias]
  (entity-utils/normalize-entity
    alias
    {:entity-ns :supplier-aliases
     :id-keys [:id]
     :stringify-keys [:supplier_id]
     :alias-keys {:supplier_display_name [:supplier-display-name]
                  :raw_label [:raw-label]
                  :raw_label_normalized [:raw-label-normalized]
                  :supplier_id [:supplier-id]
                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn store->template-entity
  [store]
  (entity-utils/normalize-entity
    store
    {:entity-ns :stores
     :id-keys [:id]
     :stringify-keys [:supplier_id :supplier-id :city_id :city-id]
     :alias-keys {:supplier_id [:supplier-id]
                  :city_id [:city-id]
                  :display_name [:display-name]
                  :normalized_key [:normalized-key]

                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn store-alias->template-entity
  [alias]
  (entity-utils/normalize-entity
    alias
    {:entity-ns :store-aliases
     :id-keys [:id]
     :stringify-keys [:store_id :store-id :supplier_id :supplier-id]
     :alias-keys {:store_id [:store-id]
                  :supplier_id [:supplier-id]
                  :supplier_display_name [:supplier-display-name]
                  :store_display_name [:store-display-name]
                  :store_address [:store-address]
                  :raw_label [:raw-label]
                  :raw_label_normalized [:raw-label-normalized]
                  :created_at [:created-at]
                  :updated_at [:updated-at]}}))

(defn unmapped-alias->template-entity
  [alias]
  (entity-utils/normalize-entity
    alias
    {:entity-ns :unmapped-aliases
     :id-keys [:id]
     :stringify-keys [:supplier_id :supplier-id]
     :alias-keys {:supplier_display_name [:supplier-display-name]
                  :raw_label [:raw-label]
                  :raw_label_normalized [:raw-label-normalized]
                  :unit [:unit]
                  :occurrence_count [:occurrence-count]
                  ;; FK id aliases
                  :supplier_id [:supplier-id]}}))
