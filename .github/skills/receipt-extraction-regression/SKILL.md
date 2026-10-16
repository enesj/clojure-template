---
name: receipt-extraction-regression
description: "Replay stored receipt OCR responses through the current extraction code — without calling providers — to detect regressions after modifying receipt extraction heuristics, merchant/header parsing, markdown normalization, or supplier/store inference."
---

# receipt-extraction-regression

Use this when modifying receipt extraction behavior, especially in:

- `src/app/domain/backend/expenses/integrations/llamaparse/**`
- `src/app/domain/backend/expenses/workers/receipt_ocr/**`
- merchant/header parsing heuristics
- markdown normalization that affects merchant/supplier guesses
- supplier/store inference that depends on OCR-derived text

## Goal

Prove that receipt extraction changes do **not** introduce new bad merchant/supplier outputs across existing stored receipts.

## Non-negotiable rules

- Prefer **Clojure MCP eval** against the running backend nREPL (typically port `7888`).
- Use the **running dev system** and its real datasource: `(:database @system.state/state)`.
- Do **not** call LlamaParse, Mistral, Cerebras, or any external provider.
- Replay **stored** `receipts.raw_extract_json.response` only.
- Save all artifacts under project-local `tmp/`.
- If you run the replay in parallel, keep the comparison logic **pure** inside `future`s.
- **Do not use `with-redefs` inside futures**. Root-var rewrites race across threads and produce invalid audit results.

## Default flow

1. **Confirm a live backend nREPL exists**.
   - Prefer the existing backend JVM REPL (usually `7888`).

2. **Capture a baseline report before editing**.
   - Replay all stored LlamaParse responses through the current code.
   - Save to `tmp/receipt-extraction-regression-baseline.edn`.

3. **Make the code change**.

4. **Run the smallest focused namespace test first**.
   - Use `app.domain.backend.expenses.integrations.llamaparse-test`.
   - Save output once to `tmp/llamaparse-test-output.txt`.

5. **Rerun the full replay audit after editing**.
   - Save to `tmp/receipt-extraction-regression-post.edn`.
   - Run the replay in parallel (chunked futures are fine) if the logic is pure.

6. **Diff baseline vs post-change**.
   - Count changed receipts.
   - Surface suspicious new merchant names.
   - List changed receipts with before/after merchant names.

7. **Inspect changed receipts before calling them regressions**.
   - Some changes are intended improvements.
   - Prefer viewing the actual stored image for ambiguous cases.

## Core runtime pieces

Use these runtime functions/components in the replay:

- DB handle:
  - `(:database @system.state/state)`
- Parse stored `raw_extract_json`:
  - `cheshire.core/parse-string`
  - `org.postgresql.util.PGobject` handling when needed
- LlamaParse text source:
  - `app.domain.backend.expenses.integrations.llamaparse.http/response->text`
- Current structured header text:
  - `app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.text/response->header-text`
- Merchant extraction:
  - `app.domain.backend.expenses.integrations.llamaparse.receipt-extraction.merchant/text->merchant-context`

## Audit shape

The replay report should include at least:

- `:total_receipts_with_raw_extract_json`
- `:provider_counts`
- `:llamaparse_receipts_checked`
- `:changed_count`
- `:unchanged_count`
- `:suspicious_generic_new_names`
- `:changed_receipts`

For each changed receipt include:

- `:original_filename`
- `:stored_supplier_guess`
- `:old_name`
- `:new_name`
- `:items_preview`

## Suggested report filenames

- Baseline: `tmp/receipt-extraction-regression-baseline.edn`
- Post-change: `tmp/receipt-extraction-regression-post.edn`
- One-off ad hoc audit: `tmp/llamaparse-heading-regression-audit.edn`

## Suspicious outputs to flag immediately

Treat these as likely regressions until proven otherwise:

- generic headings like `Merchant Information`
- `FISKALNI RAČUN` / `FISKALNI RACUN` / `RAČUN` / `RACUN`
- city-only names like `Sarajevo`
- postal/address-like strings
- `nil` / blank merchant names

## Interpretation guide

### Usually improvements

- owner/address text replaced by real brand/store name
- malformed OCR legal-entity line normalized to a cleaner merchant name
- city/address-only output replaced by supplier/brand

### Usually regressions

- generic section label replaces merchant
- heading/noise replaces a previously valid merchant
- address/city replaces merchant
- blank merchant replaces a previously non-blank merchant

## Validation checklist

Before concluding the change is safe:

- Focused LlamaParse namespace test passes.
- Full replay audit completes against all stored receipts.
- No provider/network calls were made.
- No suspicious generic new names remain.
- Changed receipts were reviewed and classified as improvement vs regression.
- Final report artifact is saved under `tmp/`.

## Fallback when you forgot to capture a baseline

If code has already been modified and no baseline report exists:

- compare current replay outputs against stored `receipts.supplier_guess`
- label the result as a **heuristic**, not a true pre/post diff
- for narrow local changes, a pure “old logic” function may be reconstructed for comparison
- avoid thread-unsafe tricks like parallel `with-redefs`

## Repo-specific lessons already learned

- Heading-aware extraction fixed real receipts like:
  - `IMG_4202.jpeg` → `STEP`
  - `IMG_4207.jpeg` → `BY AJJA`
  - `IMG_4225.jpeg` → `KOMEL`
- A generic agentic heading caused a real regression:
  - `IMG_3611.jpg` → `Merchant Information`
- The right fix for generic headings is a **code-level guard**, not refinement.
- Refinement is optional / conditional and should not be the primary defense for deterministic parser artifacts.
- Parallel replay audits must avoid shared root-var mutation.
- Wrapped item names (pharmacy `NAME_LINE` + `12_3a40  3,70E`, `.../KO` + `M`, `NAME 2/` + `KO` + qty line) are
  joined in `text_items.clj` (`tail-fragment?`); replay of 330 receipts changed 17 labels, 0 totals.
