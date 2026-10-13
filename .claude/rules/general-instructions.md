# Copilot instructions (single-tenant template)

**Precedence**: `AGENTS.md` = policy/workflow & hard rules. This file = implementation patterns inside those constraints.

## MCP tools (default)

- **Clojure/EDN** (`.clj`/`.cljs`/`.cljc`/`.edn`): use the standard edit tools. If an edit causes reader/compilation errors (unbalanced parens/invalid EDN), fix it with `mcp__clojure-mcp__paren_repair` (or `clj-paren-repair <file>`) rather than hunting parens by hand.
- **REPL**: use `clj-nrepl-eval` (shell) for exploration, debugging, and focused tests (examples below).
- **DB**: use `postgres-mcp` for queries + schema (e.g. `mcp__postgres__execute_sql`, `mcp__postgres__list_tables`).
  - For agent-driven DB reads/writes, use `postgres-mcp` only (no direct `psql`, including `bb -e` + `clojure.java.shell`).
  - If `(empty)`, verify before assuming failure: `mcp__postgres__list_tables` → `SELECT current_database(), current_user;` → `SELECT COUNT(*) FROM <table>;`.
  - VS Code: if results look inconsistent, reconnect/restart the PostgreSQL MCP session and re-run.
- **Browser**: use `claude-in-chrome` (`mcp__claude-in-chrome__*`; real Chrome session, no debug port or second login). `chrome-devtools` is not used in Claude Code. Ensure stable `:id` attributes (see `AGENTS.md` for ID patterns).
- **Railway (production)**: use `Railway-mcp` tools when the server is configured. Prefer MCP over CLI for structured output. Install once: `claude mcp add Railway npx @railway/mcp-server`. Key tools: `get-logs`, `list-variables`, `list-services`, `deploy`. Full reference: `docs/general/operations/railway-deployment.md#railway-mcp-server-ai-native-debugging`.

## Entrypoints

- System: `src/app/template/backend/core.clj` (config, models, webserver, DI).
- DI container: `src/app/template/di/config.clj`.
- Routing composition: `src/app/template/backend/routes.clj`.
- Route boundaries:
  - Admin API: `src/app/template/backend/routes/admin_api.clj` mounted at `/admin/api` (template JSON middleware + `wrap-admin-authentication`).
  - User API: `src/app/template/backend/routes/api.clj` mounted at `/api/v1` (includes `/config`, `/metrics`, auth routes).
  - Domain registry: `src/app/domain/backend/registry.clj` (enabled domains, API + SPA routes).
  - Example domain: Expenses admin routes under `/admin/api/expenses` (`src/app/domain/backend/expenses/routes/core.clj`).

## Local dev

- Tests: `bb be-test` (backend), `bb fe-test-parallel` (frontend) or `npm run test:cljs`.
- Temp artifacts: use project-local `tmp/` (not system `/tmp`); delete when done.
- Save test output once (don’t re-run to grep): `mkdir -p tmp && bb be-test 2>&1 | tee tmp/be-test.txt`.
- Alias mapping throughput: batch alias→article mappings via one `map_aliases.clj --mappings-file tmp/<name>.edn` run (not one-by-one loops).

## Config & ports

- Runtime: `config/base.edn` (dev web **8085**, DB **55432**; test web **8086**, DB **55433**).
- Secrets: `config/.secrets.edn` or `~/.secrets.edn` (or env vars). **Agents must not edit secrets files**; tell the user exactly what to change (path + keys/shape) using placeholders like `"REDACTED"`.
- User UI config: edited via `/admin/user-settings`, read from DB at runtime (see `src/app/template/backend/routes/api.clj`).
- Dev helpers: rate limit/session helpers under `/admin/api/*` (see `docs/template/backend/security-middleware.md`).

## DB & migrations

- Edit canonical schema inputs under `resources/db/{template,shared,domain}`; never hand-edit `resources/db/migrations/*`.
- REPL helpers: `src/app/template/backend/migrations/simple_repl.clj` (see `docs/general/migrations/migration-overview.md`).
- After generating migrations, apply to dev + test: `(mig/migrate!)` and `(mig/migrate! :test)` (or `clj -X:migrations` / `clj -X:migrations-test`).

## Conventions / footguns

- Naming boundary: DB is `snake_case`, app/runtime is kebab-case; normalize with `app.shared.model-naming/db-keyword->app` + `ensure-app-keyword`.
- Sorting contract (`order-by`/`order-dir` are boundary input):
  - Normalize `order-by` to an app keyword via `ensure-app-keyword`.
  - Allowlist by app keywords (`:allowed-order-by` etc).
  - Only after allowlisting, map to SQL identifiers/expressions.
  - Don’t convert to DB snake_case before the allowlist check.
  - Use stable tie-breakers (e.g. primary key `:asc`) for deterministic pagination.
