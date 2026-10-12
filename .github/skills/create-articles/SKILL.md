---
name: create-articles
description: "Map OCR article aliases using batch local heuristics plus Perplexity research, review, create_articles, map_aliases, and dedicated manufacturers handoff"
disable-model-invocation: true
---
# create-articles

Map raw `article_aliases` (where `article_id IS NULL`) to canonical `articles` using
**batch local heuristics + Perplexity web research** instead of per-alias Serper searches.

The `articles_research.clj` script proposes articles + mappings automatically. The agent
**reviews and corrects** instead of authoring from scratch.

---

## Pre-phase: Resolve Review-Required Receipts

Run this **before** touching the article alias backlog.

> **Resuming across context boundaries?** Re-verify state before acting:
> ```bash
> bb list-review-required-receipts dev --pretty   # should be []
> bb scripts/bb/articles/unmapped_aliases_counts.clj dev --pretty
> ```

### Step 1 — Check how many receipts need attention

```bash
bb list-review-required-receipts dev --pretty
```

If the output is **empty (`[]`)**, skip to Phase 0.

### Step 2 — Re-run OCR via REPL (uses `run-by-ids!`)

`run-by-ids!` resets each receipt to `uploaded` (clearing OCR fields and errors)
then re-runs the full pipeline with `:force-refine? true`.

```clojure
;; In the connected nREPL (port 7888, backend namespace)
(require '[app.domain.backend.expenses.workers.receipt-ocr.runner :as runner])

(let [db         (:database @system.state/state)
			app-config (:config   @system.state/state)
			ids        (mapv :id
									 (next.jdbc/execute!
										 db
										 ["SELECT id FROM receipts WHERE status = 'review_required'"]
										 {:builder-fn next.jdbc.result-set/as-unqualified-lower-maps}))]
	(if (seq ids)
		(runner/run-by-ids! db app-config ids)
		{:skipped true :reason "no review_required receipts"}))
```

### Step 3 — Check remaining review-required

```bash
bb list-review-required-receipts dev --pretty
bb list-review-required-receipts dev --full --pretty  # verbose, for mismatch diagnosis
```

| Root cause | Signal | Fix |
|-----------|--------|-----|
| **Lines-total mismatch** | `result :ok` + items sum != total | See Step 4 |
| **Undefined supplier** | supplier_guess nil / "unknown" | Manual UI review |
| **Bad extraction** | nil total, 0 items | Manual UI review |

### Step 4 — Fix lines-total mismatch (no re-OCR needed)

```clojure
(require '[app.domain.backend.expenses.services.receipts.approval :as approval])
(try
	(let [result
				(approval/save-review!
					(:database @system.state/state)
					<receipt-uuid>
					{:supplier_id  <supplier-uuid>
					 :purchased_at "2026-01-07T11:48:00Z"   ; ISO string, NOT #inst
					 :total_amount 61.44
					 :currency     "BAM"
					 :items        [{:raw-label "CRP" :qty 1 :unit-price 11.44 :line-total 11.44}
													{:raw-label "Ostale usluge" :qty 1 :unit-price 50.00 :line-total 50.00}]})]
		(select-keys result [:id :status]))
	(catch Exception e {:error (ex-message e) :data (ex-data e)}))
```

---

## Response size rules (non-negotiable)

- **Never reprint file contents** — after reading any `tmp/` file, output only a short prose summary.
- **Tool output: summarise, don't repeat** — describe results in 1-5 bullet points.
- **Per-phase summaries only** — concise status lines, not alias-by-alias listings.
- **Article/mapping tables** — compact Markdown table, max 30 rows visible.

---

## Hard rules (non-negotiable)

