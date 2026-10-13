---
name: debugging
description: "Short, evidence-based debugging playbook for Clojure/ClojureScript. Use when debugging errors, failures, regressions, or troubleshooting frontend/backend paths."
---

# debugging

## Goal
Find the root cause quickly, using evidence (not guesses).

## Default flow
1. Reproduce with the smallest steps.
2. Decide scope: frontend / backend / both.
3. Gather evidence (logs, eval, browser network).
4. Write one hypothesis and run one confirming/disproving test.
5. Fix, confirm end-to-end, then add/adjust a focused test.

## Tool choices (use specialized skills)
- Frontend state/auth/UI → `app-db-inspect`
- Frontend event/perf flow → `reframe-events-analysis`
- Build/runtime/logs (FE or BE) → `system-logs`

## Evaluation (preferred)

### Backend (Clojure)
Use `clj-nrepl-eval` to evaluate Clojure code against the running backend nREPL.

- Discover ports: `clj-nrepl-eval --discover-ports`
- Evaluate: `clj-nrepl-eval -p <PORT> "(require 'my.ns :reload) (my.ns/foo)"`

### Frontend (ClojureScript)
Use `clj-nrepl-eval` against the **shadow-cljs nREPL**, then select a build inside the eval.

- Discover ports: `clj-nrepl-eval --discover-ports`
- Select build + eval: `clj-nrepl-eval -p <SHADOW_PORT> "(shadow.cljs.devtools.api/nrepl-select :app) (require 'app.template.frontend.dev.repl-tracing :reload)"`

## Logs
```bash
./scripts/sh/monitoring/read_output.sh
./scripts/sh/monitoring/read_output.sh | rg -i "error|exception"
```

## Tests (save output once)
```bash
mkdir -p tmp
# Frontend
npm run test:cljs 2>&1 | tee tmp/fe-test.txt

# Backend
bb be-test 2>&1 | tee tmp/be-test.txt
```

Prefer focused runs over full suites:
```bash
npm run test:cljs -- --testNamePattern="..."
bb be-test --focus <namespace>
```

## Interactive browser checks (chrome-devtools)
Use `chrome-devtools` tools to validate UI flows in a real browser against the running app:
- Admin UI defaults to `http://localhost:8085`
- Prefer selecting elements by `:id` for stability (see `INTERACTIVE-COMPONENTS-ID-AUDIT.md`)

## What to collect before changing code
- Exact error/stack trace (logs) or failing test output
- Smallest repro steps (URL, clicks, payload)
- Current state snapshots (e.g. FE app-db, BE function outputs)
- One-sentence hypothesis
