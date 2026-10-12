---
name: create-articles-remote-db
description: "Map OCR article aliases on Railway prod DB using batch research, review, create_articles, map_aliases, and dedicated manufacturers handoff"
disable-model-invocation: true
---
# create-articles-remote-db

Map raw `article_aliases` (where `article_id IS NULL`) to canonical `articles` on the
**Railway production database**. Same canonical workflow as `create-articles`:
`articles-research → review → create_articles → map_aliases → manufacturers`, but
without the local REPL receipt-retry / receipt re-OCR pre-phase.

---

## REMOTE / PROD DB -- READ THIS FIRST

You are operating against the **production database**. Every write is real.

### Prerequisites

- Railway CLI installed and linked: `railway login && railway link`
- `bb` and `psql` installed locally (scripts shell out to `psql`)
- The `railway-articles-run` bb task is available (defined in `bb.edn`)

### Step 1: Switch postgres-mcp to Railway

The postgres MCP server must point to Railway so that `mcp__postgres__execute_sql`
and other postgres-mcp tools query the production database.

```bash
bb postgres-mcp-railway
```

This writes Railway connection settings to `tmp/.postgres.env`. After running it,
**restart the postgres MCP server** for the new settings to take effect.

When you are done with the remote session, switch back:

```bash
bb postgres-mcp-dev
```

### Step 2: Use `railway-articles-run` for bb scripts

All bb script invocations go through the `railway-articles-run` bb task:

```bash
bb railway-articles-run scripts/bb/articles/<script>.clj [args...]
bb railway-articles-run scripts/bb/expenses/group_aliases_by_brand.clj [args...]
```

This task automatically:
1. Fetches `DATABASE_URL` from Railway via `railway run printenv`
2. Rewrites the internal hostname to the public TCP proxy
3. Injects it as `DATABASE_PUBLIC_URL` and passes `prod` as the profile

### Two tools, one database

| Tool | Use for | Example |
|------|---------|---------|
| `mcp__postgres__execute_sql` | Ad-hoc reads, inspection, quick lookups | Check an alias, verify a mapping, count rows |
| `bb railway-articles-run` | Writes and batch operations via bb scripts | `create_articles.clj`, `map_aliases.clj`, `report_progress.clj` |

Both point to the same Railway prod database after setup.

**Do NOT** use `railway run bb ...` directly -- the internal hostname is unreachable
from your local machine.

**Do NOT** print, paste, or log `DATABASE_URL` values in this conversation.

---

## Response size rules (non-negotiable)

These apply to every phase. Violating them causes token-limit errors.

- **Never reprint file contents** -- after reading any `tmp/` file, output only a short prose summary. Do not echo raw EDN/JSON/Markdown back into the conversation.
- **Tool output: summarise, don't repeat** -- when a `bb` command returns many rows, describe the result in 1-5 bullet points. Never paste raw output verbatim unless <= 10 lines.
- **Per-phase summaries only** -- at the end of each phase write a concise status line (e.g. "Phase 0 complete: 200 aliases, 30 suppliers, 7 variant-risk groups"). Skip listing every alias by ID.
- **Article/mapping tables** -- when reporting created articles or mapped aliases, use a compact Markdown table (<= 30 rows visible; if more, state the count and note detail is in `tmp/`).

---

## Hard rules (non-negotiable)

- **Do not create new categories** -- pick from the existing `categories` table only.
- **Do not use `psql` directly** -- scripts call it internally; you run them via `bb`.
- **Subcategory names must be in Bosnian.**
- **Each distinct size/volume/weight = a separate article.** Never conflate variants.
- **Batch-first**: no alias-by-alias loops. Use `--alias-id` repeats or `--mappings-file`.
- **Temporary files** go under `tmp/`; delete them after use (`bb clear-folder`).
- **No new categories**: if a better category is missing, use `Other` / `Opste` and suggest it in the output.
- **Always `--dry-run` first** for any write operation (`create_articles.clj`, `map_aliases.clj`, `ensure_taxonomy.clj`). Only run without `--dry-run` after reviewing dry-run output.