- **Do not create new categories** — pick from the existing `categories` table only.
- **Do not use `psql` directly** — scripts call it internally; run via `bb`.
- **Subcategory names must be in Bosnian.**
- **Reuse existing DB subcategory wording whenever an equivalent already exists.** Never create ASCII, diacritic-stripped, or lightly reworded variants of an existing Bosnian subcategory.
- **Each distinct size/volume/weight = a separate article.** Never conflate variants.
- **Batch-first**: no alias-by-alias loops. Use batch scripts and files.
- **Temporary files** go under `tmp/`; delete them after use (`bb clear-folder`).

---

## Key normalization reference

The `db/normalize-key` function (`scripts/bb/articles/db.clj:104-123`) determines how
canonical names become article keys in the database.

**Algorithm**: NFD decomposition → strip combining marks → lowercase → non-alnum to hyphens → trim/collapse

**Diacritic transliteration** (NFD combining-mark removal):

| Input | Output | Example |
|-------|--------|---------|
| Š/š | S/s | Šećer → secer |
| Č/č | C/c | Čaj → caj |
| Ž/ž | Z/z | Žito → zito |
| Đ/đ | D/d | Đevđelija → devdelija |
| Ć/ć | C/c | Ćevapi → cevapi |

> **CRITICAL: Never write your own `normalize-key`.** Always use the output of
> `create_articles.clj --dry-run` or query `SELECT normalized_key FROM articles`
> to get actual keys. The NFD decomposition has subtle edge cases that a naive
> regex replacement will get wrong.

**Common Bosnian manufacturer patterns** (for manual review of heuristic output):

| Pattern in raw label | Manufacturer | Notes |
| -------------------- | ------------ | ----- |
| Meggle | Meggle | Dairy |
| Dukat | Dukat | Dairy |
| Vindija, Zelene Doline | Vindija | Dairy |
| Zbregov | Zbregov | Dairy |
| Violeta, VIOLETA, VIOLET | Violeta | Tissue/paper goods; normalize OCR-shortened `VIOLET` to `Violeta` |
| Fructal | Fructal | Juice / soft drinks |
| Pfanner | Pfanner | Juice / soft drinks |
| Frosch | Frosch | Cleaning products |
| Pharmamed | Pharmamed | Pharmacy / health products |
| Herbifit | Herbifit | Pharmacy / health products |
| LOVITA, Lovita | LOVITA | Biscuits / confectionery |
| Rondini | Rondini | Biscuits / confectionery |
| Milkos | Milkos | Dairy (local) |
| Zlatna Džezva | Vispak | Coffee |
| Zvijezda | Podravka | Condiments/oil |
| Argeta | Atlantic Grupa | Pâté |
| Dunhill, DUNHIL | BAT | Tobacco |
| Nescafé, Nescafe, NESTLE | Nestlé | Coffee |
| Dolcela | Podravka | Baking |
| Balea, Alverde | dm | Drugstore brands |
| Nivea | Beiersdorf | Personal care |
| Dove, Axe, Domestos | Unilever | Personal care/cleaning |
| Haribo, HARIBO | Haribo | Confectionery |
| Roshen, ROSHE | Roshen | Confectionery |
| Aspirin, Bayer | Bayer | Pharma |
| Voltaren | GSK / Novartis | Pharma |
| Babybel, Leerdammer | Bel | Dairy/cheese |
| Zanetti | Zanetti | Dairy/cheese |
| Profissimo, Mivolis | dm | dm-exclusive house brands (like Balea/Alverde) |
| Milka, MILKA, MILKA CHOCO | Mondelez | Confectionery |
| Sensodyne | Haleon | Formerly GSK Consumer Healthcare (2022 spinoff) |

**Manufacturer anti-patterns** (do **not** accept as manufacturer without strong evidence):

| Token / pattern | Why it is risky | Default action |
| --------------- | --------------- | -------------- |
| Intenso, Classic, Soft, Blue, Prženi, Forte, Espresso | Usually product line / roast / descriptor, not manufacturer | Keep manufacturer nil unless a known brand in the same label resolves it |
| Product type words (`mileram`, `jogurt`, `sir`, `kafa`, `šećer`) | Common product nouns or category words | Keep manufacturer nil unless a known brand is present |
| OCR-shortened variants of known brands | Can create duplicate manufacturer rows (`VIOLET` vs `Violeta`) | Normalize to the canonical known brand name |

