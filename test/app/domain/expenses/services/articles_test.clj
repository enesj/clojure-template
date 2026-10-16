(ns app.domain.expenses.services.articles-test
  "Integration tests for articles service."
  (:require
    [app.backend.fixtures :as fixtures]
    [app.domain.backend.expenses.services.article-aliases :as aliases]
    [app.domain.backend.expenses.services.articles :as articles]
    [app.domain.backend.expenses.services.expenses :as expenses]
    [app.domain.backend.expenses.services.store-aliases :as store-aliases]
    [app.domain.backend.expenses.services.stores :as stores]
    [app.domain.backend.expenses.services.supplier-aliases :as supplier-aliases]
    [app.domain.backend.expenses.services.suppliers :as suppliers]
    [app.domain.expenses.test-helpers :as th]
    [clojure.test :refer [deftest is use-fixtures]]
    [next.jdbc :as jdbc])
  (:import
    (java.util UUID)))

(use-fixtures :each fixtures/with-transaction-rollback)

(defn- now [] (java.time.Instant/now))

(defn- insert-receipt!
  [db {:keys [original-filename supplier-alias-id store-alias-id created-at tenant-id]}]
  (let [receipt-id (UUID/randomUUID)
        file-hash (str (UUID/randomUUID))
        storage-key (str "test/receipts/" receipt-id)
        resolved-tenant-id (or tenant-id
                             (:tenant-id (th/ensure-test-tenant!
                                           db
                                           (th/ensure-test-user! db {:email (str "article-receipt-" (UUID/randomUUID) "@example.com")}))))]
    (jdbc/execute-one!
      db
      ["insert into receipts (id, tenant_id, storage_key, file_hash, original_filename, supplier_alias_id, store_alias_id, created_at) values (?, ?, ?, ?, ?, ?, ?, coalesce(?, now()))"
       receipt-id
       resolved-tenant-id
       storage-key
       file-hash
       original-filename
       supplier-alias-id
       store-alias-id
       created-at])
    receipt-id))

(defn- insert-category!
  [db name]
  (let [category-id (UUID/randomUUID)]
    (jdbc/execute-one! db ["insert into categories (id, name) values (?, ?)" category-id name])
    category-id))

(defn- insert-subcategory!
  [db category-id name]
  (let [subcategory-id (UUID/randomUUID)]
    (jdbc/execute-one!
      db
      ["insert into subcategories (id, category_id, name) values (?, ?, ?)"
       subcategory-id category-id name])
    subcategory-id))

(defn- insert-manufacturer!
  [db display-name normalized-key]
  (let [manufacturer-id (UUID/randomUUID)]
    (jdbc/execute-one!
      db
      ["insert into manufacturers (id, display_name, normalized_key) values (?, ?, ?)"
       manufacturer-id display-name normalized-key])
    manufacturer-id))

