<!-- ai: {:namespaces [app.domain.backend.expenses.*] :tags [:expenses :domain :backend] :kind :guide} -->

# Expenses Backend Services

This document covers the service layer for the **Home Expenses** domain.

## Service Map
- **Suppliers** (`app.domain.backend.expenses.services.suppliers`) — CRUD, normalization/dedupe by `normalized_key`, search/count helpers. Receipt OCR uses Places-assisted resolution (`resolve-or-create-supplier-with-places!`) and includes legacy `normalized_key` compatibility for older rows.
- **Payers** (`app.domain.backend.expenses.services.payers`) — CRUD, default-per-type management.
- **Receipts** (`app.domain.backend.expenses.services.receipts`) — upload with file-hash dedupe, status transitions, approve → post expense, extraction storage. Uploads now capture the selected `payer_id` so approval/edit forms can prefill the payer on a per-receipt basis.
- **Receipt OCR (provider-selectable)** (`app.domain.backend.expenses.integrations.mistral-ocr`, `app.domain.backend.expenses.integrations.llamaparse`, `app.domain.backend.expenses.integrations.ocr-provider`, `app.domain.backend.expenses.workers.receipt-ocr.core`) — out-of-band worker that processes uploaded receipts and populates markdown + extraction results/guesses.
- **Expenses** (`app.domain.backend.expenses.services.expenses`) — create/update with line items, soft delete, listing filters; records price observations.
- **Expense Items** (`app.domain.backend.expenses.services.expense-items`) — standalone CRUD for `expense_items` line items (list/count/search; joins to expense/supplier/payer/article for admin tables).
- **Raw Labels** (`app.domain.backend.expenses.services.raw-labels`) — dedupe + normalization for line-item labels; `expense_items` stores a FK to `raw_labels`.
- **Articles/Aliases/Price history** (`app.domain.backend.expenses.services.articles`, `price-history`) — canonical articles, alias mapping, price observation queries.
- **Reports (admin)** (`app.domain.backend.expenses.services.reports`) — summary, payer/supplier breakdowns, weekly/monthly totals, top suppliers.
- **Reports (user analytics)** (`app.domain.backend.expenses.services.user-expense-reports`) — supplier deep-dive, day-of-week pattern, top-items, monthly comparison, size distribution, daily heatmap, category allocation.

## Routing Notes
- Routes are mounted under `/admin/api/expenses` via the backend domain registry (`app.domain.backend.registry`).
- The concrete implementation lives in `app.domain.backend.expenses.routes.*`.
- For endpoint details, see [Expenses HTTP API](./http-api.md).

## Supplier resolution & dedupe

Suppliers are deduped via `suppliers.normalized_key` (derived from `display_name`). During receipt OCR processing we additionally use Google Places API v1 as a **canonicalizer**:

- Fast path: lookup by `normalized_key`.
- Miss path: call Places (failure-safe; never blocks OCR), select the best candidate, then lookup again by candidate `normalized_key`.
- Compatibility: the resolver also checks a *legacy* normalization variant (historically, diacritics like `Š/š` could be dropped), so new OCR runs don’t create duplicates against old rows.

## Automatic article resolution (dev only)

A background worker (`app.domain.backend.expenses.workers.article-resolver`) resolves new unmapped
`article_aliases` to canonical `articles` with the Claude API, so routine cases no longer need a manual
`/create-articles` run. Design and decisions: `PLAN-auto-article-resolution.md`.

**Pipeline** (`app.domain.backend.expenses.services.article-resolution.core/run-sweep!`, every 60 s):
1. reclaim stale `processing` rows, enqueue unmapped aliases created in the last 24 h, claim ≤ 200 (`FOR UPDATE SKIP LOCKED`);
2. group by `(raw_label_normalized, unit)` — one decision per group, applied to every alias in it;
3. free stages: OCR-noise detector → sibling alias already mapped → exact catalog-key match;
4. remaining groups go to Claude in chunks of ≤ 50 (structured JSON output; heuristic hints and similar-article candidates are sent along; no DB transaction is held across the call);
5. each result is validated (category enum, subcategory rules, name cleanup, manufacturer normalization, existing-article key must be an offered candidate);
6. `:auto` + no problems → one transaction creates/reuses subcategory, manufacturer and article, maps the aliases and back-fills `expense_items.article_id`; otherwise the proposal is stored for review.

**Statuses** (`alias_resolution_status`): `pending → processing → resolved | needs_review | noise | failed`;
`needs_review → resolved` (approve) or `rejected`; any → `pending` (requeue). Nothing is ever deleted.

**Config** (`config/base.edn`): `:anthropic` (model, effort, timeouts; key from `ANTHROPIC_API_KEY` or
`[:secrets :anthropic :api-key]`, plus `ANTHROPIC_WORKSPACE_ID` for keys that are not workspace-scoped) and
`:article-resolver` (`:enabled?` is true only for `:dev`; `:apply-mode`, thresholds, limits). Env overrides:
`ARTICLE_RESOLVER_ENABLED`, `ARTICLE_RESOLVER_APPLY_MODE`, `ANTHROPIC_MODEL`, `ANTHROPIC_EFFORT`.

**Apply mode**: `:dry-run` (initial) never touches the catalog — every outcome becomes `needs_review` with its
proposal; a manual approve always applies. Switch to `:auto` in `config/base.edn` (and restart the system)
only after the evaluation gate: `review/export-review-sheet!` → reviewer fills `:verdict` →
`review/score-review-sheet` must show `:auto-precision ≥ 0.95`. After the first `:auto` batch run
`review/near-duplicates`. If Claude rejects the credentials the sweep gives the attempt back, stops calling
and the worker backs off exponentially.

**Admin API** (`/admin/api/expenses/article-resolutions`): `GET ""` (`status`, `limit`, `offset`), `GET /stats`,
`POST /run` (one small sweep, 202), `POST /enqueue-backlog {"limit": n≤500}`, `POST /:id/approve` (optional
overrides), `POST /:id/reject`, `POST /:id/requeue` (paid: Claude answers again),
`GET /audit-candidates` (`max_confidence` default 80, `limit`), `POST /:id/correct` (audit fix of a resolved group), `POST /:id/audited {"verdict": "ok|fixed|rejected", "note": ".."}`. Workflow: `.github/skills/audit-articles/SKILL.md`.

**Cost controls**: `:settle-minutes` waits for 50 pending rows or a row that old before calling Claude (manual
`/run` skips the wait); groups missing from a partial response are re-sent once; the taxonomy is the last,
separately cached system block. After lowering `:auto-apply-threshold`, `core/apply-stored!` applies the stored
low-confidence proposals that now qualify without calling Claude (`{:dry-run? true}` first).
`review/run-golden!` checks a prompt/model change against `test/resources/article_resolution/golden.edn` (paid,
≈ $0.05).

**Audit**: a periodic agent reviews `GET /audit-candidates` (unaudited groups resolved below the confidence cutoff
plus the review queue, lowest confidence first), fixes through approve/reject or the catalog, then stamps
`POST /:id/audited`. `audited_at` / `audit_verdict` / `audit_note` never change status or mapping; audited groups
drop out of the candidate list.

**Catalog keys**: new rows use `normalization/normalize-catalog-key` (a port of bb `articles.db/normalize-key`),
and lookups also accept the backend `normalize-article-key` form. Taxonomy hint files live in
`resources/articles/taxonomy/` and are read-only for the backend.
