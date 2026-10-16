# Scripts Inventory

Inventory of all scripts under `scripts/` (updated 2026-09-25, after legacy cleanup).

## Overview

| Location | Type | Count | Purpose |
|---|---|---|---|
| `scripts/bb/` | Babashka/Clojure (`.clj`, `.bb`) | 62 | DB maintenance, article/alias taxonomy workflows, code quality, dev utilities, project management |
| `scripts/sh/` | Bash (`.sh`) | 13 | Build, dev-run, monitoring, environment setup |
| `scripts/` (root) | Mixed (`.clj`, `.mjs`, `.js`) | 3 | PostCSS watch, lint repair, Morph WarpGrep search helper |

Many `bb` tasks in `bb.edn` wrap these scripts, so they are usually invoked as `bb <task>` rather than directly. Data files (`.edn`, `.txt`) that scripts consume are listed at the end of each section.

---

## scripts/bb/ (Babashka / Clojure)

### `ai/`
| Script | Description |
|---|---|
| `audit_bundle.clj` | Audits a bundle (wrapped by a bb task). |
| `schedule_claude.clj` | Schedules/runs Claude sessions (wrapped by a bb task). |

### `articles/`
Small, deterministic scripts for article/alias/taxonomy workflows (see `scripts/bb/articles/README.md`). They connect to Postgres by shelling out to `psql` with `-X` and `ON_ERROR_STOP=1`. Dev/test profile passed as first arg (`dev`/`test`); use the `railway-articles-run` bb task for prod.

| Script | Description |
|---|---|
| `articles_research.clj` | Batch article research: local heuristics + optional Perplexity web search; outputs draft `articles.edn` + `mappings.edn`. |
| `create_articles.clj` | Create/upsert articles with optional taxonomy (idempotent by `normalized_key` + `unit`). |
| `db.clj` | Shared DB helper functions for the articles scripts. |
| `delete_unmapped_aliases.clj` | Delete unmapped aliases by raw label (OCR noise cleanup; dry-run by default). |
| `ensure_taxonomy.clj` | Ensure manufacturers/categories/subcategories exist. |
| `list_aliases_from_receipts.clj` | Extract raw labels from `receipts.raw_extract_json` (fallback). |
| `list_categories.clj` | List categories (optionally with subcategories). |
| `list_review_required_receipts.clj` | Receipts in `review_required` status with mismatch diagnosis. |
| `list_unmapped_aliases.clj` | List `article_aliases` not yet mapped to an article. |
| `map_aliases.clj` | Map `article_aliases` rows to canonical articles (optionally disambiguated by `unit`). |
| `report_progress.clj` | Coverage stats + breakdowns by category and manufacturer. |
| `unmapped_aliases_counts.clj` | Unmapped aliases grouped by supplier with counts. |

Support files: `perplexity-system-prompt.txt`, `README.md`, and `research/` (library namespaces: `heuristics.clj`, `noise.clj`, `output.clj`, `perplexity.clj`, `validation.clj`), `resources/articles/taxonomy/` (EDN data, moved from `scripts/bb/articles/taxonomy/`: `brand-parent-mappings.edn`, `brand-rules.edn`, `english-bosnian-categories.edn`, `keyword-category-hints.edn`, `meat-words.edn`, `self-named-brands.edn`, `supplier-hints.edn`).

### `code_quality/`
See `scripts/bb/code_quality/README.md`.

| Script | Description |
|---|---|
| `find_snake_case_keywords.clj` | Finds snake-case keywords in Clojure code. |
| `fix_lint_warnings.clj` | Auto-fixes simple clj-kondo warnings (unused bindings, imports). |
| `fix_misplaced_docstrings.clj` | Fixes misplaced docstrings in function definitions. |
| `fix_str_warnings.clj` | Fixes string-related linting warnings. |
| `format_edn.clj` | Formats EDN configuration files with proper indentation. |
| `unused_public_var.clj` | Finds unused public vars (wrapped by a bb task). |

### `database/`
See `scripts/bb/database/README.md`. ⚠️ Some scripts are destructive.

