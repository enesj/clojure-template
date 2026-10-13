# Repository Guidelines

## Quick decision map (tools & skills)

- Need to locate context/docs or you don’t have an exact file → use Morph WarpGrep first.
- Large file or multi-step analysis → use Lattice (load once, query/expand).
- Edits by file type:
  - `.clj`/`.cljs`/`.cljc`/`.edn` → standard edits; repair delimiters with `paren_repair` (see “MCP tool discipline” below).
  - `.md` → Morph edits.
  - Other files → standard edits with minimal diffs.
- Debugging skill map:
  - Frontend state/auth/data loading → **proj-app-db-inspect**
  - Re-frame event flow/perf → **proj-reframe-events-analysis**
  - Build/compile/runtime logs → **proj-system-logs**
- Interactive browser testing → **claude-in-chrome** in Claude Code (uses the real Chrome session and logins); **chrome-devtools** only in other clients such as Copilot, Gemini, zcode (use IDs and verify selectability).
- Database work (queries, inspection, schema info) → **postgres-mcp** tools.
- Article alias mapping throughput: for mixed alias→article targets, use `bb scripts/bb/articles/map_aliases.clj ... --mappings-file tmp/<file>.edn` in one batch call; do not run one-by-one alias loops.

## MCP tool discipline (mandatory)

These MCP servers are part of the app and should be your primary interface:

- **Clojure/EDN edits**: Use the standard edit tools for `.clj`/`.cljs`/`.cljc`/`.edn` changes (clojure-mcp runs in `:repl` profile: only `clojure_eval`, `list_nrepl_ports`, `paren_repair`). If an edit causes reader/compilation errors (unbalanced parens, invalid EDN, etc.), stop and fix with `mcp__clojure-mcp__paren_repair` (or `clj-paren-repair`) instead of continuing with ad-hoc text edits.
- **REPL evaluation is the main feedback loop**: Use `clojure-mcp` eval tools for exploration, debugging, and running focused tests.
  - Discover running REPLs: `mcp__clojure-mcp__list_nrepl_ports`
  - Evaluate code: use the `clojure-mcp` eval tool available in your client (avoid shell-based REPL commands)
  - For ClojureScript via shadow-cljs nREPL: select a build first via eval (e.g. `(shadow.cljs.devtools.api/nrepl-select :app)` or `:admin`).
- **DB access and operations**: Use `postgres-mcp` tools (e.g. `mcp__postgres__execute_sql`, schema inspection, lock inspection) instead of guessing schema or writing pseudo-SQL.
  - For agent-driven DB reads/writes, use `postgres-mcp` only. Do not run direct `psql` commands (including `bb -e` + `clojure.java.shell`) for DB inspection/querying.
  - If `mcp__postgres__execute_sql` returns `(empty)`, do not assume the query failed: the table may genuinely be empty, or the MCP session may not be connected to the expected DB.
  - Quick verification flow: run `mcp__postgres__list_tables`, then `SELECT current_database(), current_user;` and `SELECT COUNT(*) FROM <table>;` for the target table.
  - In VS Code, if results look suspicious, reconnect/restart the PostgreSQL MCP session and re-run the same checks before changing queries.
- **Browser interactions**: In Claude Code, use the `claude-in-chrome` tools (`mcp__claude-in-chrome__*`) for interactive UI testing and element verification; `chrome-devtools` is not configured for Claude Code. Other clients (Copilot, Gemini, zcode) use `chrome-devtools` MCP tools. Rely on stable `:id` attributes (see Component ID requirements below).

## Clojure helper tools (installed)

This repo also supports `clojure-mcp` tools and the lightweight `clojure-mcp-light` CLI tools (useful when a specific MCP capability isn’t available in your client, or when you want to keep the client’s native diff UI).

- **Auto paren repair via hooks (Claude Code)**: `clj-paren-repair-claude-hook`
  - Configured via `.claude/settings.json`.
  - If Clojure edits produce delimiter issues, do **not** manually “hunt parens”; rely on the hook and/or run `clj-paren-repair`.
- **On-demand paren repair (any shell-capable client)**: `clj-paren-repair path/to/file.clj`
  - Use this after Bash-based edits or when you hit the classic “Paren Edit Death Loop”.
- **REPL evaluation (preferred)**: use `clojure-mcp` eval tools.
  - Discover running REPLs: `mcp__clojure-mcp__list_nrepl_ports`
  - Evaluate code: use the `clojure-mcp` eval tool available in your client (avoid shell-based REPL commands)
  - Note: this project’s `deps.edn` includes a `:nrepl` alias (default port **7888**) for environments that still require manual nREPL startup.

## Instruction Scope & Precedence

- `AGENTS.md` is canonical for repo-wide policy and workflow (scripting, dev commands, testing discipline, tool/skill usage).
- `.github/copilot-instructions.md` is canonical for implementation guidance (coding patterns, migrations, common issues, security checks).
- If instructions conflict, follow the more specific one; otherwise prefer `AGENTS.md` for policy/workflow.

## 🚨 Hard rules