- Generic CRUD: `/api/v1/entities/*` is deny-by-default allowlisted; domain entities usually need domain APIs + a CRUD bridge (see `docs/template/backend/generic-entity-crud.md`).
- Frontend entity specs: `src/app/template/frontend/db/entity_specs.cljs` normalizes keys; wrong columns often = snake↔kebab mismatch.
- Re-frame: `re-frame/trim-v` is in `app.template.frontend.db.interceptors/common-interceptors`; handlers should destructure like `[params]` (not `[_ params]`).
- JSON: convert PG-specific objects before encoding (see `app.shared.type-conversion` usage in services).

## Clojure patterns

- REPL-first:
  - Use the connected REPL; don’t spawn new REPLs.
  - Prefer `clj-nrepl-eval`:
    - Discover: `clj-nrepl-eval --discover-ports`
    - Eval: `clj-nrepl-eval -p <PORT> "(require 'my.ns :reload)"`
    - CLJS: select build first: `(shadow.cljs.devtools.api/nrepl-select :app)` or `:admin`.
  - Reload explicitly after edits: `(require 'my.ns :reload)`.
  - Prefer returning values over printing.
- Validation (required for behavior changes / non-trivial changes):
  - Prove the change via REPL and/or focused tests (at least one).
  - Confirm runtime: correct nREPL port; for CLJS select build first.
  - Minimum edge cases (as applicable): happy path, `nil`, empty collections, invalid/boundary inputs.
  - If using tests, run the smallest focused set and save output once with `tee`.
  - Remove temporary instrumentation (`println`, extra logging, debug `def`s) before finishing; keep useful `(comment ...)` examples.
- Docstrings:
  - Docstring goes after function name, before args.
  ```clj
  (defn my-fn
    "Does X with Y."
    [x y]
    ...)
  ```
  - Quoting: use straight `"` and escape interior quotes (`\"`); avoid smart quotes.
  ```clj
  (defn normalize-arglist
    "Normalize a Postgres function \"argument\" list."
    [s]
    ...)
  ```
- Indentation: align multi-line forms for readability + delimiter safety.
  ```clj
  (if (and cond-a
           cond-b)
    x
    y)
  ```
- Inline `def`: allowed for REPL inspection; prefer `tap>` when a global isn’t needed.
  ```clj
  (defn process [xs]
    (def xs xs)
    (group-by :k xs))
  ```
- RCF: keep `(comment ...)` blocks copy‑eval ready.
  ```clj
  (comment
    (process [{:k 1} {:k 2}])
    :rcf)
  ```
- Tests from REPL:
  ```clj
  ;; CLJ
  (require 'app.backend.routes.api-test :reload)
  (clojure.test/run-tests 'app.backend.routes.api-test)

  ;; CLJS
  (shadow.cljs.devtools.api/nrepl-select :app)
  (require 'app.domain.frontend.registry-test :reload)
  (cljs.test/run-tests 'app.domain.frontend.registry-test)
  ```
  - Follow existing `deftest` naming conventions in the namespace; use `testing`; add `is` messages when useful.
  - Optional: Kaocha REPL commands are fine, but the `clojure.test`/`cljs.test` patterns above are preferred.
- Exploration: `clojure.repl.deps/add-libs` is exploration-only; never commit. Avoid stdin reads in BB/nREPL; pass args.
- Reload safety: keep top-level code idempotent and side-effect-light so `:reload` is safe.

## Production debugging (Railway)

**Preferred — Railway MCP** (when configured): use MCP tools directly — `get-logs`, `list-variables`, `list-services`, `deploy`. Install once: `claude mcp add Railway npx @railway/mcp-server`.

**Fallback — Railway CLI**: `railway run` spawns a local process with prod env vars injected (runtime Docker image is JRE-only — no Clojure tooling):

```bash
railway logs                           # tail live production logs
railway variables                      # list all injected env vars
railway run clj -M:nrepl               # ⚠ nREPL with live prod DATABASE_URL — reads OK, writes affect prod
railway run bb seed-geo-reference prod  # run a bb task against prod env
railway shell                          # bash in running container (JRE-only; no clj/bb/npm)
```

- Must `railway login` + `railway link` once per machine first.
- Migrations run automatically on every deploy via `preDeployCommand` in `railway.json` (`java -cp /app/app.jar clojure.main -m app.migrate`). No manual step needed.
- Full reference: `docs/general/operations/railway-deployment.md#production-debugging`.

## Security middleware toggles

- `DISABLE_HTTPS_REDIRECT=true` disables HTTPS redirect; `DISABLE_RATE_LIMITING=true` disables rate limiting (see `src/app/template/backend/middleware/security.clj`).
