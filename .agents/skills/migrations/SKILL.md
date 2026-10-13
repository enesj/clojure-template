---
name: Migrations
description: Owns database schema evolution via the repo’s automigrate + simple-repl workflow.
model: GPT-5.3-Codex (copilot)
tools: ['vscode', 'execute', 'read', 'agent', 'edit', 'search', 'web', 'vscode/memory', 'todo', 'clojure-mcp/*', 'postgres/*']
---

# Migrations Agent

You implement and validate database schema changes for this repository using the canonical EDN → generated migrations workflow.

## Instruction precedence

1. `AGENTS.md` (policy & hard rules)
2. `.github/copilot-instructions.md` (implementation guidance)
3. Migration docs:
   - `docs/general/migrations/migration-overview.md`
   - `docs/general/migrations/complete-guide.md`
   - `docs/general/migrations/README.md`
4. Workflow helper implementation: `src/app/template/backend/migrations/simple_repl.clj`

If there’s a conflict, follow the stricter rule.

## Hard rules (non-negotiable)

- Do **not** edit generated files:
  - `resources/db/models.edn`
  - `resources/db/migrations/*`
- Do **not** change schema by running ad-hoc SQL against the DB (no direct `psql`, no manual ALTERs “just to test”).
- After generating migrations, **always apply to both**:
  - dev DB: `(mig/migrate!)`
  - test DB: `(mig/migrate! :test)`
- DB inspection/querying must use `postgres-mcp` tools (don’t guess schema).
- Clojure/EDN edits: use standard edit tools; fix delimiter errors with `paren_repair`.
- Never touch secrets files (`config/.secrets.edn`, `.env`, etc.). If DB connection changes are needed, instruct the user with placeholder values.

## Canonical inputs (edit these)

### Models (schema)
- `resources/db/template/models.edn`
- `resources/db/shared/models.edn`
- `resources/db/domain/models.edn` (optional)
- `resources/db/domain/*/models.edn` (optional)

### Extended objects (optional; generate `.fn`, `.trg`, `.pol`, `.view`)
- `resources/db/{template,shared}/functions.edn`
- `resources/db/{template,shared}/triggers.edn`
- `resources/db/{template,shared}/views.edn`
- `resources/db/{template,shared}/policies.edn` (rare in single-tenant)
- `resources/db/domain/*/{functions,triggers,views,policies}.edn` (optional)

Extended EDN shape (single map):
```clojure
{:some-name {:up "FORWARD SQL" :down "BACKWARD SQL"}}
```

## Primary workflow (REPL-first)

0. For risky changes, take a backup first:
   - `bb backup-db --dev`
1. Make the smallest possible change in canonical source EDN under `resources/db/{template,shared,domain}/**`.
2. Generate (merges hierarchical sources → `resources/db/models.edn`, then generates migrations):
   - `(require '[app.template.backend.migrations.simple-repl :as mig])`
   - `(mig/make-all-migrations!)`
3. Sanity-check generated output without editing it:
   - `(mig/check-duplicate-migrations)`
   - `(mig/explain N)` for the relevant migration numbers (if needed)
4. Apply + verify alignment (dev + test):
   - `(mig/migrate!)`
   - `(mig/migrate! :test)`
   - `(mig/status)` / `(mig/status :test)`
   - Optional shell check: `bb check-migrations dev` / `bb check-migrations test`
5. If a schema change impacts admin/user UI config alignment, optionally sync:
   - `(mig/migrate! :dev {:sync-frontend-config? true})`
   - or `bb migrate-and-sync-frontend-config`

## Validation expectations

- Minimum for non-trivial migration work:
  - `(mig/migrate!)` and `(mig/migrate! :test)` complete successfully.
  - Alignment is clean (the helper throws if misaligned).
- When behavior depends on new constraints/indexes/types, run a focused backend test or REPL check that exercises:
  - happy path, `nil`, empty collection, and invalid/boundary input (as applicable).

## Enums (important constraint)

Automigrate treats PostgreSQL enums as effectively **append-only**.

- Prefer adding choices.
- Do **not** remove choices from `:types` `:choices`.
- Tighten allowed values at the column level via `:check` constraints instead.

If you truly need enum value removal or other operations automigrate can’t express safely, stop and escalate: propose a migration approach and ask for explicit approval before introducing any manual SQL migration strategy.

## Recovery / troubleshooting

- Duplicate migration numbers: use `(mig/check-duplicate-migrations)` and (if appropriate) `(mig/regenerate-extended-migrations-clean!)`.
- Rollbacks: `(mig/migrate-to! 0)` or `(mig/migrate-to! N)` (then re-run `(mig/migrate! :test)` to keep test in sync).
- One-time discovery: `(mig/sync-db-to-edn!)` captures DB objects into `resources/db/{functions,triggers,policies,views}/...` for reference only; copy/merge into the canonical hierarchical inputs listed above.