---

## Phase 0: Quick overview

**Ground-truth progress metric** (use this between phases, not the grouped script count):
```sql
SELECT COUNT(*) FILTER (WHERE article_id IS NULL) AS unmapped,
			 COUNT(*) AS total
FROM article_aliases;
```

**Backlog composition** (for understanding what needs work, not for progress tracking):
```bash
bb scripts/bb/articles/unmapped_aliases_counts.clj dev --pretty
```

> **Why two metrics?** The script groups by `(raw_label, supplier)` — resolving one
> cross-supplier article can map dozens of aliases but barely change the grouped count.
> The SQL count tracks individual alias rows, which is the true measure of progress.

---

## Phase 1: Batch research (replaces old Phases 0-3)

This is the core improvement. One script does: cross-supplier dedup, OCR noise detection,
local heuristic resolution (brand patterns, supplier context, product keywords),
and optional Perplexity batched web research.

### Step 1a — Local heuristics only (recommended first pass)

```bash
bb articles-research dev --skip-research --pretty
```

This produces:
- `tmp/articles-suggested.edn` — articles from locally-resolved aliases
- `tmp/mappings-suggested.edn` — alias→article mappings
- `tmp/noise-candidates.edn` — OCR noise for review
- `tmp/needs-research.edn` — aliases that need web research
- `tmp/research-summary.edn` — stats

`articles-research` preserves alias `:unit` values in both suggested articles and mappings.
Treat article identity as `normalized_key + unit`; only assume `kom` when the alias truly has no unit.

Read the summary and `tmp/needs-research.edn` to understand what local heuristics missed.

### Step 1b — Full research (with Perplexity)

```bash
bb articles-research dev --pretty
```

This runs everything from 1a plus sends unresolved aliases to Perplexity in batches of 15.
Requires `PERPLEXITY_API_KEY` in `.env`.

**Supplier filter** — process one supplier at a time for large backlogs:
```bash
bb articles-research dev --supplier "AMKO" --output-prefix amko --pretty
bb articles-research dev --supplier "APOTEKE" --output-prefix apoteke --pretty
```

`--output-prefix` prevents file overwrites between batches: output goes to
`tmp/amko-articles-suggested.edn`, `tmp/amko-mappings-suggested.edn`, etc.

> **File overwrite warning:** Each research run overwrites `tmp/articles-suggested.edn`
> and `tmp/mappings-suggested.edn`. When processing multiple suppliers sequentially,
> **complete the full research → review → create → map → manufacturers cycle for each supplier batch** before
> running the next. The workflow per supplier is:
> 1. `bb articles-research dev --supplier "X" --pretty`
> 2. Review + correct `tmp/articles-suggested.edn` and `tmp/mappings-suggested.edn` (Step 1c quality review)
> 3. Create articles (Phase 3a)
> 4. Map aliases (Phase 3b)
> 5. Run `/manufacturers` as the primary manufacturer pass (Step 3d, mandatory)
> 6. Then proceed to next supplier

### Step 1c — Quality review (single pass over both files)

Read `tmp/articles-suggested.edn` and `tmp/mappings-suggested.edn` once. Apply **all**
checks below in a single editing pass. Do not re-read the files between checks.

> **DB context (fetch once, reuse):** Before starting the checklist, run one query to get
> the full category→subcategory taxonomy. Keep the result in working memory for all checks:
> ```sql
> SELECT c.name AS category, array_agg(s.name ORDER BY s.name) AS subcategories
> FROM categories c
> LEFT JOIN subcategories s ON s.category_id = c.id
> GROUP BY c.name ORDER BY c.name;
> ```

#### A. Names — scan `:canonical-name` values

