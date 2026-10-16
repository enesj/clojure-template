#!/usr/bin/env clj

(ns scripts.bb.database.seed-categories
  (:require
    [aero.core :as aero]
    [clojure.string :as str]
    [next.jdbc :as jdbc])
  (:import
    [java.net URI]
    [java.util UUID]))

(def categories
  "General-purpose categories for article categorization.

  This list is intentionally small at the top level:
  - Max 5 food categories.
  - 31 non-food categories.

  Notes:
  - Uses `categories.name` as the stable identifier (unique index).
  - This script is idempotent: it will insert missing rows and refresh descriptions.
  - It also prunes categories not in this list, except `protected-categories`.
  - Before pruning, it remaps `subcategories.category_id` from legacy category names to the
    new consolidated names (so subcategories aren’t lost)."
  [;; Food (5)
   {:name "Svježe voće i povrće" :description "Svježe voće, povrće, začinsko bilje."}
   {:name "Mliječni proizvodi i jaja" :description "Mlijeko, sir, jogurt, maslac, jaja."}
   {:name "Meso, morski plodovi i delikatesi" :description "Meso, morski plodovi, suhomesnati proizvodi i srodni artikli."}
   {:name "Pekara i deserti" :description "Hljeb, peciva, kolači, deserti."}
   {:name "Pakovana hrana i pića" :description "Ostava, grickalice, smrznuta hrana, pića, kafa/čaj, alkohol."}

   ;; Non-food (31)
   {:name "Sredstva za čišćenje" :description "Hemikalije za čišćenje, deterdženti, dezinfekciona sredstva."}
   {:name "Papirna galanterija" :description "Papirni ubrusi, salvete, maramice, toaletni papir."}
   {:name "Jednokratno posuđe i pakovanje" :description "Posude za ponijeti, kese, folije, etikete."}
   {:name "Kuhinjske potrepštine" :description "Alu-folija, prijanjajuća folija, papir za pečenje, skladištenje hrane."}
   {:name "Sitni inventar i pribor" :description "Pribor za jelo, hvataljke, kutlače, alati, sitni inventar."}
   {:name "Oprema i uređaji" :description "Uređaji i oprema."}
   {:name "Održavanje i popravke" :description "Popravke, dijelovi, materijal za održavanje."}
   {:name "Kancelarijski materijal" :description "Pribor za pisanje, printeri i toner, administrativni artikli."}
   {:name "Kućne potrepštine" :description "Baterije, sijalice, opće kućne potrepštine."}
   {:name "Skladištenje i organizacija" :description "Kutije, police, organizatori, kontejneri."}
   {:name "Lična njega" :description "Toaletne potrepštine, higijena i njegu tijela."}
   {:name "Zdravlje i apoteka" :description "Lijekovi bez recepta i apotekarski proizvodi."}
   {:name "Proizvodi za bebe" :description "Pelene, maramice i artikli za njegu beba."}
   {:name "Oprema za kućne ljubimce" :description "Hrana za ljubimce, pijesak, njega i oprema."}
   {:name "Elektronika i dodaci" :description "Elektronika, kablovi, baterije, oprema."}
   {:name "Odjeća i modni dodaci" :description "Odjeća, obuća, modni dodaci."}
   {:name "Dom i vrt" :description "Uređenje doma, baštenski i vanjski artikli."}
   {:name "Željeznarija i alati" :description "Alati, željeznarija, vijci i srodna oprema."}
   {:name "Duhan i duvanski proizvodi" :description "Cigarete, duhan, e-cigarete i pribor za pušenje."}
   {:name "Restorani i kafići" :description "Hrana i pića posluženi u kafićima, restoranima, bistroima i ćevabdžinicama."}
   {:name "Gorivo i transport" :description "Gorivo (benzin, dizel, plin), autopraonica, parking, putarine, javni prevoz."}
   {:name "Knjige, novine i časopisi" :description "Knjige, dnevne novine, časopisi, stripovi."}
   {:name "Sport i rekreacija" :description "Sportska oprema i odjeća, članarine i ulaznice za teretane, bazene i sportske terene."}
   {:name "Kultura i zabava" :description "Kino, pozorište, koncerti, muzeji, događaji i igre na sreću."}
   {:name "Putovanja i smještaj" :description "Hoteli i smještaj, avio, autobuske i željezničke karte na duže relacije, turističke usluge."}
   {:name "Zdravstvene usluge" :description "Ljekarski pregledi, stomatolog, laboratorija, optika i terapije."}
   {:name "Obrazovanje" :description "Školarine, kursevi, udžbenici, vrtić i obrazovne usluge."}
   {:name "Pokloni i cvijeće" :description "Cvijeće, poklon paketi, poklon kartice, ukrasna ambalaža."}
   {:name "Takse i administrativne usluge" :description "Takse, pristojbe, bankarske i poštanske naknade, ovjere i administrativne usluge."}
   {:name "Igračke i igre" :description "Igračke, društvene igre i hobi artikli."}
   {:name "Ostalo" :description "Razni artikli koji se ne uklapaju u ostale kategorije."}])

