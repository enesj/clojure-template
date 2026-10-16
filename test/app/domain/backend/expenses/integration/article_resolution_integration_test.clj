(ns app.domain.backend.expenses.integration.article-resolution-integration-test
  "DB-backed tests for apply-decision!. `apply-decision!` opens its own transaction; the fixture
  binds *nested-tx* to :ignore so the inner block joins the rollback-only outer transaction
  instead of committing and leaking rows."
  (:require
    [app.backend.fixtures :as fixtures]
    [app.domain.backend.expenses.services.article-resolution.catalog :as catalog]
    [app.domain.backend.expenses.services.article-resolution.config :as config]
    [app.domain.backend.expenses.services.article-resolution.core :as core]
    [app.domain.backend.expenses.services.service-configs.normalization :as cfg-norm]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [next.jdbc.transaction]))

(use-fixtures :each
  (fn [f]
    (binding [next.jdbc.transaction/*nested-tx* :ignore]
      (fixtures/with-transaction-rollback f))))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn- q [db sql-vec] (jdbc/execute! db sql-vec opts))
(defn- q1 [db sql-vec] (jdbc/execute-one! db sql-vec opts))

(def ^:private cfg (config/build-config {:article-resolver {:apply-mode :auto}}))

(defn- insert-category!
  [db]
  (let [id (random-uuid) name* (str "IT Kategorija " id)]
    (jdbc/execute-one! db ["INSERT INTO categories (id, name) VALUES (?, ?)" id name*])
    {:id id :name name*}))

(defn- insert-supplier!
  [db]
  (let [id (random-uuid)]
    (jdbc/execute-one! db ["INSERT INTO suppliers (id, display_name, normalized_key) VALUES (?, ?, ?)"
                           id (str "IT Supplier " id) (str "it-supplier-" id)])
    id))

(defn- insert-alias!
  [db supplier-id label-norm]
  (let [id (random-uuid)]
    (jdbc/execute-one! db ["INSERT INTO article_aliases (id, supplier_id, raw_label, raw_label_normalized, unit)
                            VALUES (?, ?, ?, ?, 'kom')" id supplier-id label-norm label-norm])
    id))

(defn- insert-resolution!
  [db alias-id]
  (let [id (random-uuid)]
    (jdbc/execute-one! db ["INSERT INTO article_alias_resolutions (id, alias_id, status, attempts)
                            VALUES (?, ?, 'processing'::alias_resolution_status, 1)" id alias-id])
    id))

(defn- group-of
  "Two suppliers, one alias + resolution row each, same normalized label + unit."
  [db label-norm]
  (let [aliases (mapv (fn [_] (insert-alias! db (insert-supplier! db) label-norm)) (range 2))]
    {:raw-label label-norm :raw-label-normalized label-norm :unit "kom"
     :alias-ids aliases
     :resolution-ids (mapv #(insert-resolution! db %) aliases)}))

(defn- decision
  [category & {:as overrides}]
  (merge {:action :create :canonical-name "Meggle mlijeko 2,8% 1L" :category (:name category)
          :subcategory "Mlijeko" :manufacturer "Meggle" :confidence 92}
    overrides))

(defn- count-of [db table where-sql & params]
  (:c (q1 db (into [(str "SELECT COUNT(*) c FROM " table " WHERE " where-sql)] params))))

(deftest apply-create-maps-group
  (when-let [db fixtures/*test-db*]
    (let [category (insert-category! db)
          group    (group-of db "it-mlijeko-meggle")
          result   (core/apply-decision! db cfg group (decision category)
                     {:source "llm" :proposal {:action "create"}})
          article  (q1 db ["SELECT * FROM articles WHERE id = ?" (:article-id result)])]
      (is (true? (:created? result)))
      (is (= "meggle-mlijeko-2-8-1l" (:normalized_key article)) "catalog key, not the backend key")
      (is (= "kom" (:unit article)))
      (let [sub (q1 db ["SELECT * FROM subcategories WHERE id = ?" (:subcategory_id article)])]
        (is (= "Mlijeko" (:name sub)))
        (is (nil? (:tenant_id sub)) "global subcategory")
        (is (= "article-resolver" (:created_by_subject_ref sub))))
      (is (= 2 (count-of db "article_aliases" "article_id = ?" (:id article))) "both aliases mapped")
      (is (= #{"resolved"}
            (set (map :status (q db ["SELECT status::text AS status FROM article_alias_resolutions WHERE alias_id IN (?, ?)"
                                     (first (:alias-ids group)) (second (:alias-ids group))]))))))))

(deftest apply-create-is-idempotent
  (when-let [db fixtures/*test-db*]
    (let [category (insert-category! db)
          g1 (group-of db "it-idem-a")
          g2 (group-of db "it-idem-b")
          r1 (core/apply-decision! db cfg g1 (decision category) {:source "llm"})
          arts (count-of db "articles" "normalized_key = 'meggle-mlijeko-2-8-1l'")
          subs (count-of db "subcategories" "category_id = ?" (:id category))
          r2 (core/apply-decision! db cfg g2 (decision category) {:source "llm"})]
      (is (= (:article-id r1) (:article-id r2)))
      (is (false? (:created? r2)))
      (is (= arts (count-of db "articles" "normalized_key = 'meggle-mlijeko-2-8-1l'")))
      (is (= subs (count-of db "subcategories" "category_id = ?" (:id category)))))))

(deftest manufacturer-reused-by-backend-key
  (when-let [db fixtures/*test-db*]
    (let [category (insert-category! db)
          bk (cfg-norm/normalize-manufacturer-key "L'Oréal")
          _  (jdbc/execute-one! db ["INSERT INTO manufacturers (id, display_name, normalized_key) VALUES (?, ?, ?)"
                                    (random-uuid) "L'Oreal Paris (legacy)" bk])
          before (count-of db "manufacturers" "normalized_key IN (?, ?)" bk "l-oreal")
          result (core/apply-decision! db cfg (group-of db "it-loreal")
                   (decision category :canonical-name "Elvital šampon 250ml" :manufacturer "L'Oréal")
                   {:source "llm"})
          article (q1 db ["SELECT manufacturer_id FROM articles WHERE id = ?" (:article-id result)])]
      (is (= 1 before))
      (is (= before (count-of db "manufacturers" "normalized_key IN (?, ?)" bk "l-oreal")) "no new manufacturer")
      (is (= bk (:normalized_key (q1 db ["SELECT normalized_key FROM manufacturers WHERE id = ?"
                                         (:manufacturer_id article)])))))))

(deftest backfill-expense-items
  (when-let [db fixtures/*test-db*]
    (let [category (insert-category! db)
          group    (group-of db "it-backfill")
          [alias-a alias-b] (:alias-ids group)
          other    (catalog/ensure-article! db {:canonical-name "IT Other article" :unit "kom"})
          tenant   (random-uuid) payer (random-uuid) expense (random-uuid)
          item-a   (random-uuid) item-b (random-uuid)]
      (jdbc/execute-one! db ["INSERT INTO tenants (id, name, slug) VALUES (?, 'IT tenant', ?)" tenant (str "it-" tenant)])
      (jdbc/execute-one! db ["INSERT INTO payers (id, tenant_id, label) VALUES (?, ?, 'IT payer')" payer tenant])
      (jdbc/execute-one! db ["INSERT INTO expenses (id, tenant_id, payer_id, purchased_at, total_amount)
                              VALUES (?, ?, ?, NOW(), 10)" expense tenant payer])
      (jdbc/execute-one! db ["INSERT INTO expense_items (id, tenant_id, expense_id, line_total, alias_id)
                              VALUES (?, ?, ?, 5, ?)" item-a tenant expense alias-a])
      (jdbc/execute-one! db ["INSERT INTO expense_items (id, tenant_id, expense_id, line_total, alias_id, article_id)
                              VALUES (?, ?, ?, 5, ?, ?)" item-b tenant expense alias-b (:id other)])
      (let [result (core/apply-decision! db cfg group (decision category) {:source "llm"})]
        (is (= (:article-id result)
              (:article_id (q1 db ["SELECT article_id FROM expense_items WHERE id = ?" item-a])))
          "item without an article is backfilled")
        (is (= (:id other)
              (:article_id (q1 db ["SELECT article_id FROM expense_items WHERE id = ?" item-b])))
          "item that already had an article is unchanged")))))

(defn- hold-for-review!
  "Turn the group's rows into needs_review/low-confidence rows carrying a stored proposal."
  [db group category confidence]
  (doseq [rid (:resolution-ids group)]
    (jdbc/execute-one! db
      [(str "UPDATE article_alias_resolutions SET status = 'needs_review'::alias_resolution_status,"
         " decision_reason = 'low-confidence', confidence = ?, proposal = ?::jsonb WHERE id = ?")
       confidence
       (str "{\"action\":\"create\",\"kind\":\"article\",\"canonical_name\":\"Sir čabrenjak " confidence "\","
         "\"category\":\"" (:name category) "\",\"subcategory\":\"Sir\",\"manufacturer\":null,"
         "\"existing_article_key\":null,\"llm_confidence\":" confidence ",\"reason\":\"Sir.\"}")
       rid])))

(deftest apply-stored-respects-threshold
  (when-let [db fixtures/*test-db*]
    (let [category   (insert-category! db)
          above      (group-of db "it-stored-above")
          below      (group-of db "it-stored-below")
          app-config {:article-resolver {:apply-mode :auto :auto-apply-threshold 70}}
          status-of  (fn [g] (set (map :status (q db ["SELECT status::text AS status, decision_reason FROM article_alias_resolutions WHERE id = ANY(?)"
                                                      (into-array java.util.UUID (:resolution-ids g))]))))]
      (hold-for-review! db above category 72)
      (hold-for-review! db below category 65)
      (testing "dry run writes nothing"
        (is (pos? (:applied (core/apply-stored! db app-config {:dry-run? true}))))
        (is (= #{"needs_review"} (status-of above))))
      (core/apply-stored! db app-config {})
      (is (= #{"resolved"} (status-of above)))
      (is (= #{"needs_review"} (status-of below)) "below the threshold stays in review")
      (is (= 1 (count-of db "articles" "canonical_name = 'Sir čabrenjak 72'")))
      (is (= #{"stored-proposal"}
            (set (map :decision_reason (q db ["SELECT decision_reason FROM article_alias_resolutions WHERE id = ANY(?)"
                                              (into-array java.util.UUID (:resolution-ids above))]))))))))

(deftest audit-marks-group-and-leaves-candidates
  (when-let [db fixtures/*test-db*]
    (let [category (insert-category! db)
          group    (group-of db "it-audit")
          ids      (set (:resolution-ids group))
          listed?  #(some (fn [r] (ids (:resolution_id r))) (core/audit-candidates db {:limit 1000}))]
      (hold-for-review! db group category 72)
      (core/apply-stored! db {:article-resolver {:apply-mode :auto :auto-apply-threshold 70}} {})
      (is (listed?) "resolved below 80 and unaudited: a candidate")
      (is (= 2 (core/mark-audited! db (first (:resolution-ids group)) {:verdict "ok" :note "fine"})))
      (is (not (listed?)) "audited groups drop out")
      (is (= #{"resolved"} (set (map :status (q db ["SELECT status::text AS status FROM article_alias_resolutions WHERE id = ANY(?)"
                                                    (into-array java.util.UUID ids)]))))
        "auditing never changes the status"))))

(deftest correct-edits-in-place-or-remaps
  (when-let [db fixtures/*test-db*]
    (let [category   (insert-category! db)
          app-config {:article-resolver {:apply-mode :auto :auto-apply-threshold 70}}
          resolve!   (fn [label name*]
                       (let [g (group-of db label)]
                         (core/apply-decision! db cfg g (decision category :canonical-name name*) {:source "llm"})
                         g))
          article-of (fn [g] (:article_id (q1 db ["SELECT article_id FROM article_aliases WHERE id = ?" (first (:alias-ids g))])))]
      (testing "sole user: article edited in place, group audited as fixed"
        (let [g   (resolve! "it-correct-a" "IT Krivo ime 1L")
              old (article-of g)
              r   (core/correct! db app-config (first (:resolution-ids g))
                    {:canonical_name "IT Ispravno ime 1L" :manufacturer nil})]
          (is (= :updated (:action r)))
          (is (= old (article-of g)))
          (is (= "IT Ispravno ime 1L" (:canonical_name (q1 db ["SELECT canonical_name FROM articles WHERE id = ?" old]))))
          (is (nil? (:manufacturer_id (q1 db ["SELECT manufacturer_id FROM articles WHERE id = ?" old]))))
          (core/correct! db app-config (first (:resolution-ids g)) {:manufacturer "Denkmit"})
          (is (= "dm" (:display_name (q1 db ["SELECT m.display_name FROM articles a JOIN manufacturers m ON m.id = a.manufacturer_id WHERE a.id = ?" old])))
            "manufacturer goes through brand-parent-mappings.edn")
          (is (= #{"fixed"} (set (map :audit_verdict (q db ["SELECT audit_verdict FROM article_alias_resolutions WHERE id = ANY(?)"
                                                            (into-array java.util.UUID (:resolution-ids g))])))))))
      (testing "shared article: only this group moves to a new article; the other keeps the old one"
        (let [g1  (resolve! "it-correct-b" "IT Dijeljeni 1L")
              g2  (resolve! "it-correct-c" "IT Dijeljeni 1L")
              old (article-of g1)
              r   (core/correct! db app-config (first (:resolution-ids g2)) {:canonical_name "IT Drugi proizvod 1L"})]
          (is (= :remapped (:action r)))
          (is (= old (article-of g1)))
          (is (= (:article-id r) (article-of g2)))
          (is (not= old (:article-id r)))))
      (testing "existing_article_key remaps; unknown key is a 400"
        (let [target (resolve! "it-correct-d" "IT Cilj 1L")
              g      (resolve! "it-correct-e" "IT Pogresno 1L")
              r      (core/correct! db app-config (first (:resolution-ids g)) {:existing_article_key "it-cilj-1l"})]
          (is (= (article-of target) (article-of g) (:article-id r)))
          (is (= 400 (:status (ex-data (try (core/correct! db app-config (first (:resolution-ids target))
                                              {:existing_article_key "nema-takvog"})
                                            (catch clojure.lang.ExceptionInfo e e)))))))))))
