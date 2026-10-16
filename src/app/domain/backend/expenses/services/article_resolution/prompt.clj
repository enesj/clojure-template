(ns app.domain.backend.expenses.services.article-resolution.prompt
  "System prompt, user message and response schema for the article resolver (pure).
  The system prompt must be byte-identical for identical inputs (prompt caching): everything
  is sorted and no timestamps or ids are rendered."
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]))

(defn- template
  []
  (slurp (io/resource "articles/resolver-system-prompt.txt")))

(defn- taxonomy-lines
  [category-index]
  (->> category-index
    (sort-by key)
    (map (fn [[category {:keys [subcategories]}]]
           (let [names (sort (map :name subcategories))]
             (str "- " category ": " (if (seq names) (str/join ", " names) "(none yet)")))))
    (str/join "\n")))

(defn- brand-mapping-lines
  [brand-parent-mappings]
  (->> brand-parent-mappings
    (group-by val)
    (sort-by key)
    (map (fn [[parent entries]]
           (str "- " parent ": " (str/join ", " (sort (map key entries))))))
    (str/join "\n")))

(def ^:private taxonomy-heading "\n## Categories and their existing subcategories\n")

(defn system-blocks
  "Render resources/articles/resolver-system-prompt.txt as [stable dynamic] text blocks, split at the
  taxonomy heading (the template keeps it last). The resolver adds subcategories as it runs, so the
  taxonomy goes in its own cached block: a change rewrites only that block, not the whole prompt.
  Deterministic for identical inputs."
  [category-index taxonomy]
  (let [text (-> (template)
               (str/replace "{{TAXONOMY}}" (taxonomy-lines category-index))
               (str/replace "{{BRAND_PARENT_MAPPINGS}}" (brand-mapping-lines (:brand-parent-mappings taxonomy)))
               (str/replace "{{SELF_NAMED_BRANDS}}" (str/join ", " (sort (:self-named-brands taxonomy)))))
        i    (str/index-of text taxonomy-heading)]
    (if i
      [(subs text 0 i) (subs text i)]
      [text])))

(defn system-prompt
  "The whole rendered system prompt as one string (see `system-blocks`)."
  [category-index taxonomy]
  (apply str (system-blocks category-index taxonomy)))

(defn user-message
  "JSON string: vector of {:id \"g1\" :label .. :unit .. :stores [..] :hint {..}|nil :candidates [{:key :name}]}.
  Ids are g1..gN in chunk order (short ids; never send UUIDs to the model)."
  [groups]
  (json/generate-string
    (vec (map-indexed
           (fn [i {:keys [raw-label unit stores hint candidates]}]
             (array-map "id" (str "g" (inc i))
               "label" raw-label
               "unit" unit
               "stores" (vec (sort (distinct (remove nil? stores))))
               "hint" (when hint
                        (array-map "category" (:category hint)
                          "subcategory" (:subcategory hint)
                          "manufacturer" (:manufacturer hint)))
               "candidates" (mapv (fn [{:keys [key name]}] (array-map "key" key "name" name))
                              candidates)))
           groups))))

(defn- nullable
  [schema]
  {:anyOf [schema {:type "null"}]})

(defn- base-response-schema
  [category-index]
  {:type "object"
   :additionalProperties false
   :required ["items"]
   :properties
   {:items
    {:type "array"
     :items
     {:type "object"
      :additionalProperties false
      :required ["id" "kind" "canonical_name" "category" "subcategory"
                 "manufacturer" "existing_article_key" "confidence" "reason"]
      :properties
      {:id {:type "string"}
       :kind {:type "string" :enum ["article" "noise" "unknown"]}
       :canonical_name (nullable {:type "string"})
       :category (nullable {:type "string" :enum (vec (sort (keys category-index)))})
       :subcategory (nullable {:type "string"})
       :manufacturer (nullable {:type "string"})
       :existing_article_key (nullable {:type "string"})
       :confidence {:type "integer"}
       :reason {:type "string"}}}}}})

(defn response-schema
  "JSON schema for the structured output. category enum = sorted category names from category-index.
  With `id-count`, `id` is restricted to the enum g1..g<id-count> so the model cannot invent placeholder
  ids (it did: \"_\", \"__skip__\"); pass the configured batch size, not the chunk size, to keep the
  schema - and so the prompt cache - stable. No minimum/maximum/minLength keys (rejected by structured
  outputs); additionalProperties false everywhere."
  ([category-index]
   (base-response-schema category-index))
  ([category-index id-count]
   (cond-> (base-response-schema category-index)
     id-count (assoc-in [:properties :items :items :properties :id]
                {:type "string" :enum (mapv #(str "g" %) (range 1 (inc id-count)))}))))