| Check | Pattern | Fix |
|-------|---------|-----|
| All-lowercase | Entire name lowercase (`"indijaner"`) | Capitalize first word |
| Double-letter typo | Doubled vowel not in raw label (`"Foliija"`, `"Tjeestenina"`) | Remove extra letter |
| Fragmented words (Perplexity only) | Short 2–4 char fragments after space (`"Bombo ni"`, `"Det erđent"`) | Rejoin: `"Bomboni"`, `"Deterdžent"` |
| OCR merge artifact | Two product names joined by hex token (`"PRODUCT 2f5 PRODUCT"`) | Delete from articles + mappings; schedule alias for noise deletion |
| Trailing hex/numbers | `[0-9a-f]{3,4}` at end, not a weight unit | Remove suffix |

#### B. Manufacturers — keep only obvious high-confidence fixes in `:manufacturer-name`

The dedicated `/manufacturers` workflow is now the **primary** way to create/reuse manufacturers
and wire articles after mapping. During `tmp/articles-suggested.edn` review, only fix clear,
high-confidence mistakes that would otherwise carry obviously wrong data forward.

| Check | Pattern | Fix |
|-------|---------|-----|
| nil/Generic for branded product | Name contains known brand (see manufacturer table above) | Assign correct manufacturer |
| Short OCR fragment | ≤ 2 chars, all-caps no-vowel fragments | Set nil |
| Truncated from raw label | Value appears verbatim in raw label (`"DUNHIL"`, `"HARIBO BE"`) | Resolve to canonical manufacturer name |
| Product attribute as mfr | `"BIOFIT"`, `"LIGHT"`, `"CLASSIC"`, `"RINFU"` | Set nil |
| Brand visible anywhere in label but mfr=nil | Brand appears at start, middle, or end (`"Violeta maramice u"`, `"... U KUTIJI VIOLETA"`) | Assign canonical manufacturer; do **not** leave nil |
| OCR-shortened brand variant | `"VIOLET"`, `"PADJENI"`-style near-matches | Normalize to canonical brand/company (`Violeta`, `Mljekara Pađeni`, ...) |
| Descriptor mistaken for brand | `"INTENSO"`, `"SOFT"`, `"ESPRESSO"`, `"FORTE"`, `"PRŽENI"` | Keep nil unless another trusted brand token resolves the manufacturer |

Do not do exhaustive manufacturer discovery here. If a label is plausible but not obvious,
leave it for `/manufacturers` after Phase 3.

> **Whole-label rule (critical):** A recognizable brand can appear at the **beginning, middle, or end** of the raw label. Do not decide `:manufacturer-name nil` until you have scanned the full raw label, not just the first token.

> **Regression lesson:** Prompt tightening fixed obvious branded-null rows like `Violeta maramice u` and `PAPIRNE MARAMICE 60-1 U KUTIJI VIOLETA`, but ambiguous descriptor-led rows like `KAPSULE INTENSO...` and `KIKIRIKI GUD PRZENI...` still stayed nil. Treat that split as expected behavior: fix obvious known-brand misses aggressively, but do **not** force a manufacturer onto descriptor-only labels without stronger evidence.

> **New brand→manufacturer discovered during review?** Add it to the appropriate taxonomy file:
> - `scripts/bb/articles/taxonomy/brand-parent-mappings.edn` — brand→parent-company map
>   (when brand name differs from manufacturer, e.g. `"Milka" "Mondelez International"`)
> - `scripts/bb/articles/taxonomy/self-named-brands.edn` — vector of brands where brand = manufacturer
>   (e.g. `"Meggle"`, `"Haribo"`)
>
> These are injected into the Perplexity system prompt via `{{BRAND_MAPPINGS}}`
> so future research runs will get them right automatically.

#### C. Categories & subcategories — validate against DB taxonomy (fetched above)

1. Every category must exist in DB. **Never create new top-level categories.**
2. Subcategories: prefer existing DB match. Accept auto-create only if it's a legitimate
	 Bosnian-language name and the category exists.
