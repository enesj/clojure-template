---
name: test-receipt-ocr-extract
description: "Run the receipt OCR extraction fine-tuning workflow used in the March 2026 regression session: focused tests, batch reparses, scratch-pad tracking, reruns after parser changes, and final markdown reporting"
disable-model-invocation: true
---
Use the receipt OCR extraction fine-tuning workflow we used in the March 2026 session.

Treat the active user prompt as the scope definition, but default to this workflow unless the user explicitly narrows it.

## Operating mode

- Profile: default `dev`
- OCR workflow: use the workflow requested by the user; for LlamaParse load `.env` first so `LLAMA_CLOUD_API_KEY` is available
- Batch size: default `20`
- Batch execution style: sequential, one batch at a time
- Reporting target: append findings to `receipt-ocr-batch-testing-results.md` in the project root rather than replacing earlier history

## Required workflow

1. Run focused backend tests first and save the output once under `tmp/`.
2. Generate a reusable batch-plan artifact with the repo script under `scripts/bb/expenses/`.
3. Track progress in the Clojure scratch pad throughout the run.
4. Reparse batches one at a time.
5. After each batch:
	- inspect the results in the database
	- identify parsing failures, `review_required` receipts, zero-item outputs, missing totals, or other anomalies
	- decide whether the parsing workflow needs improvement
6. If no workflow change is needed, continue to the next batch.
7. If a workflow or parser change is needed:
	- implement the smallest targeted fix
	- rerun the focused backend tests
	- rerun the current batch
	- rerun **all previously completed batches** before moving to the next unseen batch
8. Continue until all requested batches are complete or any remaining failures are clearly proven to be workflow/safety blockers rather than parsing regressions.
9. Finish by appending a dated section to `receipt-ocr-batch-testing-results.md` with the full run summary.

## Hard requirements

- Follow `AGENTS.md` and `.github/copilot-instructions.md`.
- Use `postgres-mcp` for database inspection and count reconciliation.
- Use the reusable scripts in `scripts/bb/expenses/` for batch planning and batch reparses.
- Save terminal/test artifacts under `tmp/`.
- For `.clj` / `.cljs` / `.cljc` edits, use the Clojure structural editing tools.
- Prefer direct Clojure MCP eval for one-off inspection, but keep the repeatable execution path script-based.
- Do not stop at test execution alone; finish the batch analysis and report.

## Count and scope discipline

Before interpreting totals from the run, verify the population you are counting.

- Confirm whether the run is database-wide, tenant-scoped, or user-visible-page-scoped.
- If the user compares batch totals with a UI page count, verify tenant/user visibility rules before claiming a mismatch.
- Call out linked-expense blockers, purged receipts, and cross-tenant receipts separately so the report does not mix populations.

## Batch planning and execution

Use the repo scripts under `scripts/bb/expenses/`.

Planner expectations:

- default statuses to inspect for corpus reruns: `posted` and `extracted`
- save both machine-readable and human-readable artifacts under `tmp/`
- record total receipts planned, total batches, and any relevant scope split (tenant/user/status) when it matters

Execution expectations:

- keep concurrency bounded
- reparse one batch at a time
- save a dedicated artifact for every batch run and every rerun
- if a rerun uses a special mode such as `--no-reset`, record that explicitly in the artifact name and report

## Scratch-pad tracking

Maintain scratch-pad state for the whole run, including:

- plan summary
- artifact paths
- current batch number
- per-batch result summaries
- blocked receipt IDs
- final run status (`running`, `rerunning`, `reporting`, `completed`)

The scratch pad is the source of truth for progress during the session.

## How to analyze each batch

After every batch, inspect at least:

- runner summary (`:ok`, `:failed`, `:skipped`, `:missing-result`)
- resulting receipt statuses
- whether any receipts ended in `review_required`
- whether any extracted receipts have suspiciously empty or incomplete item lists
- whether totals mismatch item sums
- whether the issue is a parsing problem, a provider problem, or a workflow/safety constraint

When a failure is not a parsing regression, say so explicitly.

Examples:

- linked-expense receipts blocked by `reset-for-ocr!` are workflow/safety blockers
- provider timeout is an execution/provider issue unless repeated evidence shows a parser defect
- page-count discrepancies may be tenant/user visibility issues rather than receipt-loss issues

## Reporting requirements

Append a dated section to `receipt-ocr-batch-testing-results.md` that includes:

- scope of the run
- focused test artifact and result summary
- batch-plan artifacts
- parser/workflow changes made during the run
- batch-by-batch results
- rerun history after each workflow change
- explicitly documented blockers
- final counts, with scope clarified
- concise interpretation of whether the parsing workflow converged

## Completion gate

Do not consider the run complete until all of the following are true:

1. focused validation has been executed and saved
2. all requested batches have been processed
3. every workflow change has been followed by rerunning the completed earlier batches
4. scratch-pad tracking is up to date
5. the root markdown report has been appended with the final dated summary
6. any remaining blockers are explicitly categorized as parsing issues or non-parsing workflow constraints