(deftest articles-batch-create-aliases-dedupes-and-skips-invalid
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "AliasBatch Supplier " (UUID/randomUUID)) {}))
          article (articles/create-article! db {:canonical_name (str "AliasBatch Article " (UUID/randomUUID))})
          result (articles/batch-create-aliases!
                   db
                   {:supplier-id (:id supplier)
                    :article-id (:id article)
                    :raw-labels [" Milk " "MILK" "" "##" "A"]})]
      (is (= 1 (count (:created result))))
      (is (= "milk" (:raw_label_normalized (first (:created result)))))
      (is (some #(= :duplicate (:reason %)) (:skipped result)))
      (is (some #(= :blank (:reason %)) (:skipped result)))
      (is (some #(= :normalizes-to-blank (:reason %)) (:skipped result)))
      (is (some #(= :too-short (:reason %)) (:skipped result))))))

(deftest articles-batch-create-aliases-conflict-and-reassign
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "AliasConflict Supplier " (UUID/randomUUID)) {}))
          article-a (articles/create-article! db {:canonical_name (str "AliasConflict A " (UUID/randomUUID))})
          article-b (articles/create-article! db {:canonical_name (str "AliasConflict B " (UUID/randomUUID))})
          _ (articles/batch-create-aliases!
              db
              {:supplier-id (:id supplier)
               :article-id (:id article-a)
               :raw-labels ["MILK"]})
          conflict (articles/batch-create-aliases!
                     db
                     {:supplier-id (:id supplier)
                      :article-id (:id article-b)
                      :raw-labels ["MILK"]})
          reassigned (articles/batch-create-aliases!
                       db
                       {:supplier-id (:id supplier)
                        :article-id (:id article-b)
                        :raw-labels ["MILK"]
                        :allow-reassign? true})]
      (is (= 1 (count (:conflicts conflict))))
      (is (empty? (:reassigned conflict)))
      (is (= 1 (count (:reassigned reassigned))))
      (is (empty? (:conflicts reassigned))))))

(deftest articles-find-or-create-distinguishes-unit
  (when-let [db fixtures/*test-db*]
    (let [canonical-name (str "Unit Distinct Article " (UUID/randomUUID))
          kom-article (articles/find-or-create-article-by-canonical-name! db canonical-name)
          kg-article (articles/find-or-create-article-by-canonical-name! db canonical-name "kg")
          kom-again (articles/find-or-create-article-by-canonical-name! db canonical-name)
          kg-again (articles/find-or-create-article-by-canonical-name! db canonical-name "kg")]
      (is (= "kom" (:unit kom-article)))
      (is (= "kg" (:unit kg-article)))
      (is (not= (:id kom-article) (:id kg-article)))
      (is (= (:id kom-article) (:id kom-again)))
      (is (= (:id kg-article) (:id kg-again))))))

(deftest articles-batch-create-aliases-distinguishes-units
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "AliasUnit Supplier " (UUID/randomUUID)) {}))
          canonical-name (str "AliasUnit Article " (UUID/randomUUID))
          article-kom (articles/create-article! db {:canonical_name canonical-name})
          article-kg (articles/create-article! db {:canonical_name canonical-name
                                                   :unit "kg"})
          kom-result (articles/batch-create-aliases!
                       db
                       {:supplier-id (:id supplier)
                        :article-id (:id article-kom)
                        :raw-labels ["MILK"]})
          kg-result (articles/batch-create-aliases!
                      db
                      {:supplier-id (:id supplier)
                       :article-id (:id article-kg)
                       :raw-labels ["MILK"]
                       :unit "kg"})
          kom-created (first (:created kom-result))
          kg-created (first (:created kg-result))
          kom-lookup (articles/find-article-by-alias db (:id supplier) "MILK")
          kg-lookup (articles/find-article-by-alias db (:id supplier) "MILK" "kg")]
      (is (= 1 (count (:created kom-result))))
      (is (= 1 (count (:created kg-result))))
      (is (= "kom" (:unit kom-created)))
      (is (= "kg" (:unit kg-created)))
      (is (not= (:id kom-created) (:id kg-created)))
      (is (= (:id article-kom) (:id kom-lookup)))
      (is (= "kom" (:unit kom-lookup)))
      (is (= (:id article-kg) (:id kg-lookup)))
      (is (= "kg" (:unit kg-lookup))))))

(deftest article-aliases-find-or-create-rejects-too-short-normalized-label
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "Short Alias Supplier " (UUID/randomUUID)) {}))]
      (doseq [raw-label ["A" "##"]]
        (let [error (try
                      (aliases/find-or-create-alias! db (:id supplier) raw-label)
                      nil
                      (catch clojure.lang.ExceptionInfo e
                        e))]
          (is (instance? clojure.lang.ExceptionInfo error))
          (is (= 400 (:status (ex-data error))))
          (is (= :raw_label (:field (ex-data error))))))
      (is (empty? (aliases/list-article-aliases db {:supplier-id (:id supplier)
                                                    :limit 10
                                                    :offset 0}))))))

(deftest article-aliases-find-or-create-rejects-ocr-noise-unless-allowed
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "Noise Alias Supplier " (UUID/randomUUID)) {}))]
      (doseq [raw-label ["69 60936564905" "MG 1 A 3a26" "kom 1"]]
        (let [error (try (aliases/find-or-create-alias! db (:id supplier) raw-label "kom") nil
                         (catch clojure.lang.ExceptionInfo e e))]
          (is (= :ocr-noise (:reason (ex-data error))) raw-label)))
      (is (empty? (aliases/list-article-aliases db {:supplier-id (:id supplier) :limit 10 :offset 0})))
      (is (some? (:id (aliases/find-or-create-alias! db (:id supplier) "MG 1 A 3a26" "kom" {:allow-noise? true})))
        "a label typed by a user is kept")
      (is (some? (:id (aliases/find-or-create-alias! db (:id supplier) "Čaj" "kom"))) "short real product"))))

(deftest articles-map-alias-to-article-makes-lookup-work
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "MapItem Supplier " (UUID/randomUUID)) {}))
          payer (th/create-payer! db {:type "cash" :label "Cash"})
          article (articles/create-article! db {:canonical_name (str "MapItem Article " (UUID/randomUUID))})
          expense (expenses/create-expense!
                    db
                    {:supplier_id (:id supplier)
                     :payer_id (:id payer)
                     :purchased_at (now)
                     :total_amount (bigdec "1.00")
                     :currency "BAM"}
                    [{:raw_label "MILK" :line_total (bigdec "1.00")}])
          alias-id (-> expense :items first :alias_id)]
      (is (uuid? alias-id))

      (aliases/map-alias-to-article! db alias-id (:id article))

      (is (= (:id article)
            (:id (articles/find-article-by-alias db (:id supplier) "MILK")))))))

(deftest articles-list-unmapped-aliases-filters-by-supplier
  (when-let [db fixtures/*test-db*]
    (let [supplier-a (:supplier (suppliers/find-or-create-supplier! db (str "Unmapped Supplier A " (UUID/randomUUID)) {}))
          supplier-b (:supplier (suppliers/find-or-create-supplier! db (str "Unmapped Supplier B " (UUID/randomUUID)) {}))
          payer (th/create-payer! db {:type "cash" :label "Cash"})
          _exp-a (expenses/create-expense!
                   db
                   {:supplier_id (:id supplier-a)
                    :payer_id (:id payer)
                    :purchased_at (now)
                    :total_amount (bigdec "1.00")
                    :currency "BAM"}
                   [{:raw_label "A1" :line_total (bigdec "1.00")}])
          _exp-b (expenses/create-expense!
                   db
                   {:supplier_id (:id supplier-b)
                    :payer_id (:id payer)
                    :purchased_at (now)
                    :total_amount (bigdec "1.00")
                    :currency "BAM"}
                   [{:raw_label "B1" :line_total (bigdec "1.00")}])
          rows (aliases/list-unmapped-aliases db {:supplier-id (:id supplier-a) :limit 50 :offset 0})]
      (is (= 1 (count rows)))
      (is (= (:id supplier-a) (:supplier_id (first rows))))
      (is (= "A1" (:raw_label (first rows))))
      (is (string? (:supplier_display_name (first rows)))))))

(deftest articles-list-unmapped-aliases-scopes-to-tenant-occurrences
  (when-let [db fixtures/*test-db*]
    (let [user-a (th/ensure-test-user! db {:email (str "unmapped-tenant-a-" (UUID/randomUUID) "@example.com")})
          user-b (th/ensure-test-user! db {:email (str "unmapped-tenant-b-" (UUID/randomUUID) "@example.com")})
          {:keys [tenant-id] :as _tenant-a} (th/ensure-test-tenant! db user-a)
          tenant-a-id tenant-id
          {:keys [tenant-id] :as _tenant-b} (th/ensure-test-tenant! db user-b)
          tenant-b-id tenant-id
          supplier (:supplier (suppliers/find-or-create-supplier! db (str "Tenant Scoped Supplier " (UUID/randomUUID)) {}))
          payer-a (th/create-payer! db {:type "cash"
                                        :label (str "Cash A " (UUID/randomUUID))
                                        :tenant_id tenant-a-id})
          payer-b (th/create-payer! db {:type "cash"
                                        :label (str "Cash B " (UUID/randomUUID))
                                        :tenant_id tenant-b-id})
          _exp-a (expenses/create-expense!
                   db
                   {:tenant_id tenant-a-id
                    :supplier_id (:id supplier)
                    :payer_id (:id payer-a)
                    :purchased_at (now)
                    :total_amount (bigdec "1.00")
                    :currency "BAM"}
                   [{:raw_label "TENANT-A-ONLY" :line_total (bigdec "1.00")}])
          _exp-b (expenses/create-expense!
                   db
                   {:tenant_id tenant-b-id
                    :supplier_id (:id supplier)
                    :payer_id (:id payer-b)
                    :purchased_at (now)
                    :total_amount (bigdec "1.00")
                    :currency "BAM"}
                   [{:raw_label "TENANT-B-ONLY" :line_total (bigdec "1.00")}])
          tenant-a-rows (aliases/list-unmapped-aliases db {:tenant-id tenant-a-id :limit 50 :offset 0})
          tenant-b-rows (aliases/list-unmapped-aliases db {:tenant-id tenant-b-id :limit 50 :offset 0})]
      (is (= #{"TENANT-A-ONLY"}
            (set (map :raw_label tenant-a-rows))))
      (is (= #{"TENANT-B-ONLY"}
            (set (map :raw_label tenant-b-rows))))
      (is (= 1 (aliases/count-unmapped-aliases db {:tenant-id tenant-a-id})))
      (is (= 1 (aliases/count-unmapped-aliases db {:tenant-id tenant-b-id}))))))

(deftest article-aliases-list-supports-visible-column-text-filters
  (when-let [db fixtures/*test-db*]
    (let [token (str (UUID/randomUUID))
          supplier-a-name (str "Article Alias Filter Supplier A " token)
          supplier-b-name (str "Article Alias Filter Supplier B " token)
          article-a-name (str "Article Alias Filter Article A " token)
          article-b-name (str "Article Alias Filter Article B " token)
          supplier-a (:supplier (suppliers/find-or-create-supplier! db supplier-a-name {}))
          supplier-b (:supplier (suppliers/find-or-create-supplier! db supplier-b-name {}))
          article-a (articles/create-article! db {:canonical_name article-a-name})
          article-b (articles/create-article! db {:canonical_name article-b-name})
          alias-a (aliases/find-or-create-alias! db (:id supplier-a) (str token " Alpha Tea"))
          alias-b (aliases/find-or-create-alias! db (:id supplier-b) (str token " Omega Coffee"))
          _mapped-a (aliases/map-alias-to-article! db (:id alias-a) (:id article-a))
          _mapped-b (aliases/map-alias-to-article! db (:id alias-b) (:id article-b))
          by-supplier (aliases/list-article-aliases db {:supplier-display-name supplier-a-name
                                                        :limit 50
                                                        :offset 0})
          by-article (aliases/list-article-aliases db {:article-canonical-name article-b-name
                                                       :limit 50
                                                       :offset 0})
          by-raw-label (aliases/list-article-aliases db {:raw-label "Alpha Tea"
                                                         :limit 50
                                                         :offset 0})
          by-normalized (aliases/list-article-aliases db {:raw-label-normalized "omega-coffee"
                                                          :limit 50
                                                          :offset 0})]
      (is (= [(:id alias-a)] (mapv :id by-supplier)))
      (is (= [(:id alias-b)] (mapv :id by-article)))
      (is (= [(:id alias-a)] (mapv :id by-raw-label)))
      (is (= [(:id alias-b)] (mapv :id by-normalized)))
      (is (= 1 (aliases/count-article-aliases db {:supplier-display-name supplier-a-name})))
      (is (= 1 (aliases/count-article-aliases db {:article-canonical-name article-b-name})))
      (is (= 1 (aliases/count-article-aliases db {:raw-label "Alpha Tea"})))
      (is (= 1 (aliases/count-article-aliases db {:raw-label-normalized "omega-coffee"}))))))

(deftest articles-list-unmapped-aliases-supports-text-filters-and-sorting
  (when-let [db fixtures/*test-db*]
    (let [token (str (UUID/randomUUID))
          supplier-a-name (str "Sort Filter Supplier A " token)
          supplier-b-name (str "Sort Filter Supplier B " token)
          raw-label-a (str token "-ALPHA")
          raw-label-b (str token "-OMEGA")
          supplier-a (:supplier (suppliers/find-or-create-supplier! db supplier-a-name {}))
          supplier-b (:supplier (suppliers/find-or-create-supplier! db supplier-b-name {}))
          payer (th/create-payer! db {:type "cash" :label "Cash"})
          _exp-a-1 (expenses/create-expense!
                     db
                     {:supplier_id (:id supplier-a)
                      :payer_id (:id payer)
                      :purchased_at (now)
                      :total_amount (bigdec "1.00")
                      :currency "BAM"}
                     [{:raw_label raw-label-a :line_total (bigdec "1.00")}])
          _exp-a-2 (expenses/create-expense!
                     db
                     {:supplier_id (:id supplier-a)
                      :payer_id (:id payer)
                      :purchased_at (now)
                      :total_amount (bigdec "1.00")
                      :currency "BAM"}
                     [{:raw_label raw-label-a :line_total (bigdec "1.00")}])
          _exp-b (expenses/create-expense!
                   db
                   {:supplier_id (:id supplier-b)
                    :payer_id (:id payer)
                    :purchased_at (now)
                    :total_amount (bigdec "1.00")
                    :currency "BAM"}
                   [{:raw_label raw-label-b :line_total (bigdec "1.00")}])
          filtered-by-supplier (aliases/list-unmapped-aliases
                                 db
                                 {:supplier-name supplier-a-name
                                  :limit 50
                                  :offset 0})
          filtered-by-normalized (aliases/list-unmapped-aliases
                                   db
                                   {:supplier-name supplier-b-name
                                    :raw-label-normalized "omega"
                                    :limit 50
                                    :offset 0})
          sorted-by-raw-label (aliases/list-unmapped-aliases
                                db
                                {:raw-label token
                                 :order-by :raw-label
                                 :order-dir :desc
                                 :limit 50
                                 :offset 0})
          sorted-by-occurrences (aliases/list-unmapped-aliases
                                  db
                                  {:raw-label token
                                   :order-by :occurrence-count
                                   :order-dir :desc
                                   :limit 50
                                   :offset 0})]
      (is (= [raw-label-a] (mapv :raw_label filtered-by-supplier)))
      (is (= [raw-label-b] (mapv :raw_label filtered-by-normalized)))
      (is (= [raw-label-b raw-label-a] (mapv :raw_label sorted-by-raw-label)))
      (is (= [2 1] (mapv :occurrence_count sorted-by-occurrences)))
      (is (= 1 (aliases/count-unmapped-aliases db {:supplier-name supplier-a-name})))
      (is (= 1 (aliases/count-unmapped-aliases db {:supplier-name supplier-b-name
                                                   :raw-label-normalized "omega"})))
      (is (= 1 (aliases/count-unmapped-aliases db {:raw-label token
                                                   :occurrence-count-min 2}))))))

(deftest articles-list-unmapped-aliases-supports-unit-filter
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "Unit Filter Supplier " (UUID/randomUUID)) {}))
          payer (th/create-payer! db {:type "cash" :label "Cash"})
          _kom-expense (expenses/create-expense!
                         db
                         {:supplier_id (:id supplier)
                          :payer_id (:id payer)
                          :purchased_at (now)
                          :total_amount (bigdec "1.00")
                          :currency "BAM"}
                         [{:raw_label "MILK"
                           :unit "kom"
                           :line_total (bigdec "1.00")}])
          _kg-expense (expenses/create-expense!
                        db
                        {:supplier_id (:id supplier)
                         :payer_id (:id payer)
                         :purchased_at (now)
                         :total_amount (bigdec "2.00")
                         :currency "BAM"}
                        [{:raw_label "MILK"
                          :unit "kg"
                          :line_total (bigdec "2.00")}])
          kg-rows (aliases/list-unmapped-aliases db {:supplier-id (:id supplier)
                                                     :unit "kg"
                                                     :limit 50
                                                     :offset 0})
          kom-rows (aliases/list-unmapped-aliases db {:supplier-id (:id supplier)
                                                      :unit "kom"
                                                      :limit 50
                                                      :offset 0})]
      (is (= ["kg"] (mapv :unit kg-rows)))
      (is (= ["kom"] (mapv :unit kom-rows)))
      (is (= 1 (aliases/count-unmapped-aliases db {:supplier-id (:id supplier)
                                                   :unit "kg"})))
      (is (= 1 (aliases/count-unmapped-aliases db {:supplier-id (:id supplier)
                                                   :unit "kom"}))))))

(deftest articles-list-related-records-resolves-alias-linked-expense-items
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "Related Alias Supplier " (UUID/randomUUID)) {}))
          payer (th/create-payer! db {:type "cash" :label "Cash"})
          store (stores/find-or-create-store!
                  db
                  {:supplier_id (:id supplier)
                   :display_name (str "Related Alias Store " (UUID/randomUUID))})
          receipt-id (insert-receipt! db {:original-filename "related-alias.pdf"})
          article (articles/create-article! db {:canonical_name (str "Related Alias Article " (UUID/randomUUID))})
          expense (expenses/create-expense!
                    db
                    {:supplier_id (:id supplier)
                     :store_id (:id store)
                     :receipt_id receipt-id
                     :payer_id (:id payer)
                     :purchased_at (now)
                     :total_amount (bigdec "4.20")
                     :currency "BAM"}
                    [{:raw_label "MILK" :line_total (bigdec "4.20")}])
          expense-item (-> expense :items first)
          alias-id (:alias_id expense-item)]
      (is (uuid? alias-id))
      (is (nil? (:article_id expense-item)))

      (aliases/map-alias-to-article! db alias-id (:id article))

      (let [related-expenses (articles/list-related-records db (:id article) {:type "expenses"})
            related-receipts (articles/list-related-records db (:id article) {:type "receipts"})
            related-stores (articles/list-related-records db (:id article) {:type "stores"})]
        (is (= [(:id expense)] (mapv :id related-expenses)))
        (is (= [receipt-id] (mapv :id related-receipts)))
        (is (= [(:id store)] (mapv :id related-stores)))))))

(deftest articles-list-related-records-resolves-receipts-and-stores-via-alias-supplier-links
  (when-let [db fixtures/*test-db*]
    (let [supplier (:supplier (suppliers/find-or-create-supplier! db (str "Related Alias Path Supplier " (UUID/randomUUID)) {}))
          store-a (stores/find-or-create-store!
                    db
                    {:supplier_id (:id supplier)
                     :display_name (str "Alias Path Store A " (UUID/randomUUID))})
          store-b (stores/find-or-create-store!
                    db
                    {:supplier_id (:id supplier)
                     :display_name (str "Alias Path Store B " (UUID/randomUUID))})
          article (articles/create-article! db {:canonical_name (str "Related Alias Path Article " (UUID/randomUUID))})
          _ (articles/create-alias! db (:id supplier) (str "ALIAS-PATH-" (UUID/randomUUID)) (:id article))
          supplier-alias (supplier-aliases/find-or-create-alias! db (str "Receipt Supplier Alias " (UUID/randomUUID)))
          _ (supplier-aliases/map-alias-to-supplier! db (:id supplier-alias) (:id supplier))
          store-alias (store-aliases/find-or-create-alias! db (str "Receipt Store Alias " (UUID/randomUUID)))
          _ (store-aliases/map-alias-to-store! db (:id store-alias) (:id store-b))
          receipt-via-supplier (insert-receipt!
                                 db
                                 {:original-filename "via-supplier-alias.pdf"
                                  :supplier-alias-id (:id supplier-alias)
                                  :created-at (java.time.Instant/parse "2026-01-01T10:00:00Z")})
          receipt-via-store (insert-receipt!
                              db
                              {:original-filename "via-store-alias.pdf"
                               :store-alias-id (:id store-alias)
                               :created-at (java.time.Instant/parse "2026-01-02T10:00:00Z")})
          related-expenses (articles/list-related-records db (:id article) {:type "expenses"})
          related-receipts (articles/list-related-records db (:id article) {:type "receipts"})
          related-stores (articles/list-related-records db (:id article) {:type "stores"})]
      (is (empty? related-expenses))
      (is (= [receipt-via-store receipt-via-supplier] (mapv :id related-receipts)))
      (is (every? #(= (:display_name supplier) %) (mapv :supplier_display_name related-receipts)))
      (is (= [(:id store-a) (:id store-b)] (mapv :id related-stores))))))

(deftest articles-list-related-records-supports-manufacturers-and-subcategories
  (when-let [db fixtures/*test-db*]
    (let [category-id (insert-category! db (str "Category " (UUID/randomUUID)))
          subcategory-id (insert-subcategory! db category-id (str "Subcategory " (UUID/randomUUID)))
          manufacturer-name (str "Manufacturer " (UUID/randomUUID))
          manufacturer-id (insert-manufacturer! db manufacturer-name (articles/normalize-article-key manufacturer-name))
          article (articles/create-article! db {:canonical_name (str "Article " (UUID/randomUUID))
                                                :manufacturer_id manufacturer-id
                                                :subcategory_id subcategory-id})
          related-manufacturers (articles/list-related-records db (:id article) {:type "manufacturers"})
          related-subcategories (articles/list-related-records db (:id article) {:type "subcategories"})]
      (is (= [manufacturer-id] (mapv :id related-manufacturers)))
      (is (= [subcategory-id] (mapv :id related-subcategories)))
      (is (= manufacturer-name (:display_name (first related-manufacturers))))
      (is (= category-id (:category_id (first related-subcategories)))))))

(deftest articles-list-related-records-invalid-type-includes-new-options
  (when-let [db fixtures/*test-db*]
    (let [article (articles/create-article! db {:canonical_name (str "Invalid Type Article " (UUID/randomUUID))})]
      (try
        (articles/list-related-records db (:id article) {:type "invalid"})
        (is false "Expected invalid type error")
        (catch clojure.lang.ExceptionInfo e
          (is (= 400 (:status (ex-data e))))
          (is (re-find #"manufacturers, subcategories" (.getMessage e))))))))