3. Diacritics: verify exact string match (Ž not Z, Š not S, Č not C, Ć not C, Đ not D).
4. Language: all subcategories must be Bosnian (not English).
5. Proliferation: watch for near-duplicates (`"Maramice"` vs `"Maramice i papirni proizvodi"`) — pick DB match.
6. Equivalent wording: if DB already has the same subcategory with Bosnian diacritics or an obviously equivalent spelling, reuse the exact DB wording instead of introducing a new variant.

**Hard classification rules** (Perplexity commonly gets these wrong):

| Context | Category | Subcategory | Common mistake |
|---------|----------|-------------|----------------|
| Carbonated soft drinks (Coca-Cola, Pepsi, Sprite, Fanta) | Pakovana hrana i pića | Bezalkoholna pića | ~~Voćni sokovi~~ |
| Plastic/carrier bags (vreća, tregerica, kesa PE) | Jednokratno posuđe i pakovanje | Kese | ~~Ostalo/Kese i vreće~~ |
| Beauty/salon services (laminiranje, farbanje, waxing) | Lična njega | Usluge ljepote | ~~Ostalo/Usluge~~ |
| Popcorn (kokičar, kokice) | Pakovana hrana i pića | Grickalice | ~~Mliječni proizvodi/Maslac~~ |
| Carob, vanilla sugar, baking additives (rogač, vanilin šećer) | Pakovana hrana i pića | Dodaci za pečenje | ~~Žitarice i tjestenina~~ |

#### D. Duplicates — within-batch + cross-batch

1. **Within-batch**: same product from different suppliers (e.g. "Kefa Potrosacka" vs
	 "Kesa Potrosacka") → merge into one article, redirect mappings.
2. **Cross-batch (multi-batch sessions only)**: check recently created articles:
	 ```sql
	 SELECT canonical_name, normalized_key FROM articles
	 ORDER BY created_at DESC LIMIT 50;
	 ```
	 For any semantic match (same product, different name), remove the proposed article
	 and redirect its mappings to the existing `normalized_key`.
3. **Variant risk**: different sizes must be separate articles. If >20 unmapped aliases,
	 also run `bb group-aliases-by-brand dev --min-group 2` and check `VARIANT RISK` clusters.
4. **Fresh-produce merge**: aliases like `LIMUN /KG` and `SVJEZI LIMUN` are the same
	 product — keep one, redirect mappings for the other.

#### E. Noise cleanup in mappings

For every noise alias identified above (OCR artifacts, confirmed noise):
1. Remove its entry from `tmp/mappings-suggested.edn`.
2. Add its `raw-label` to the noise deletion list for Phase 4.

This prevents `map_aliases.clj` from failing on missing articles.

**Edit both `.edn` files directly.** After all fixes, proceed to Phase 3.

---

## Phase 3: Create articles + map aliases

### Step 3a — Dry-run + create

```bash
bb scripts/bb/articles/create_articles.clj dev \
	--articles-file tmp/articles-suggested.edn \
	--dry-run --pretty | tee tmp/articles-planned.edn
```

If any article shows `already_exists: true`:
1. Remove it from `tmp/articles-suggested.edn`.
2. In `tmp/mappings-suggested.edn`, redirect its mappings to the existing `normalized_key`.
3. Re-run dry-run until 0 conflicts.

Then create for real:
```bash
bb scripts/bb/articles/create_articles.clj dev \
	--articles-file tmp/articles-suggested.edn \
	--pretty | tee tmp/created-articles.edn
```

### Step 3b — Map aliases

```bash
bb scripts/bb/articles/map_aliases.clj dev \
	--mappings-file tmp/mappings-suggested.edn \
	--skip-mapped --pretty | tee tmp/mapped.edn
```

> Always use `--skip-mapped` — it's safe for single and multi-batch sessions.
> For large backlogs (>150 mappings), split into batches of ~80.