---

## Canonical workflow (mandatory)

Follow this exact order for each supplier batch on prod:

1. `articles_research.clj` via `bb railway-articles-run ...` to generate suggested articles + mappings
2. Review and correct both generated `.edn` files in one pass
3. `create_articles.clj` dry-run, then apply
4. `map_aliases.clj` dry-run, then apply
5. Run `/manufacturers-remote-db` as the primary manufacturer pass

Do **not** skip the dedicated manufacturers step for known-brand manufacturer cleanup, and do **not** use
manual Serper-only research as the primary workflow. Serper is optional support for ambiguous
items during review, not the main batch pipeline.

---

## Phase 0: Triage the backlog

> **Resuming across context boundaries?** Re-verify the starting state before acting:
> ```bash
> bb railway-articles-run scripts/bb/articles/unmapped_aliases_counts.clj --pretty  # confirms backlog size
> ```
> Do not assume prior session work is still valid — aliases may have been partially mapped.

Get an overview of what's unmapped on prod.

```bash
# Quick count + supplier grouping
bb railway-articles-run scripts/bb/articles/unmapped_aliases_counts.clj --pretty

# Full backlog list
bb railway-articles-run scripts/bb/articles/list_unmapped_aliases.clj --pretty

# Save full backlog for reference
# IMPORTANT: use --limit 9999 — the default (200) silently truncates large backlogs
bb railway-articles-run scripts/bb/articles/list_unmapped_aliases.clj --limit 9999 --pretty
```

Scan for:
- OCR noise (blank labels, digits-only, punctuation-only, < 3 alnum chars)
- High-frequency aliases that should become articles quickly
- Supplier context clues (pharmacy -> Zdravlje i apoteka, clothing -> Odjeća i modni dodaci, etc.)

---

## Phase 1: Preflight -- variant risk

Before researching or creating, detect size/variant clusters that must NOT be merged.

```bash
bb railway-articles-run scripts/bb/expenses/group_aliases_by_brand.clj --min-group 2
```

Look for `VARIANT RISK` clusters -- aliases that share a brand but differ in size (e.g. `500ml` vs `1L`).
Create **separate articles** for each distinct size.

---

## Phase 2: Check existing categories

Always pick a category from the actual table. Never assume a name.

```bash
# Flat list
bb railway-articles-run scripts/bb/articles/list_categories.clj --pretty

# With subcategories
bb railway-articles-run scripts/bb/articles/list_categories.clj --with-subcategories --pretty
```

Then pick subcategory defaults per supplier/keyword (Bosnian names):

| Context | Category | Subcategory |
|---------|----------|-------------|
| Pharmacy supplier | Zdravlje i apoteka | Opste |
| Clothing/shoes retailer | Odjeća i modni dodaci | Opste |
| Drugstore (dm, Bipa) | Lična njega | Opste |
| Dairy keywords (mlijeko, jogurt, sir) | Mliječni proizvodi i jaja | (descriptive) |
| Bread/pastry keywords (hljeb, pecivo) | Pekara i deserti | (descriptive) |
| Drinks (voda, sok, pivo) | Pića | Opste |
| Unknown / no fit | Ostalo | Opste |

---

## Phase 3: Batch research + review

Use the same batch research workflow as the local prompt, but routed through Railway.

### Step 3a — Generate suggested articles and mappings

```bash
bb railway-articles-run scripts/bb/articles/articles_research.clj --pretty

# Supplier-scoped batches (recommended for large backlogs)
bb railway-articles-run scripts/bb/articles/articles_research.clj --supplier "AMKO" --output-prefix amko --pretty
bb railway-articles-run scripts/bb/articles/articles_research.clj --supplier "APOTEKE" --output-prefix apoteke --pretty
```

This generates batch files in `tmp/`, including:

- `articles-suggested.edn`
- `mappings-suggested.edn`
- `noise-candidates.edn`
- `research-summary.edn`

When using `--output-prefix`, review the prefixed files for that supplier batch.

