---
name: audit-articles
description: "Periodic audit of articles the background resolver created with lower confidence: review unaudited groups, fix wrong articles in place or remap them, approve/reject the review queue, and stamp each group audited. No paid API calls."
disable-model-invocation: true
---
# audit-articles

The background resolver (`:article-resolver` in `config/base.edn`, dev only) auto-applies Claude proposals at
confidence ≥ `:auto-apply-threshold` (70) and leaves the rest in a review queue. This skill is the safety net:
it walks the groups nobody has checked yet, lowest confidence first, and fixes what is clearly wrong.

Run it periodically (for example weekly, or after a large receipt import) in a local Claude Code session with the
dev system running (nREPL port 7888). Remote routines cannot reach the local dev DB.

## Hard rules

- **No paid calls.** Never call `requeue!` / `requeue-group!` / `POST /:id/requeue`, `run-sweep!`, `/run` or
  `review/run-golden!`: they pay Claude to answer again. Every fix below is a plain DB write.
- **Never delete** articles, aliases, resolutions or other rows. A wrong article that is replaced stays in the
  catalog (unreferenced); that is fine.
- New **categories** only through `scripts/bb/database/seed_categories.clj` (ask the user first). Never create
  subcategories named Opste/Opšte/Ostalo/Razno/General.
- Do not edit `resources/articles/resolver-system-prompt.txt` or taxonomy files during the audit; collect
  suggestions and report them (see "Report").
- DB reads via postgres-mcp; writes only through the functions below (they keep aliases, expense items and
  resolution rows consistent).

## Tolerance — what counts as wrong

This is a household expense tracker, not accounting. Fix only what changes meaning or grouping:

| Fix | Leave as is (`ok`) |
|---|---|
| Wrong category or clearly wrong subcategory | Word order ("Barista kapsule" vs "Kapsule Barista") |
| Name describes a different product, flavour, variant or size than the label | Missing optional detail the label does not clearly state |
| Wrong manufacturer (a product word, store or unrelated company) | Manufacturer `null` when the brand is unclear |
| Brand, variant or detail the label does not show (even if past receipts always were that brand) | Generic name for a label that fits several products |
| Duplicate of an existing article (should be remapped) | Capitalization, `1L` vs `1l`, `20kom` vs `20 kom` |
| Garbage/noise mapped as a product | Debatable subcategory where both fit |

The category conventions are the rules in `resources/articles/resolver-system-prompt.txt` (served drinks →
Restorani i kafići, pita/burek → Pekara i deserti / Pecivo, fuel → Gorivo i transport / Gorivo, …). Brand →
parent mappings: `resources/articles/taxonomy/brand-parent-mappings.edn` and self-named brands
`self-named-brands.edn` are authoritative: do not "correct" a manufacturer that follows them (`correct!` and
`approve!` map brands through the table anyway); propose table changes in the report instead.

## Setup (REPL)

```clojure
(require '[app.domain.backend.expenses.services.article-resolution.core :as arc])
(def db (:database @system.state/state))
(def app-config (:config @system.state/state))
```

If `require` complains about an existing alias, pick another alias name (REPL quirk), or call the functions
fully qualified.

## Loop

### 1. Fetch a batch

```clojure
(arc/audit-candidates db {:max-confidence 80 :limit 30})
```

One row per (label, unit) group, lowest confidence first:
`:resolution_id :status :confidence :raw_label :unit :supplier :alias_count` plus, for resolved groups, the applied
article (`:canonical_name :category :subcategory :manufacturer`) and for `needs_review` groups the stored
`:proposal` JSON (`decision_reason` says why it was held back). Empty result → done, go to "Report".

To see existing articles that might be the same product (duplicates):

```sql
SELECT normalized_key, canonical_name, unit, similarity(normalized_key, '<catalog key>') s
FROM articles WHERE unit = '<unit>' AND normalized_key % '<catalog key>' ORDER BY s DESC LIMIT 5;
```

### 2. Decide per group

**Resolved groups** (`:status "resolved"`):

```clojure
;; right (within tolerance)
(arc/mark-audited! db rid {:verdict "ok"})

;; wrong fields -> fix; any subset of keys, snake_case strings or keywords
(arc/correct! db app-config rid {:canonical_name "Dukat sir feta 500g" :subcategory "Sir"})
(arc/correct! db app-config rid {:category "Pekara i deserti" :subcategory "Pecivo"})
(arc/correct! db app-config rid {:manufacturer nil})          ; clears the manufacturer
;; duplicate of an existing article -> remap the group to it
(arc/correct! db app-config rid {:existing_article_key "meggle-mlijeko-3-2-1l"})
```

`correct!` edits the article in place when only this group uses it; when the article is shared, or the corrected
name already exists, it finds/creates the corrected article and moves only this group. It stamps the group
audited with verdict `fixed`, so no separate `mark-audited!` is needed.

**Review-queue groups** (`:status "needs_review"`):

```clojure
;; proposal right -> apply it as is, then stamp
(arc/approve! db app-config rid {})
(arc/mark-audited! db rid {:verdict "ok"})

;; proposal salvageable -> apply with overrides, then stamp
(arc/approve! db app-config rid {:canonical_name "..." :category "..." :subcategory "..." :manufacturer "..."})
(arc/mark-audited! db rid {:verdict "fixed" :note "category"})

;; not a product / unusable (OCR garbage, merged lines) -> reject, then stamp
(arc/reject! db rid)
(arc/mark-audited! db rid {:verdict "rejected" :note "ocr garbage"})
```

`approve!` throws `{:status 400 :problems [...]}` when the proposal is invalid (e.g. unknown category); fix the
override and retry. A proposal with `"action": "noise"` is confirmed as noise by `approve!` with no overrides.
If you cannot tell what the product is, leave the group unaudited and list it in the report for the user.

### 3. Repeat

Work in batches of 30 until `audit-candidates` returns nothing or the user's time box is reached. Keep a running
tally per verdict.

## Report

End with:

1. Counts: groups audited, `ok` / `fixed` / `rejected`, left for the user (with labels).
2. Error rate on resolved groups by confidence band (70–74, 75–79). If a band is clean over two audits, suggest
   lowering `:auto-apply-threshold`; if it is above ~15% wrong, suggest raising it.
3. Recurring patterns as concrete suggestions: a prompt rule (one line), a `brand-parent-mappings.edn` entry, a
   missing subcategory for `seed-subcategories.edn`. The user decides; do not apply them during the audit.

## Progress queries

```sql
SELECT audit_verdict, COUNT(*) FROM article_alias_resolutions WHERE audited_at IS NOT NULL GROUP BY 1;
SELECT COUNT(*) FROM article_alias_resolutions
WHERE audited_at IS NULL AND ((status = 'resolved' AND source = 'llm' AND confidence < 80) OR status = 'needs_review');
```

Same operations over HTTP (admin session required): `GET /admin/api/expenses/article-resolutions/audit-candidates`,
`POST /:id/correct`, `POST /:id/approve`, `POST /:id/reject`, `POST /:id/audited`.
