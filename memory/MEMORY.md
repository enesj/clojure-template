# Project Memory

## Key Architecture

- **Backend entrypoint**: `src/app/template/backend/core.clj`
- **Domain routes**: `src/app/domain/backend/registry.clj` → `src/app/domain/backend/expenses/routes/core.clj`
- **Frontend entrypoint**: shadow-cljs builds `:app` and `:admin`
- **Test DB port**: 55433; **Dev DB port**: 55432; **Dev web**: 8085; **Test web**: 8086
- **nREPL port**: 7888

## DRY Patterns (Domain)

- **Backend CRUD routes**: `routes/routes_factory.clj` — `register-entity-routes!` + configs in `route_configs.clj`
- **Backend service maps**: `services/services_factory.clj`
- **Backend related-records helpers**: `services/related_records.clj` — `clamp-related-limit`, `normalize-related-type` (with aliasing), `merge-related-rows`
- **Backend pagination helpers**: `handlers/user_expenses/helpers.clj` — `parse-page-limit`, `parse-page-offset`
- **Frontend CRUD events**: `events/events_factory.cljs` — `register-entity-events!`
- **Frontend related-records events**: `events/related_records_factory.cljs` — `register-related-records-events!`
- **Frontend related-records subs**: `subs/related_records_factory.cljs` — `register-related-records-subs!`
  - State key is `:entity`; sub suffix is `-modal-entity` (not `-modal-article`)

## Frontend Naming Convention (related-records factory)

When using `register-related-records-subs!` with `entity-singular "article"`, the factory generates:
- `:expenses/article-related-records-modal-open?`
- `:expenses/article-related-records-modal-entity`  ← entity key, not `-article`
- `:expenses/article-related-records-step`
- `:expenses/article-related-records-type`
- `:expenses/article-related-records`
- `:expenses/article-related-record`
- `:expenses/article-related-records-loading?`
- `:expenses/article-related-records-error`

## Workflow

- `bb run-app` — start dev server (auto-reload)
- `bb be-test` — backend tests
- `bb fe-test-parallel` — frontend tests
- Use standard edits for `.clj`/`.cljs`/`.cljc` files; `paren_repair` for delimiter errors
- REPL on port 7888; for CLJS select build: `(shadow.cljs.devtools.api/nrepl-select :app)`

## Audit Artifacts

- `tmp/domain-dry-audit.md` — Domain DRY audit (generated 2026-02-25)
- `tmp/template-dry-audit.md` — Template DRY audit + completed refactor backlog (generated 2026-02-25)

## Template DRY Patterns (consolidated 2026-02-25)

- **`paths/admin-route? db`** — shared helper in `db/paths.cljs`; replaces the 6-site inline `(str/starts-with? route-name "admin")` pattern
- **`filter-helpers/infer-filter-type`** — public fn in `components/filter/helpers.cljs`; replaces private duplicates in `subs/list.cljs` and `subs/entity.cljs`
- **`list-subs/server-pagination? ui-state`** — public fn in `subs/list.cljs`; `subs/entity.cljs` imports and reuses it
- **`build-submit-fx`** — private helper in `events/form.cljs`; shared by `::submit-form` and `::process-default-submission`
- **`log-admin-action`** delegates to `log-admin-action-with-context` via `declare` in `routes/admin/utils.clj`
