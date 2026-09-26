# scripts/bb/articles

Small, deterministic Babashka scripts for article/alias/taxonomy workflows.

These are designed to replace ad-hoc SQL snippets (and any Postgres MCP usage) in the `create-articles` skill.

They connect to Postgres by shelling out to `psql` (bb-compatible) with `-X` and `ON_ERROR_STOP=1` for more deterministic behavior.

## Running locally (dev / test)

Pass `dev` or `test` as the first argument (defaults to `dev`):

```bash
bb scripts/bb/articles/list_unmapped_aliases.clj dev
bb scripts/bb/articles/create_articles.clj dev --canonical-name "Nescafe"
bb scripts/bb/articles/list_categories.clj dev --with-subcategories --pretty
bb scripts/bb/articles/report_progress.clj dev --pretty
```

DB connection settings are read from `config/base.edn` for the given profile.

## Running against Railway prod

Use the `railway-articles-run` bb task, which automatically:
1. Fetches `DATABASE_URL` from Railway via `railway run`
2. Rewrites the internal hostname to the public TCP proxy
3. Injects it as `DATABASE_PUBLIC_URL` and passes `prod` as the profile

**Prerequisites:** Railway CLI installed and linked (`railway login && railway link`).

```bash
# Inspection (read-only)
bb railway-articles-run scripts/bb/articles/list_categories.clj --with-subcategories --pretty
bb railway-articles-run scripts/bb/articles/report_progress.clj --pretty
bb railway-articles-run scripts/bb/articles/list_unmapped_aliases.clj --limit 50 --pretty
bb railway-articles-run scripts/bb/articles/unmapped_aliases_counts.clj --pretty
bb railway-articles-run scripts/bb/articles/list_aliases_from_receipts.clj --pretty
bb railway-articles-run scripts/bb/articles/list_review_required_receipts.clj --pretty

# Writes (use --dry-run first)
bb railway-articles-run scripts/bb/articles/create_articles.clj --canonical-name "Nescafe" --dry-run
bb railway-articles-run scripts/bb/articles/ensure_taxonomy.clj --category-name "Dairy" --dry-run
bb railway-articles-run scripts/bb/articles/map_aliases.clj --alias-id UUID --article-key KEY --dry-run
bb railway-articles-run scripts/bb/articles/delete_unmapped_aliases.clj --raw-label "noise"  # dry-run by default; add --apply to execute
```

Alternatively, set `DATABASE_PUBLIC_URL` manually and pass `prod` directly:

```bash
DATABASE_PUBLIC_URL="postgresql://user:pass@gondola.proxy.rlwy.net:12386/railway" \
  bb scripts/bb/articles/report_progress.clj prod --pretty
```

## Scripts

| Script | Purpose |
|---|---|
| `list_categories.clj` | List categories (optionally with subcategories) |
| `list_unmapped_aliases.clj` | List `article_aliases` not yet mapped to an article |
| `unmapped_aliases_counts.clj` | Unmapped aliases grouped by supplier with counts |
| `list_aliases_from_receipts.clj` | Extract raw labels from `receipts.raw_extract_json` (fallback) |
| `list_review_required_receipts.clj` | Receipts in `review_required` status with mismatch diagnosis |
| `report_progress.clj` | Coverage stats + breakdowns by category and manufacturer |
| `create_articles.clj` | Create/upsert articles with optional taxonomy (idempotent by `normalized_key` + `unit`) |
| `ensure_taxonomy.clj` | Ensure manufacturers/categories/subcategories exist |
| `map_aliases.clj` | Map `article_aliases` rows to canonical articles (optionally disambiguated by `unit`) |
| `delete_unmapped_aliases.clj` | Delete unmapped aliases by raw label (OCR noise cleanup) |