### Step 3b — Review both generated `.edn` files once

Review `tmp/*articles-suggested.edn` and `tmp/*mappings-suggested.edn` together before any writes.

Focus on:

- canonical naming / obvious OCR cleanup
- only obvious manufacturer fixes for known brands visible anywhere in the full label
- category/subcategory reuse from existing DB taxonomy
- size / weight / volume variants staying separate
- duplicate article merges and mapping redirects
- removing mappings for confirmed noise aliases

`manufacturer_id = NULL` is acceptable only for truly unbranded items (loose produce, services, bags, many diagnostics).
Do not do exhaustive manufacturer discovery here; `/manufacturers-remote-db` owns that pass after mapping.

Optional support for unresolved edge cases:

```bash
bb serper-search "ALIAS TEXT supplier context" --type web --num 5 --format pretty
```

---

## Phase 4: Create canonical articles

**Always dry-run first on prod.**

### Single article

```bash
bb railway-articles-run scripts/bb/articles/create_articles.clj \
	--canonical-name "Meggle Mlijeko 1L" \
	--manufacturer-name "Meggle" \
	--category-name "Mliječni proizvodi i jaja" \
	--subcategory-name "Mlijeko" \
	--dry-run --pretty

# Review output, then run without --dry-run:
bb railway-articles-run scripts/bb/articles/create_articles.clj \
	--canonical-name "Meggle Mlijeko 1L" \
	--manufacturer-name "Meggle" \
	--category-name "Mliječni proizvodi i jaja" \
	--subcategory-name "Mlijeko" \
	--pretty
```

### Batch via EDN file (preferred for > 2 articles)

Write `tmp/articles.edn` as an EDN vector of maps:

```clojure
[{:canonical-name "Meggle Mlijeko 1L"
	:manufacturer-name "Meggle"
	:manufacturer-key "meggle"
	:category-name "Mliječni proizvodi i jaja"
	:subcategory-name "Mlijeko"}

 {:canonical-name "Balea Shower Gel 250ml"
	:manufacturer-name "Balea"
	:manufacturer-key "balea"
	:category-name "Lična njega"
	:subcategory-name "Gel za tusiranje"}]
```

Then run — always dry-run first to verify `normalized_key` values before any prod writes:

```bash
# Step 1: Dry-run to preview normalized_key — REQUIRED before writing mappings.edn
bb railway-articles-run scripts/bb/articles/create_articles.clj \
	--articles-file tmp/articles.edn --dry-run --pretty | tee tmp/articles-planned.edn
# Inspect planned[*].normalized_key carefully (see Đ warning below), then:

# Step 2: Create for real
bb railway-articles-run scripts/bb/articles/create_articles.clj \
	--articles-file tmp/articles.edn --pretty | tee tmp/created-articles.edn
```

> **⚠ Đ normalization warning**: The character Đ/đ (U+0110/U+0111) is NOT NFD-decomposable —
> it is **dropped entirely** from `normalized_key`, leaving a gap.
> Examples: `"Deterđent za Suđe"` → `deter-ent-za-su-e`; `"Šećer Smeđi"` → `secer-sme-i`.
> Dž (two chars: D + ž) does decompose → `dz` (e.g. `"Džezva"` → `dzezva`).
> Always read `normalized_key` from dry-run output — never derive it mentally for names with Đ/đ.

Key facts:
- `normalized_key` is auto-derived from `canonical-name` if omitted (NFD normalization → lowercase → keep `[a-z0-9]` runs).
- Include size/volume in the name when known.
- `unit` is preserved end-to-end when present on aliases/mapping entries; the default remains `kom` only when no unit is supplied.
- Writes are idempotent per article identity (`ON CONFLICT (normalized_key, unit) DO NOTHING`) — safe to re-run.
- If you discover missing articles after the batch has run, create them in a second pass — idempotency makes it safe.

---

## Phase 5: Map aliases -> articles (batch-first)

**Always dry-run first on prod.**

### Many aliases -> same article