### Step 3c — Manual corrections (if needed)

For aliases the script missed or got wrong, write `tmp/mappings-extra.edn`:

```clojure
[{:alias-id "uuid-1"  :article-key "meggle-mlijeko-1l"}
 {:alias-id "uuid-2"  :article-key "coca-cola-125l"}]
```

```bash
bb scripts/bb/articles/map_aliases.clj dev \
	--mappings-file tmp/mappings-extra.edn --pretty
```

### Step 3d — Run the dedicated manufacturers workflow (mandatory)

After creating articles and mapping aliases, hand off manufacturer creation/reuse/wiring to
the dedicated manufacturers workflow:

```text
/manufacturers
```

That command is now the **primary** manufacturer solution for this job. It handles:

1. surveying articles still missing `manufacturer_id`
2. reusing existing manufacturers when possible
3. creating new manufacturer rows when needed
4. wiring articles to the correct manufacturer with its own review gates

Use the obvious fixes you made in `tmp/articles-suggested.edn` as a head start, but do not
try to replicate the full manufacturers workflow inside `create-articles`.

---

## Phase 4: Finalize

### Noise deletion

Review `tmp/noise-candidates.edn` plus aliases flagged during Phase 2 review:

```bash
# Dry-run first
bb scripts/bb/articles/delete_unmapped_aliases.clj dev \
	--raw-label "0 ML 4f92" --raw-label "----" --pretty

# Apply after confirming
bb scripts/bb/articles/delete_unmapped_aliases.clj dev \
	--raw-label "0 ML 4f92" --raw-label "----" --apply --yes --pretty
```

### Remaining unmapped

```bash
bb scripts/bb/articles/unmapped_aliases_counts.clj dev --pretty
```

Classify: **Mappable** → re-run research with `--supplier` filter | **Noise** → delete | **Ambiguous** → document.

### Verify coverage

```bash
bb scripts/bb/articles/report_progress.clj dev --pretty | tee tmp/progress-report.edn
```

Check: coverage %, `Ostalo` slice (<20%), Generic manufacturer (<30% of branded products).

### Prompt improvement (conditional — only after Perplexity runs)

If you fixed the same class of mistake on **≥ 2 articles**, update the appropriate file:

| Mistake class | File to update |
|--------------|----------------|
| Brand→manufacturer missing/wrong | `scripts/bb/articles/taxonomy/brand-parent-mappings.edn` (brand→parent map) |
| Brand = manufacturer not recognized | `scripts/bb/articles/taxonomy/self-named-brands.edn` (add brand name) |
| Category/subcategory wrong | Hard classification rules in this file |
| Prompt wording flaw | `scripts/bb/articles/perplexity-system-prompt.txt` |

When improving the Perplexity prompt for manufacturer misses, prefer these rules:

- force whole-label scanning before allowing `mfr = null`
- treat supplied brand tables as authoritative constraints
- normalize OCR-near matches to canonical brands (`VIOLET` → `Violeta`)
- require an explicit checklist before allowing `mfr = null`
- explicitly warn that descriptor words like `Intenso`, `Classic`, `Soft`, `Forte`, `Espresso`, `Prženi` are not manufacturers by default

The brand mapping files are loaded at research time and injected into the Perplexity
system prompt via `{{BRAND_MAPPINGS}}`. Editing them improves future runs automatically.

Also record any new hard classification rules discovered during Phase 2 by adding rows
to the tables in this file.

---

## Completion gate

- [ ] Pre-phase done (review_required receipts resolved or documented).
- [ ] Articles created + aliases mapped for requested backlog slice.
- [ ] Dedicated manufacturers workflow run (Step 3d) — obvious known-brand misses handled or documented.
- [ ] No variant/size conflation. No subcategory named `"General"`.
- [ ] `Generic` ≤ ~30% of branded-product articles; `Ostalo` < ~20%.
- [ ] Progress verified via `report_progress.clj`.
- [ ] Remaining unmapped documented (noise vs ambiguity).
- [ ] Prompt files updated if recurring patterns found (or stated "no changes needed").