| Script | Description |
|---|---|
| `backup_db.clj` | Creates timestamped database backups. |
| `clean_db.clj` | Completely drops and recreates the database (DANGER: destructive). |
| `compare_db_schemas.clj` | Compares database schemas between environments. |
| `compare_with_models.clj` | Compares database schema with application models (`resources/db/models.edn`). |
| `delete_articles.clj` | Deletes all articles (dry-run by default). |
| `delete_stores.clj` | Deletes all stores and unmaps store aliases (dry-run by default). |
| `empty_stores_suppliers_receipts.clj` | Empties suppliers/stores/aliases/receipts (also deletes dependent expenses). |
| `restore_db_legacy.clj` | Restores database data from a backup file (active `bb restore-db` task path). |
| `restore_db_script.clj` | Restores database data from a backup file (wired at `bb.edn:445`). |
| `seed_admin.clj` | Seeds/refreshes the default admin (`admin@example.com`/`admin123`) via the app's `admin-auth` path — hashes the password and writes the encrypted-email storage fields lookup login requires (upsert by blind lookup hash). |
| `seed_bh_cities.clj` | Seeds Bosnia & Herzegovina `cities` reference data (`bb seed-bh-cities`). |
| `seed_categories.clj` | Seeds food/non-food category reference data. |
| `seed_countries.clj` | Seeds global `countries` reference data (idempotent; `bb seed-countries`). |

### `development/`
| Script | Description |
|---|---|
| `clear_rate_limits.clj` | Clears rate-limit state by locating the dev webserver port from config. |
| `pretty_tasks.clj` | Pretty-prints the bb task list. |

### `expenses/`
| Script | Description |
|---|---|
| `group_aliases_by_brand.clj` | Groups aliases by brand using psql-based DB helpers. |
| `privacy_subject_backfill.clj` | Backfill for privacy subject data (run via a bb task, dotenv-aware). |
| `receipt_file_janitor.clj` | Cleans up receipt files (run via a bb task). |
| `spellcheck_article_canonical_names.clj` | Spellchecks article canonical names via a JSpell Checker MCP server (RapidAPI); API key via env var, never hardcoded. |

### `project-management/`
| Script | Description |
|---|---|
| `analyze_longest_files.clj` | Reports the largest/longest files in the project. |
| `auto_commit.clj` | Automatic git commits (loaded directly by a bb task). |
| `create_new_app.clj` | Enhanced Clojure project creation from the template with proper EDN parsing (replaces the former bash version). |
| `file_combiner.clj` | Combines multiple project files into one document. |
| `md_to_pdf.clj` | Converts Markdown files to PDF. |

### Root-level bb scripts
| Script | Description |
|---|---|
| `config_audit.clj` | Audits admin/domain EDN config keys — finds "looks-configurable but isn't" keys under `src/app/**/frontend/**/config/*.edn`. |
| `delete_files.bb` | Babashka helper to delete files that PATCH deletion can't touch; supports `--dry-run`. |
| `export_frontend_config_from_db.clj` | Exports frontend UI config from the DB to EDN files. |
| `fe_test_parallel.clj` | Runs frontend tests in parallel (behind the `fe-test-parallel` bb task). |
| `guard_no_concrete_domain.clj` | Guard: fails if template/admin/shared contain concrete domain references (e.g. "expenses"). |
| `migrate_and_sync_frontend_config.clj` | Runs UI config migrations and syncs them. |
| `perplexity_search.clj` | Perplexity API web-search helper. |
| `railway_frontend_config.clj` | Frontend config operations against Railway (validate/sync/export/migrate subcommands). |
| `sync_frontend_config.clj` | Syncs frontend UI config EDN files. |
| `validate_frontend_config.clj` | Validates frontend UI config. |

### `web/`
| Script | Description |
|---|---|
| `google_refresh_token.clj` | Obtains/refreshes Google OAuth tokens. |
| `serper_search.clj` | Serper.dev Google Search API helper (env-var auth). |

---

## scripts/sh/ (Bash)

### `build/`
| Script | Description |
|---|---|
| `build-production.sh` | Builds the app for production. |

### `development/`
See `scripts/sh/development/README.md`.

| Script | Description |
|---|---|
| `kill-java.sh` | Terminates all Java processes except clojure-mcp to clean up the dev environment. |
| `run-app.sh` | Primary dev-environment starter: checks port 8085, starts Docker Postgres (dev+test), waits for Postgres, starts PostCSS watcher, runs `clojure -M:dev` under monitoring. |