(def subcategory-remaps
  "Legacy category names to consolidate into the new top-level categories.

  This is best-effort; missing legacy categories are ignored."
  [["Meat & Poultry" "Meso, morski plodovi i delikatesi"]
   ["Seafood" "Meso, morski plodovi i delikatesi"]
   ["Bakery" "Pekara i deserti"]

   ["Pantry Staples" "Pakovana hrana i pića"]
   ["Grains & Pasta" "Pakovana hrana i pića"]
   ["Canned & Jarred" "Pakovana hrana i pića"]
   ["Condiments & Sauces" "Pakovana hrana i pića"]
   ["Spices & Seasonings" "Pakovana hrana i pića"]
   ["Baking Supplies" "Pakovana hrana i pića"]
   ["Snacks" "Pakovana hrana i pića"]
   ["Frozen Foods" "Pakovana hrana i pića"]
   ["Beverages" "Pakovana hrana i pića"]
   ["Coffee & Tea" "Pakovana hrana i pića"]
   ["Alcohol" "Pakovana hrana i pića"]

   ;; Bootstrap remaps: English categories created before canonical seed was run
   ["Clothing & Accessories" "Odjeća i modni dodaci"]
   ["Services" "Ostalo"]
   ["Tobacco" "Duhan i duvanski proizvodi"]])

(def ^:const protected-categories
  "Categories that are never pruned because the guided expense workflows
   (utility/subscription power forms, tenant-managed subcategories) depend
   on them by name. Seeded by migration 0078_seed_utilities_subscriptions_taxonomy."
  #{"Utilities" "Subscriptions"})

(defn get-db-config [profile]
  (if (= profile :prod)
    (let [url (or (System/getenv "DATABASE_PUBLIC_URL")
                (System/getenv "DATABASE_URL"))]
      (when-not (some-> url str/trim not-empty)
        (throw (ex-info "DATABASE_PUBLIC_URL or DATABASE_URL required for prod" {})))
      (let [uri       (URI. (str/replace (str/trim url) #"^postgres://" "postgresql://"))
            user-info (.getUserInfo uri)
            [pg-user pg-pass] (when user-info (str/split user-info #":" 2))
            pg-port   (.getPort uri)]
        {:host     (.getHost uri)
         :port     (if (pos? pg-port) pg-port 5432)
         :dbname   (str/replace (or (.getPath uri) "/railway") #"^/" "")
         :user     pg-user
         :password pg-pass}))
    (-> (aero/read-config "config/base.edn" {:profile profile})
      :database)))

(defn datasource [{:keys [host port dbname user password]}]
  (jdbc/get-datasource {:dbtype "postgresql"
                        :host host
                        :port port
                        :dbname dbname
                        :user user
                        :password password}))

(defn- update-count [result]
  (or (:next.jdbc/update-count result)
    (:update-count result)
    0))

(defn- upsert-categories! [tx]
  (let [sql (str "insert into categories (id, name, description, created_at, updated_at) "
              "values (?, ?, ?, now(), now()) "
              "on conflict (name) do update "
              "  set description = excluded.description, "
              "      updated_at = now()")]
    (doseq [{:keys [name description]} categories]
      (jdbc/execute-one! tx [sql (UUID/randomUUID) name description]))))

(defn- remap-subcategories! [tx]
  (let [sql (str "update subcategories s "
              "   set category_id = c_to.id "
              "  from categories c_from, categories c_to "
              " where c_from.name = ? "
              "   and c_to.name = ? "
              "   and s.category_id = c_from.id")]
    (reduce
      (fn [acc [from-name to-name]]
        (+ acc (update-count (jdbc/execute-one! tx (into [sql] [from-name to-name])))))
      0
      subcategory-remaps)))

(defn- prune-categories! [tx seed-names]
  (let [keep-names (into seed-names protected-categories)
        placeholders (str/join ", " (repeat (count keep-names) "?"))
        sql (str "delete from categories where name not in (" placeholders ")")]
    (update-count (jdbc/execute-one! tx (into [sql] keep-names)))))

(defn seed-categories! [ds]
  (let [seed-names (mapv :name categories)]
    (jdbc/with-transaction [tx ds]
      (upsert-categories! tx)
      (let [remapped (remap-subcategories! tx)
            pruned (prune-categories! tx seed-names)]
        {:remapped-subcategories remapped
         :pruned-categories pruned}))))

(defn total-count [ds]
  (:total
   (jdbc/execute-one!
     ds
     ["select count(*)::int as total from categories"])))

(defn -main [& args]
  (let [env (or (first args) "dev")
        profile (keyword env)]
    (when-not (#{:dev :test :prod} profile)
      (println "❌ Invalid environment. Use: dev, test, or prod")
      (System/exit 1))
    (try
      (let [db (get-db-config profile)
            ds (datasource db)
            before (total-count ds)
            {:keys [remapped-subcategories pruned-categories]} (seed-categories! ds)
            after (total-count ds)]
        (when-not (= after (+ (count categories) (count protected-categories)))
          (throw (ex-info "Categories seed did not converge to the expected total" {:expected (+ (count categories) (count protected-categories))
                                                                                    :actual after})))
        (println "✅ Categories ensured:")
        (println "   Seeded set:          " (count categories))
        (println "   Remapped subcategories" remapped-subcategories)
        (println "   Pruned categories:    " pruned-categories)
        (println "   Total rows:           " after "(was" before ")")
        (println "   DB:                  " (str (:host db) ":" (:port db) "/" (:dbname db))))
      (catch Exception e
        (println "❌ Failed to seed categories:" (.getMessage e))
        (System/exit 1)))))

(apply -main *command-line-args*)