- Database schema changes must happen ONLY via the migrations process. Never alter the schema directly (manual SQL, psql, ORM/DSL hacks, or ad-hoc edits to `resources/db/models.edn` or the live database). All changes must be captured as forward/backward migrations under `resources/db/migrations/` and applied using the documented tooling (`app.template.backend.migrations.simple-repl` or bb tasks).
- When you generate or change migrations, **always apply them to both the dev and test databases** (so tests and local dev stay in sync).
- **No Python scripting** in this repo. Use Babashka (`.bb`) or Bash (`.sh`) when necessary.
- **Never commit secrets**. Keep them in `config/.secrets.edn` (or `~/.secrets.edn`) and environment variables.
- **Secret handling (agents)**: Do not read, quote, request, or **edit** secrets in `config/.secrets.edn`, `~/.secrets.edn`, `.env`, `.postgres.env`, CI secrets, or similar. If a change is required, ask the user to do it and give precise instructions (exact file path + exact keys/shape to add/change) using placeholder values (e.g. `"REDACTED"`).
- **Temporary files**: Always write temporary artifacts under the project-local `tmp/` directory (not system `/tmp`) to avoid sandbox/agent path issues; delete them when no longer needed.
- Keep changes small and focused; avoid unrelated refactors.

## Project Structure (Quick Map)

```text
src/app/        # admin, template, domain, shared (plus a small frontend/ folder for global assets)
test/           # *_test.clj / *_test.cljs mirroring src
resources/      # public assets, db models/migrations
config/         # base + secrets (local only)
cli-tools/      # dev utilities, test scripts
scripts/        # build/dev/testing helpers (sh, bb)
vendor/         # vendored libs (automigrate, ring, etc.)
Key configs: deps.edn, shadow-cljs.edn, resources/db/models.edn
```

## Development quick facts

- App is always running during development; it auto-restarts after FE/BE changes.
- Admin UI runs at `http://localhost:8085` (not 3000).
- Admin settings pages:
  - `/admin/admin-settings` (admin UI config)
  - `/admin/user-settings` (domain-owned user UI config)
- Admin domain pages (Expenses) are available under: `/admin/articles`, `/admin/article-aliases`, `/admin/suppliers`, `/admin/supplier-aliases`, `/admin/manufacturers`, `/admin/unmapped-aliases`.
- Sessions are isolated: user logout preserves `:admin-token`; admin logout preserves user `:auth-session`.
- Dev stderr is suppressed by default; set `DEV_SUPPRESS_STDERR=false` (or `0`/`no`) to keep stderr visible.
- Dev watchers ignore UI config bootstrap-default EDNs under `src/app/admin/frontend/config/*.edn` and `src/app/domain/frontend/**/config/*.edn`. Runtime config is DB-backed (seeded from these EDNs at startup).

## System logs

Use these to tail combined backend and frontend dev output:

```bash
./scripts/sh/monitoring/read_output.sh -f
```

## Testing discipline

- Run only relevant tests; avoid full-suite runs for targeted changes.
- Always save full output once and analyze it; do not re-run just to grep. Use `tee` when saving output to a file.
- For REPL-driven workflows and examples (Clojure + ClojureScript), see `.github/copilot-instructions.md#testing-from-the-repl`.

Verification (behavior changes / non-trivial changes): validate via **REPL and/or focused tests**; **at least one is required**. Minimum edge cases: happy path, `nil`, empty collections, invalid/boundary inputs. See the full checklist in `.github/copilot-instructions.md`.

Example save-output pattern:

```bash
mkdir -p tmp
bb fe-test-parallel 2>&1 | tee tmp/frontend-test-$(date +%H%M%S).txt
bb be-test 2>&1 | tee tmp/backend-test-$(date +%H%M%S).txt
```

## Component ID requirements (browser testing)

All interactive UI components must have unique `:id` attributes for browser automation (claude-in-chrome / chrome-devtools).

Use claude-in-chrome (Claude Code) or chrome-devtools (other clients) for interactive browser testing and element verification (IDs are mandatory).

ID patterns (examples):

| Component Type | Pattern | Example |
| -------------- | ------- | ------- |
| Form fields | `(str formId "-" field-type)` | `"login-form-input"` |
| Buttons | `(str "btn-" action "-" context)` | `"btn-submit-login"` |
| Settings toggles | `(str "toggle-" label "-" entity)` | `"toggle-timestamps-users"` |
| Column toggles | `(str "col-toggle-" entity "-" field)` | `"col-toggle-users-email"` |
| Action dropdowns | `(str "actions-btn-" entity-id)` | `"actions-btn-123"` |
| Filter controls | `(str "filter-" type "-" field)` | `"filter-toggle-users-name"` |

When creating new components:

1. Always accept an `:id` prop.
2. Generate fallback IDs when not provided.
3. Error elements should have IDs too (`(str field-id "-error")`).
4. Verify selectability via browser automation (claude-in-chrome / chrome-devtools).

## Documentation pointers

- `docs/index.md` (overview)
- Skill docs: `.claude/skills/*/SKILL.md` (thin wrappers) and `.github/skills/*/SKILL.md` (sources)
