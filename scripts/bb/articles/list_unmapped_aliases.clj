#!/usr/bin/env bb

(ns scripts.bb.articles.list-unmapped-aliases
  (:require
    [articles.db :as db]))

(defn- usage
  ([] (usage nil))
  ([msg]
   (when msg
     (binding [*out* *err*]
       (println msg)
       (println "")))
   (println "List article_aliases that are not mapped to an article (article_id IS NULL).")
   (println "")
   (println "Usage:")
   (println "  bb scripts/bb/articles/list_unmapped_aliases.clj [dev|test] [--limit N] [--pretty] [--all]")
   (println "")
   (println "Notes:")
   (println "  - Default output is EDN (vector of maps).")
   (println "  - Use --all to include mapped aliases too (removes the WHERE clause).")))

(defn- parse-args
  [args]
  (loop [args args
         parsed {:limit 200
                 :pretty? false
                 :all? false}]
    (let [[a b & more] args]
      (cond
        (nil? a) parsed

        (or (= a "--help") (= a "-h"))
        (do (usage) (System/exit 0))

        (= a "--pretty")
        (recur (cons b more) (assoc parsed :pretty? true))

        (= a "--all")
        (recur (cons b more) (assoc parsed :all? true))

        (= a "--limit")
        (let [n (db/parse-long b)]
          (when-not n
            (usage (str "Invalid --limit: " b))
            (System/exit 1))
          (recur more (assoc parsed :limit n)))

        :else
        (do
          (usage (str "Unknown arg: " a))
          (System/exit 1))))))

(defn -main
  [& args]
  (let [{:keys [profile args]} (db/parse-profile args)
        {:keys [limit pretty? all?]} (parse-args args)
        config (db/read-config profile)
        db (:database config)
        sql (str
              "SELECT\n"
              "  aa.id AS alias_id,\n"
              "  aa.raw_label,\n"
              "  aa.raw_label_normalized,\n"
              "  aa.supplier_id,\n"
              "  s.display_name AS supplier\n"
              "FROM article_aliases aa\n"
              "LEFT JOIN suppliers s ON s.id = aa.supplier_id\n"
              (when-not all? (str "WHERE aa.article_id IS NULL AND " db/not-settled-noise-sql "\n"))
              "ORDER BY s.display_name NULLS LAST, aa.raw_label\n"
              "LIMIT " limit)
        rows (db/query db sql)]
    (if pretty?
      (db/pprint-edn rows)
      (db/prn-edn rows))))

(apply -main *command-line-args*)