### `monitoring/`
See `scripts/sh/monitoring/README.md`.

| Script | Description |
|---|---|
| `check-chrome-remote-debugging.sh` | Checks Chrome remote-debugging availability (host/port options). |
| `monitor_terminal.sh` | Runs a command with output capture, PID tracking, and cleanup; used by `run-app.sh`. |
| `read_output.sh` | Reads output from `monitor_terminal.sh` commands; `-f` follows in real time. |
| `read-console.sh` | Reads browser console logs from the running dev app with filtering options. |
| `run-chrome-devtools.sh` | Launches Chrome with remote debugging for devtools-based testing. |

### `setup/`
See `scripts/sh/setup/README.md`.

| Script | Description |
|---|---|
| `archive-railway-env-to-encrypted-disk-image.sh` | Archives Railway environment variables to an encrypted disk image (GUI passphrase option). |
| `install-clj-kondo-imports.sh` | Downloads/installs community clj-kondo import configs (idempotent). |
| `install-completions.sh` | Installs shell completions. |
| `update-fish-completions.sh` | Updates Fish shell completions. |
| `update-zsh-completions.sh` | Updates Zsh completions. |

---

## scripts/ (root-level)

| Script | Description |
|---|---|
| `postcss-watch.mjs` | Node script for PostCSS watch builds (frontend global assets). |
| `repair-lints.clj` | Repair pass for lint issues (wrapped by a bb task). |
| `warpgrep-search.js` | Morph WarpGrep search helper. |

---

## Removed legacy scripts (2026-09-25)

The following legacy scripts were removed from the repo, together with all references to them:

| Removed | Reason |
|---|---|
| `bb/articles/backfill_brand_rule_manufacturers.clj` | One-off backfill, already spent. |
| `bb/articles/phase1_triage.clj`, `phase1_triage_report.clj` | "Phase 1" OCR triage on local tmp files from a finished data-cleanup campaign. |
| `bb/code_quality/check_unused_reframe.clj` + `check_unused_reframe/` (6 files) | One-off re-frame cleanup tooling with a frozen snapshot of clojure-lsp diagnostics. |
| `bb/expenses/list_unmapped_article_aliases.clj`, `search_article_products.clj` | Superseded by the `bb/articles/` equivalents. |
| `bb/expenses/receipt_ocr_extract_batches.clj`, `receipt_ocr_reparse_batch.clj` | Orphaned half of an OCR pipeline whose entry point (`receipt_ocr_worker.sh`) was already deleted. |
| `sh/setup/create-new-app.sh` | Superseded by `bb create-new-app` (`project-management/create_new_app.clj`). |

Removed later (2026-09-26): `bb/database/backfill_email_privacy.clj` and `app.template.backend.security.email-legacy-backfill` (plus the `bb backfill-email-privacy` task). The one-time email-privacy backfill completed on dev and test, and the legacy plaintext `email` columns were then dropped by migration `0080_schema.edn`, making the tool inapplicable (it queries the now-dropped plaintext column).

Reference sites cleaned at the same time: the `receipt-ocr-worker` task in `bb.edn` (pointed at the already-deleted `receipt_ocr_worker.sh`), rows in `scripts/bb/articles/README.md`, the `create-new-app.sh` section in `scripts/sh/setup/README.md`, and the phase1-triage commands/table rows in `.github/prompts/create-articles-remote-db.prompt.md` (the backlog-save step there was kept, minus the triage pipeline).

---

## Notes and inconsistencies observed

- `bb.edn` references `scripts/legacy/inventory.bb` and `scripts/legacy/audit.bb`, but `scripts/legacy/` does not exist on disk — those tasks would fail (candidate for cleanup).
- `bb.edn` contains a task pointing to `scripts/bb/code-quality/fix_str_warnings.clj` (with a dash), while the actual path is `scripts/bb/code_quality/fix_str_warnings.clj` (underscore) — that task is broken as written.
- `scripts/sh/development/README.md` mentions `sync-codex-mcp.sh`, which is not present in the folder.
- `.DS_Store` files exist in `scripts/`, `scripts/bb/`, and `scripts/sh/` (untracked noise, safe to delete and gitignore).