---

## Output contract

Report after each run:

1. **Receipt resolution** — review_required count, resolved vs stuck.
2. **Summary counts** — articles created, aliases mapped, subcategories added.
3. **EDN corrections** — grouped by type:
	 - *Manufacturers*: `"DUNHIL"→BAT`, `"HARIBO BE"→Haribo`, …
	 - *Names*: `"Bombo ne"→"Bomboni"`, `"Foliija"→"Folija"`, …
	 - *Categories*: Profissimo Salvete: Sredstva za čišćenje → Papirna galanterija, …
	 - *Duplicates/redirects*: LIMUN SVJEŽI → existing `limun`, …
	 - *Noise*: deleted aliases and reasons
4. **Manufacturer handoff** — confirm `/manufacturers` was run and summarize what it resolved, or note what remains for that pass.
5. **Remaining unmapped** and classification (mappable / noise / ambiguous).
6. **Prompt changes** — list files changed with one-line reasons, or "No changes needed."

---

## Cleanup (always last)

```bash
bb clear-folder
```

---

## Key scripts reference

| Script | Command | Purpose |
|--------|---------|---------|
| `articles_research.clj` | `bb articles-research dev [--skip-research] [--supplier X]` | Batch heuristics + Perplexity research |
| `create_articles.clj` | `bb scripts/bb/articles/create_articles.clj dev` | Create/upsert articles |
| `map_aliases.clj` | `bb scripts/bb/articles/map_aliases.clj dev` | Batch alias mapping |
| `delete_unmapped_aliases.clj` | `bb scripts/bb/articles/delete_unmapped_aliases.clj dev` | Noise deletion |
| `report_progress.clj` | `bb scripts/bb/articles/report_progress.clj dev` | Coverage report |
| `group_aliases_by_brand.clj` | `bb group-aliases-by-brand dev` | Variant risk detection |
| `list_categories.clj` | `bb list-categories dev [--with-subcategories]` | List categories |
| `unmapped_aliases_counts.clj` | `bb scripts/bb/articles/unmapped_aliases_counts.clj dev` | Grouped counts |
| `runner/run-by-ids!` | REPL | Re-process review_required receipts |
| `approval/save-review!` | REPL | Fix items/status without re-OCR |
| `perplexity_search.clj` | `bb scripts/bb/perplexity_search.clj "query"` | Manual web research (fallback) |

### Taxonomy data files (editable by this workflow)

| File | Format | Injected as | Purpose |
|------|--------|-------------|---------|
| `taxonomy/brand-parent-mappings.edn` | `{"Brand" "Parent"}` map | `{{BRAND_MAPPINGS}}` | Brand→parent-company for Perplexity prompt |
| `taxonomy/self-named-brands.edn` | `["Brand" ...]` vector | `{{BRAND_MAPPINGS}}` | Brands where brand name = manufacturer |
| `taxonomy/brand-rules.edn` | `[regex mfr cat subcat]` tuples | Local heuristics | Auto-learned brand patterns for offline matching |
| `taxonomy/keyword-category-hints.edn` | keyword→category map | Local heuristics | Keyword-based category hints |
| `taxonomy/supplier-hints.edn` | regex→category pairs | Local heuristics | Supplier-based category defaults |
| `taxonomy/english-bosnian-categories.edn` | EN→BS map | Local heuristics | English-to-Bosnian category translation |
| `taxonomy/meat-words.edn` | word set | Local heuristics | Meat product keyword detection |

All taxonomy files live under `scripts/bb/articles/taxonomy/` and are loaded by
`articles_research.clj` at research time. Brand mapping files (`brand-parent-mappings.edn`
and `self-named-brands.edn`) are formatted and injected into the Perplexity system prompt
via the `{{BRAND_MAPPINGS}}` placeholder.