```bash
# Dry-run
bb railway-articles-run scripts/bb/articles/map_aliases.clj \
	--alias-id <uuid1> --alias-id <uuid2> \
	--article-key meggle-mlijeko-1l \
	--dry-run --pretty

# Then apply
bb railway-articles-run scripts/bb/articles/map_aliases.clj \
	--alias-id <uuid1> --alias-id <uuid2> \
	--article-key meggle-mlijeko-1l \
	--pretty
```

### Mixed targets -> mappings file (preferred for many articles)

**Before writing `mappings.edn`**: get the exact `normalized_key` for every article from the
dry-run output (`tmp/articles-planned.edn`) or by querying the DB for articles created earlier:

```bash
# Look up keys for pre-existing articles (not in this session's creation batch):
bb railway-articles-run scripts/bb/articles/report_progress.clj --coverage-only --pretty
# For targeted lookup, use postgres-mcp (already pointed at prod):
# SELECT normalized_key, canonical_name FROM articles WHERE canonical_name ILIKE '%<query>%' ORDER BY canonical_name LIMIT 20;
```

Write `tmp/mappings.edn`:

```clojure
[{:alias-id "uuid-1"  :article-key "meggle-mlijeko-1l"}
 {:alias-id "uuid-2"  :article-key "meggle-mlijeko-1l"}
 {:alias-id "uuid-3"  :article-key "balea-shower-gel-250ml"}
 {:raw-label "MEGGLE MLIJ" :supplier "BINGO" :article-key "meggle-mlijeko-1l"}]
```

```bash
# Dry-run
bb railway-articles-run scripts/bb/articles/map_aliases.clj \
	--mappings-file tmp/mappings.edn --dry-run --pretty

# Then apply
bb railway-articles-run scripts/bb/articles/map_aliases.clj \
	--mappings-file tmp/mappings.edn --pretty | tee tmp/mapped.edn

rm tmp/mappings.edn
```

Use `--allow-reassign` only for deliberate remaps.

> **Large backlog (> 150 aliases)**: Writing a single 200+ entry `mappings.edn` can exhaust the
> context window. Split into batches of ~80 entries by supplier group, running `map_aliases.clj`
> once per batch. Each run is safe to re-run (skips already-mapped aliases).

### Step 5c — Run the dedicated manufacturers workflow (mandatory)

After creating articles and mapping aliases, hand off manufacturer creation/reuse/wiring to:

```text
/manufacturers-remote-db
```

That command is now the **primary** manufacturer solution for production article batches.
It already includes the Railway postgres-mcp switch/restore bookends, mandatory previews,
and confirmation gates before real writes.

---

## Phase 6: Handle remaining unmapped aliases

After mapping, check what's left:

```bash
bb railway-articles-run scripts/bb/articles/unmapped_aliases_counts.clj --pretty
```

Classify each remaining alias:
- **Mappable but not yet done** -> go back to Phase 3.
- **OCR noise** (blank, digits-only, punctuation-only, < 3 alnum) -> candidate for deletion.
- **Ambiguous** (too generic, cannot determine product) -> document and leave unmapped.

Dry-run noise deletion — `--raw-label` is **required**; use labels identified by triage:

```bash
# Dry-run first (default; shows would_delete count — no writes)
bb railway-articles-run scripts/bb/articles/delete_unmapped_aliases.clj \
	--raw-label "NOISE LABEL" \
	--supplier "SUPPLIER NAME" \
	--pretty

# Repeat --raw-label for multiple noise labels in one call:
bb railway-articles-run scripts/bb/articles/delete_unmapped_aliases.clj \
	--raw-label "NOISE LABEL" \
	--raw-label "----" \
	--pretty

# Apply only after confirming would_delete count looks right
bb railway-articles-run scripts/bb/articles/delete_unmapped_aliases.clj \
	--raw-label "NOISE LABEL" \
	--supplier "SUPPLIER NAME" \
	--apply --yes --pretty
```

---

## Phase 7: Verify and report progress

