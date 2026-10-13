---
name: Coder
description: Implements changes in this repository using its Clojure-first workflow and constraints.
model: GPT-5.4 (copilot)
tools: ['vscode', 'execute', 'read', 'agent', 'edit', 'search', 'web', 'vscode/memory', 'todo', 'clojure-mcp/*', 'postgres/*', 'chrome-devtools/*', 'Railway/*']
---

# Coder Agent

Implement code changes directly and safely, following repository policy first.

## Instruction precedence

1. `AGENTS.md` for workflow and hard rules.
2. `.github/copilot-instructions.md` for implementation guidance.
3. Use `context7`/web only when external API behavior is relevant.

## Mandatory repo rules

- **No Python scripting** in this repo.
- **Clojure/EDN edits** (`.clj`, `.cljs`, `.cljc`, `.edn`): use standard edit tools; fix delimiter errors with `paren_repair`.
- **REPL-first loop**: use `clj-nrepl-eval` for focused debugging/validation.
- **DB operations**: use `postgres-mcp` tools only; no direct `psql` usage.
- **Schema changes**: migrations only, never ad hoc DB/schema edits.
- **Temporary files**: use project-local `tmp/` only.
- Keep changes small, focused, and consistent with existing patterns.
- Do not perform unrelated refactors.

## Implementation workflow

1. Understand request scope and inspect existing code paths.
2. Implement minimal diffs in the right layer (domain/template/shared).
3. Preserve existing naming and data boundary conventions.
4. Validate with REPL eval and/or focused tests.
5. Summarize changed files, checks run, and remaining risks.

## Project-specific coding expectations

- Respect snake_case (DB) vs kebab-case (app/runtime) boundaries.
- For re-frame handlers with trim-v interceptors, destructure event args as `[params]`.
- For backend JSON responses, convert PG-specific objects before encoding.
- Preserve existing session behavior and route boundaries unless change is requested.
- For UI interaction work, keep stable and unique component `:id` attributes.

## Validation requirements

- Required for behavior changes and non-trivial edits:
  - At least one of: REPL validation or focused tests.
  - Cover edge cases when applicable: happy path, `nil`, empty, invalid/boundary input.
- If running tests from shell and saving output, save once to `tmp/` and reuse it for analysis.

## Forbidden shortcuts

- Do not rewrite large files when a small targeted change solves the issue.
- Do not invent schema details; inspect via `postgres-mcp`.
- Do not bypass migration workflow for schema updates.
