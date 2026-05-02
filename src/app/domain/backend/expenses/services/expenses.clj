(ns app.domain.backend.expenses.services.expenses
  "Expense creation/update with line items."
  (:require
    [app.domain.backend.expenses.services.article-aliases :as aliases]
    [app.domain.backend.expenses.services.expenses.parsing :as parsing]
    [app.domain.backend.expenses.services.expenses.queries :as queries]
    [clojure.string :as str]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs])
  (:import
    [java.util UUID]))

;; ============================================================================
;; Helpers
;; ============================================================================

(defn- require-keys!
  [m ks]
  (parsing/require-keys! m ks))

(defn- parse-uuid!
  [field v]
  (parsing/parse-uuid! field v))

(defn- update-if-present
  [m k f]
  (parsing/update-if-present m k f))

(defn- normalize-expense-item
  [item]
  (parsing/normalize-expense-item item))

(defn- valid-alias-label?
  [raw-label]
  (parsing/valid-alias-label? raw-label))

(defn- resolve-alias!
  "Resolve or create an article alias for an expense item.

  Accepts either:
  - :alias_id (UUID)
  - :raw_label (string) (upserted into article_aliases)

  Options:
  - :allow-auto-link? (default true) controls whether raw_label can be used to
    auto-link existing items on update.

  Returns the alias row or nil when both are missing/invalid."
  ([tx supplier-id item]
   (resolve-alias! tx supplier-id item {}))
  ([tx supplier-id {:keys [raw_label alias_id unit]} {:keys [allow-auto-link?] :or {allow-auto-link? true}}]
   (cond
     alias_id
     (when-let [alias (jdbc/execute-one!
                        tx
                        (sql/format {:select [:*]
                                     :from [:article_aliases]
                                     :where [:= :id alias_id]
                                     :limit 1})
                        {:builder-fn rs/as-unqualified-lower-maps})]
       alias)

     (and allow-auto-link?
       (valid-alias-label? raw_label))
     (let [alias (aliases/find-or-create-alias! tx supplier-id (str/trim (str raw_label)) unit)]
       alias)

     :else nil)))

(defn- normalize-expense-data
  [expense-data]
  (parsing/normalize-expense-data expense-data))

(defn normalize-expense-amounts
  ([db expense-data]
   (parsing/normalize-expense-amounts db expense-data))
  ([db expense-data opts]
   (parsing/normalize-expense-amounts db expense-data opts)))