```bash
bb railway-articles-run scripts/bb/articles/report_progress.clj --pretty | tee tmp/progress-report.edn
```

Check the report:
- **Coverage**: what % of aliases are now mapped.
- **By-category**: ensure `Other` is a small slice.
- **By-manufacturer**: ensure Generic is < ~30%.

---

## Completion gate (must pass before finishing)

- [ ] Requested backlog slice: articles created + aliases mapped.
- [ ] Variant risks addressed: no different sizes mapped to the same article.
- [ ] Taxonomy linked for created articles (manufacturer + subcategory where known).
- [ ] Dedicated manufacturers workflow run after mapping; known-brand misses resolved or documented.
- [ ] No subcategory named `"General"` -- all subcategories are descriptive.
- [ ] `Generic` manufacturer ≤ ~30% of **branded-product** articles (lab tests, services, parking, bulk produce are exempt).
- [ ] `Other` category is used sparingly.
- [ ] Progress verified via `report_progress.clj`.
- [ ] Remaining unmapped aliases documented (noise vs ambiguity).
- [ ] Suggested new categories listed in Bosnian with justification.

---

## Output contract

Report after each run:

1. **Summary counts**
	 - New articles created vs existing articles reused
	 - New subcategories created vs existing reused
2. What was created (articles + taxonomy) and what was mapped (aliases -> articles).
3. **Manufacturer handoff** — confirm `/manufacturers-remote-db` was run and summarize what it resolved, including new vs reused manufacturers.
4. How variant/size conflation was prevented.
5. Where evidence lives in `tmp/` (progress report, created-articles EDN, etc.).
6. What remains unmapped and why (noise vs ambiguity).
7. **Suggested new categories** (in Bosnian, with justification) for items that fell into `Other`.

---

## Cleanup (always last)

```bash
bb clear-folder
```

Deletes all files under `tmp/`.

---

## Key scripts reference

| Script | Command | Purpose |
|--------|---------|---------|
| `list_categories.clj` | `bb railway-articles-run scripts/bb/articles/list_categories.clj --pretty` | List categories (optionally with subcategories) |
| `list_unmapped_aliases.clj` | `bb railway-articles-run scripts/bb/articles/list_unmapped_aliases.clj --pretty` | Backlog list |
| `unmapped_aliases_counts.clj` | `bb railway-articles-run scripts/bb/articles/unmapped_aliases_counts.clj --pretty` | Grouped counts |
| `list_aliases_from_receipts.clj` | `bb railway-articles-run scripts/bb/articles/list_aliases_from_receipts.clj --pretty` | Extract raw labels from receipts |
| `list_review_required_receipts.clj` | `bb railway-articles-run scripts/bb/articles/list_review_required_receipts.clj --pretty` | Receipts in review_required status |
| `articles_research.clj` | `bb railway-articles-run scripts/bb/articles/articles_research.clj --pretty` | Batch research + suggested article/mapping output |
| `report_progress.clj` | `bb railway-articles-run scripts/bb/articles/report_progress.clj --pretty` | Coverage + taxonomy report |
| `create_articles.clj` | `bb railway-articles-run scripts/bb/articles/create_articles.clj --dry-run --pretty` | Create/upsert articles + taxonomy |
| `ensure_taxonomy.clj` | `bb railway-articles-run scripts/bb/articles/ensure_taxonomy.clj --dry-run --pretty` | Ensure manufacturers/categories/subcategories |
| `map_aliases.clj` | `bb railway-articles-run scripts/bb/articles/map_aliases.clj --dry-run --pretty` | Batch alias mapping |
| `delete_unmapped_aliases.clj` | `bb railway-articles-run scripts/bb/articles/delete_unmapped_aliases.clj --pretty` | Noise deletion (dry-run default) |
| `group_aliases_by_brand.clj` | `bb railway-articles-run scripts/bb/expenses/group_aliases_by_brand.clj --json` | Variant risk detection |
| `serper_search.clj` | `bb serper-search "query"` (local, no DB) | Optional targeted web research for ambiguous review cases |
