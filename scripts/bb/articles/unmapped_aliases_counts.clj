#!/usr/bin/env bb

(ns scripts.bb.articles.unmapped-aliases-counts
  (:require
    [articles.db :as db]))

(defn- usage
  ([] (usage nil))
  ([msg]
   (when msg
     (binding [*out* *err*]
       (println msg)
       (println "")))
   (println "List unmapped article aliases grouped by supplier with occurrence counts.")
   (println "")
   (println "Usage:")
   (println "  bb scripts/bb/articles/unmapped_aliases_counts.clj [dev|test] [--limit N] [--pretty]")
   (println "")
   (println "Output:")
   (println "  EDN vector of {:raw_label .. :supplier .. :occurrence_count ..}.")))

(defn- parse-args
  [args]
  (loop [args args
         parsed {:limit 200
                 :pretty? false}]
    (let [[a b & more] args]
      (cond
        (nil? a) parsed

        (or (= a "--help") (= a "-h"))
        (do (usage) (System/exit 0))

        (= a "--pretty")
        (recur (cons b more) (assoc parsed :pretty? true))

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
        {:keys [limit pretty?]} (parse-args args)
        config (db/read-config profile)
        db (:database config)
        sql (str
              "SELECT\n"
              "  aa.raw_label,\n"
              "  s.display_name AS supplier,\n"
              "  COUNT(*) AS occurrence_count\n"
              "FROM article_aliases aa\n"
              "LEFT JOIN suppliers s ON s.id = aa.supplier_id\n"
              "WHERE aa.article_id IS NULL AND " db/not-settled-noise-sql "\n"
              "GROUP BY aa.raw_label, s.display_name\n"
              "ORDER BY occurrence_count DESC, aa.raw_label\n"
              "LIMIT " limit)
        rows (db/query db sql)]
    (if pretty?
      (db/pprint-edn rows)
      (db/prn-edn rows))))

(apply -main *command-line-args*)
