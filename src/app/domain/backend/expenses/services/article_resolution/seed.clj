(ns app.domain.backend.expenses.services.article-resolution.seed
  "Dev helper: idempotently seed global subcategories from seed-subcategories.edn.
  Not a migration on purpose - migrations auto-run on prod."
  (:require
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(def ^:private seed-ref "article-resolver-seed")

(defn seed-subcategories!
  "Idempotently insert global subcategories from the taxonomy's :seed-subcategories map
  ({category [names]}). Returns {:inserted n :skipped-categories [names of missing categories]}."
  [db taxonomy]
  (let [opts {:builder-fn rs/as-unqualified-lower-maps}]
    (reduce
      (fn [acc [category-name sub-names]]
        (if-let [category-id (:id (jdbc/execute-one! db ["SELECT id FROM categories WHERE name = ?" category-name] opts))]
          (reduce
            (fn [acc* sub-name]
              (let [r (jdbc/execute-one!
                        db
                        [(str "INSERT INTO subcategories (id, category_id, tenant_id, name, description, is_active,"
                           " is_system_default, created_by_subject_ref, created_at, updated_at)"
                           " VALUES (?, ?, NULL, ?, NULL, TRUE, TRUE, ?, NOW(), NOW())"
                           " ON CONFLICT (category_id, name) WHERE tenant_id IS NULL DO NOTHING")
                         (java.util.UUID/randomUUID) category-id sub-name seed-ref])]
                (update acc* :inserted + (long (or (::jdbc/update-count r) 0)))))
            acc
            sub-names)
          (update acc :skipped-categories conj category-name)))
      {:inserted 0 :skipped-categories []}
      (sort-by key (:seed-subcategories taxonomy)))))

(comment
  (require '[app.domain.backend.expenses.services.article-resolution.taxonomy :as taxonomy])
  (seed-subcategories! (:database @system.state/state) (taxonomy/current))
  ;; => {:inserted 0 :skipped-categories []} on an already seeded database
  :rcf)