(defn- revert-linked-receipt-after-expense-delete!
  "If the deleted expense was created from a receipt, revert the receipt so it can be
  deleted or approved+posted again.

  Rules:
  - Only applies when the receipt is currently linked to this expense.
  - Always clears the receipt's :expense_id link.
  - Only reverts receipts in status \"posted\" back to \"extracted\".

  NOTE: We revert to \"extracted\" (approvable); there is no \"exported\" receipt status
  (\"exported\" refers to the local file storage subdir used during posting)."
  [tx {:keys [id receipt_id]}]
  (when (and id receipt_id)
    (jdbc/execute-one!
      tx
      (sql/format {:update :receipts
                   :set {:expense_id nil
                         ;; If the receipt was posted, revert it to an approvable state.
                         ;; If it was already reverted/reset to a different status, keep it
                         ;; but still clear the now-invalid expense link.
                         :status [:raw "CASE WHEN status = 'posted'::receipt_status THEN 'extracted'::receipt_status ELSE status END"]}
                   :where [:and
                           [:= :id receipt_id]
                           [:= :expense_id id]]
                   :returning [:*]})
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn delete-expense!
  "Hard delete expense.

  Expense items are removed via FK ON DELETE CASCADE.

  If the expense is linked to a receipt (via :receipt_id and the receipt's :expense_id),
  the receipt is reverted to an approvable status so it can be deleted or processed again.

  Optional `tenant-id` scopes the delete to a specific tenant."
  ([db id] (delete-expense! db id nil))
  ([db id tenant-id]
   (jdbc/with-transaction [tx db]
     (let [where (if tenant-id
                   [:and [:= :id id] [:= :tenant_id tenant-id]]
                   [:= :id id])]
       (when-let [expense (jdbc/execute-one!
                            tx
                            (sql/format {:select [:*]
                                         :from [:expenses]
                                         :where where
                                         :limit 1})
                            {:builder-fn rs/as-unqualified-lower-maps})]
         (revert-linked-receipt-after-expense-delete! tx expense)
         (jdbc/execute-one!
           tx
           (sql/format {:delete-from :expenses
                        :where where
                        :returning [:*]})
           {:builder-fn rs/as-unqualified-lower-maps}))))))

(defn get-expense-with-items
  "Get an expense with its items. Optional `tenant-id` scopes to a specific tenant."
  ([db id] (queries/get-expense-with-items db id))
  ([db id tenant-id] (queries/get-expense-with-items db id tenant-id)))

(defn get-expense
  "Get an expense (with items). Wrapper expected by the generic admin routes factory."
  [db id]
  (get-expense-with-items db id))

(defn list-expenses
  "List expenses with common filters.
   opts: :from, :to, :supplier-id, :payer-id, :tenant-id, :source, :limit, :offset."
  [db opts]
  (queries/list-expenses db opts))

(defn count-expenses
  "Count expenses with the same filters as `list-expenses`.
   opts: :from, :to, :supplier-id, :payer-id, :tenant-id, :source."
  [db opts]
  (queries/count-expenses db opts))

(defn- lookup-tenant-id
  [db table-name entity-id]
  (when entity-id
    (some-> (jdbc/execute-one!
              db
              (sql/format {:select [:tenant_id]
                           :from [table-name]
                           :where [:= :id entity-id]
                           :limit 1})
              {:builder-fn rs/as-unqualified-lower-maps})
      :tenant_id)))

(defn- infer-expense-tenant-id
  [db {:keys [tenant_id receipt_id payer_id]}]
  (or tenant_id
    (lookup-tenant-id db :receipts receipt_id)
    (lookup-tenant-id db :payers payer_id)))

(defn- existing-expense-id-for-receipt
  "Return the already-linked expense id for a receipt, if any.
   Throws 404 when the referenced receipt does not exist."
  [tx receipt-id]
  (let [receipt (jdbc/execute-one!
                  tx
                  (sql/format {:select [:id :expense_id]
                               :from [:receipts]
                               :where [:= :id receipt-id]
                               :limit 1})
                  {:builder-fn rs/as-unqualified-lower-maps})]
    (when-not receipt
      (throw (ex-info "Receipt not found"
               {:status 404
                :field :receipt_id
                :receipt-id receipt-id})))
    (or (:expense_id receipt)
      (:id (jdbc/execute-one!
             tx
             (sql/format {:select [:id]
                          :from [:expenses]
                          :where [:= :receipt_id receipt-id]
                          :limit 1})
             {:builder-fn rs/as-unqualified-lower-maps})))))

(declare throw-receipt-link-conflict!)

(defn- ensure-receipt-link-available!
  "Prevent creating a second expense for the same receipt.
   Callers must explicitly unlink first (for example by deleting the linked expense)."
  [tx {:keys [receipt_id]}]
  (when receipt_id
    (when-let [expense-id (existing-expense-id-for-receipt tx receipt_id)]
      (throw-receipt-link-conflict! receipt_id expense-id))))

(defn- throw-receipt-link-conflict!
  [receipt-id expense-id]
  (throw (ex-info "Receipt already linked to an expense. Unlink it first before creating another expense"
           {:status 409
            :field :receipt_id
            :receipt-id receipt-id
            :expense-id expense-id})))

(defn create-expense!
  "Create an expense and its line items.

  Supports two arities:
  - (create-expense! db {:supplier_id ... :items [...] ...})
  - (create-expense! db expense-data items)

  Behavior:
  - Stores each line item's raw label via `article_aliases` when the label is valid.
  - Skips alias creation for blank/short/punctuation-only labels.
  - Falls back to inferring `:tenant_id` from the linked receipt or payer when
    older callers omit it.
  - Persists normalized monetary fields (`original_amount`, `bam_amount`,
    `exchange_rate`, `rate_fetched_at`) for every create path."
  ([db {:keys [items] :as body}]
   (create-expense! db (dissoc body :items) items))
  ([db expense-data items]
   (let [expense-data* (normalize-expense-data expense-data)
         normalized-items (mapv normalize-expense-item (or items []))
         article-id (:article_id expense-data*)
         items* (if (and article-id (empty? normalized-items))
                  (let [article (jdbc/execute-one!
                                  db
                                  (sql/format {:select [:canonical_name]
                                               :from [:articles]
                                               :where [:= :id article-id]
                                               :limit 1})
                                  {:builder-fn rs/as-unqualified-lower-maps})
                        article-name (or (:canonical_name article) "Item")]
                    [{:raw_label article-name
                      :qty (bigdec 1)
                      :unit_price (:total_amount expense-data*)
                      :line_total (:total_amount expense-data*)}])
                  normalized-items)]

     (jdbc/with-transaction [tx db]
       (let [expense-data* (if (and (nil? (:supplier_id expense-data*))
                                 (:store_id expense-data*))
                             (if-let [store (jdbc/execute-one!
                                              tx
                                              (sql/format {:select [:supplier_id]
                                                           :from [:stores]
                                                           :where [:= :id (:store_id expense-data*)]
                                                           :limit 1})
                                              {:builder-fn rs/as-unqualified-lower-maps})]
                               (assoc expense-data* :supplier_id (:supplier_id store))
                               expense-data*)
                             expense-data*)
             expense-data* (if-let [inferred-tenant-id (infer-expense-tenant-id tx expense-data*)]
                             (assoc expense-data* :tenant_id inferred-tenant-id)
                             expense-data*)
             _ (require-keys! expense-data* [:payer_id :purchased_at :total_amount])
             _ (ensure-receipt-link-available! tx expense-data*)
             expense-data* (normalize-expense-amounts tx expense-data*)
             ;; Context validation: at least one of supplier, store, category, or article
             _ (when-not (or (:supplier_id expense-data*)
                           (:store_id expense-data*)
                           (:expense_category_id expense-data*)
                           article-id)
                 (throw (ex-info "At least one context is required: supplier, store, category, or article"
                          {:status 400 :field :context})))
             expense-id (UUID/randomUUID)
             expense-row (-> expense-data*
                           (select-keys [:tenant_id
                                         :store_id
                                         :supplier_id
                                         :payer_id
                                         :expense_category_id
                                         :expense_context_id
                                         :subject_ref
                                         :created_by_subject_ref
                                         :receipt_id
                                         :purchased_at
                                         :total_amount
                                         :original_amount
                                         :bam_amount
                                         :exchange_rate
                                         :rate_fetched_at
                                         :currency
                                         :notes])
                           (update-if-present :currency #(when % [:cast % :currency]))
                           (assoc :id expense-id))
             expense (try
                       (jdbc/execute-one!
                         tx
                         (sql/format {:insert-into :expenses
                                      :values [expense-row]
                                      :returning [:*]})
                         {:builder-fn rs/as-unqualified-lower-maps})
                       (catch org.postgresql.util.PSQLException e
                         (let [message (or (.getMessage e) "")]
                           (if (and (:receipt_id expense-row)
                                 (= "23505" (.getSQLState e))
                                 (or (str/includes? message "uniq_expenses_receipt_id")
                                   (str/includes? message "expenses_receipt_id")))
                             (throw-receipt-link-conflict! (:receipt_id expense-row)
                               (existing-expense-id-for-receipt tx (:receipt_id expense-row)))
                             (throw e)))))
             supplier-id (:supplier_id expense)]

         ;; Insert line items (if any — empty items is valid for minimal entry)
         (when (seq items*)
           (let [resolved-items (mapv (fn [item]
                                        (require-keys! item [:line_total])
                                        (let [alias (resolve-alias! tx supplier-id item)
                                              resolved-article-id (:article_id alias)]
                                          (assoc item
                                            :resolved_alias alias
                                            :resolved_alias_id (some-> alias :id)
                                            :resolved_article_id resolved-article-id)))
                                  items*)
                 tenant-id (:tenant_id expense)
                 item-rows (mapv (fn [{:keys [resolved_alias_id qty unit unit_price line_total price_modified]}]
                                   (cond-> {:id (UUID/randomUUID)
                                            :expense_id expense-id
                                            :alias_id resolved_alias_id
                                            :qty qty
                                            :unit_price unit_price
                                            :line_total line_total}
                                     tenant-id (assoc :tenant_id tenant-id)
                                     (some? price_modified) (assoc :price_modified price_modified)
                                     unit (assoc :unit unit)))
                             resolved-items)]
             (jdbc/execute!
               tx
               (sql/format {:insert-into :expense_items
                            :values item-rows
                            :returning [:*]})
               {:builder-fn rs/as-unqualified-lower-maps})))

         (get-expense-with-items tx expense-id))))))

(defn- ensure-direct-expense-edit-allowed!
  "Reject direct edits for receipt-linked expenses unless the caller explicitly opts in.

  Receipt-linked expenses must be changed through receipt editing so the expense
  row, receipt metadata, and receipt status stay in sync."
  [{:keys [id receipt_id]} {:keys [allow-linked-expense-update?]}]
  (when (and receipt_id (not allow-linked-expense-update?))
    (throw (ex-info "Only manually entered expenses can be edited directly. Edit the linked receipt instead."
             {:status 409
              :field :receipt_id
              :expense-id id
              :receipt-id receipt_id}))))

(defn update-expense!
  "Update an expense and optionally upsert its items.

  If `:items` is present in the update body, the provided set becomes the new
  active set:
  - missing items are deleted
  - items with matching `:id` are updated
  - items without `:id` are inserted

  Auto-linking from aliases is applied only for newly inserted items (existing
  items are not retroactively auto-linked unless the alias is explicitly changed).

  Receipt-linked expenses (`:receipt_id`) are read-only for direct expense edits
  by default. Use `:allow-linked-expense-update?` only from receipt workflows
  that keep the linked receipt in sync.

  Optional `tenant-id` scopes the update to a specific tenant."
  ([db id body] (update-expense! db id body nil nil))
  ([db id body tenant-id] (update-expense! db id body tenant-id nil))
  ([db id body tenant-id opts]
   (let [id* (parse-uuid! :id id)
         parsed-updates (-> body
                          (dissoc :items)
                          normalize-expense-data)
         amount-keys [:purchased_at :total_amount :currency]
         base-keys [:store_id :supplier_id :payer_id :expense_category_id :expense_context_id :purchased_at :total_amount :currency :notes]
         where (if tenant-id
                 [:and [:= :id id*] [:= :tenant_id tenant-id]]
                 [:= :id id*])]
     (jdbc/with-transaction [tx db]
       (when-let [existing (jdbc/execute-one!
                             tx
                             (sql/format {:select [:*]
                                          :from [:expenses]
                                          :where where
                                          :limit 1})
                             {:builder-fn rs/as-unqualified-lower-maps})]
         (ensure-direct-expense-edit-allowed! existing opts)
         (let [updates* (if (seq parsed-updates)
                          (let [base-updates (select-keys parsed-updates base-keys)]
                            (if (some #(contains? base-updates %) amount-keys)
                              (let [amount-input (merge (select-keys existing [:purchased_at :total_amount :currency :original_amount :bam_amount :exchange_rate :rate_fetched_at])
                                                   base-updates)
                                    normalized-amounts (select-keys (normalize-expense-amounts tx amount-input)
                                                         [:original_amount :bam_amount :exchange_rate :rate_fetched_at])]
                                (merge base-updates normalized-amounts))
                              base-updates))
                          {})
               updates* (update-if-present updates* :currency #(when % [:cast % :currency]))
               expense (if (seq updates*)
                         (jdbc/execute-one!
                           tx
                           (sql/format {:update :expenses
                                        :set updates*
                                        :where where
                                        :returning [:*]})
                           {:builder-fn rs/as-unqualified-lower-maps})
                         existing)]

           (when (contains? body :items)
             (let [existing-ids (->> (jdbc/execute!
                                       tx
                                       (sql/format {:select [:id]
                                                    :from [:expense_items]
                                                    :where [:= :expense_id id*]})
                                       {:builder-fn rs/as-unqualified-lower-maps})
                                  (map :id)
                                  (into #{}))
                   items* (mapv normalize-expense-item (or (:items body) []))
                   _ (when (empty? items*)
                       (throw (ex-info "At least one line item is required" {:status 400 :field :items})))

                   update-items (filterv (fn [{item-id :id}]
                                           (and item-id (contains? existing-ids item-id)))
                                  items*)
                   keep-ids (into #{} (map :id update-items))
                   insert-items (filterv (fn [{item-id :id}]
                                           (not (and item-id (contains? existing-ids item-id))))
                                  items*)
                   supplier-id (:supplier_id expense)
                   delete-where (if (seq keep-ids)
                                  [:and
                                   [:= :expense_id id*]
                                   [:not [:in :id keep-ids]]]
                                  [:= :expense_id id*])]

              ;; Delete removed items.
               (jdbc/execute!
                 tx
                 (sql/format {:delete-from :expense_items
                              :where delete-where}))

              ;; Update existing items (no retroactive auto-linking).
               (doseq [item update-items]
                 (require-keys! item [:line_total])
                 (let [alias (resolve-alias! tx supplier-id item)
                       item* (cond-> (select-keys item [:qty :unit :unit_price :line_total])
                               alias (assoc :alias_id (:id alias)))]
                   (jdbc/execute!
                     tx
                     (sql/format {:update :expense_items
                                  :set item*
                                  :where [:and
                                          [:= :id (:id item)]
                                          [:= :expense_id id*]]}))))

              ;; Insert new items (auto-link from alias when possible).
               (let [resolved-inserts (mapv (fn [item]
                                              (require-keys! item [:line_total])
                                              (let [alias (resolve-alias! tx supplier-id item)
                                                    resolved-article-id (:article_id alias)]
                                                (assoc item
                                                  :resolved_alias alias
                                                  :resolved_alias_id (some-> alias :id)
                                                  :resolved_article_id resolved-article-id)))
                                        insert-items)
                     exp-tenant-id (:tenant_id expense)
                     item-rows (mapv (fn [{:keys [resolved_alias_id qty unit unit_price line_total]}]
                                       (cond-> {:id (UUID/randomUUID)
                                                :expense_id id*
                                                :alias_id resolved_alias_id
                                                :qty qty
                                                :unit_price unit_price
                                                :line_total line_total}
                                         exp-tenant-id (assoc :tenant_id exp-tenant-id)
                                         unit (assoc :unit unit)))
                                 resolved-inserts)
                     _inserted-items (if (seq item-rows)
                                       (jdbc/execute!
                                         tx
                                         (sql/format {:insert-into :expense_items
                                                      :values item-rows
                                                      :returning [:*]})
                                         {:builder-fn rs/as-unqualified-lower-maps})
                                       [])])))

           (get-expense-with-items tx id*)))))))


