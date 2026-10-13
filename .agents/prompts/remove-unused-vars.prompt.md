---
name: remove-unused-vars
description: Safely remove unused code (vars, namespaces, referred vars, private vars) from the codebase in tested batches
---

# Remove Unused Code

Execute a safe, batched removal of unused code identified by clojure-lsp.

The diagnostics script captures:
- `unused-public-var` - public vars (functions, defs) never referenced
- `unused-namespace` - required namespaces never used
- `unused-referred-var` - vars from `:refer` never used
- `unused-private-var` - private vars never used within their namespace

## Workflow

### Phase 1: Gather Diagnostics

1. Run the diagnostics task with desired scope:
   ```bash
   # Default: domain + admin only
   bb unused-public-var
   
   # Specific paths
   bb unused-public-var --domain
   bb unused-public-var --admin
   bb unused-public-var --template
   bb unused-public-var --shared
   
   # Multiple paths
   bb unused-public-var --domain --admin
   
   # Custom output file
   bb unused-public-var --domain -o tmp/domain-unused.txt
   ```

2. Read the output file at `tmp/unused_public_var.txt` (or custom path).

3. Parse and categorize the unused items by:
   - **Namespace group**: `app.domain.backend`, `app.domain.frontend`, `app.admin`, `system`, `test`, `dev`, `scripts`
   - **Diagnostic type**: 
     - `unused-public-var` / `unused-private-var` → remove the var
     - `unused-namespace` → remove the `:require` entry
     - `unused-referred-var` → remove from `:refer` vector or switch to alias-only
   - **Risk level**:
     - Low: test helpers, dev utilities, isolated handlers
     - Medium: domain services, admin events/subs
     - High: core routes, auth handlers, shared utilities

4. Capture session guardrails before edits:
   - Create a `tmp/` note with the current diagnostics snapshot.
   - Flag candidates that are likely used indirectly (especially vars commonly redefined in tests via `with-redefs`).

### Phase 2: Create Batch Plan

Group vars into logical batches (5-10 items per batch):

1. **Batch 1: Test/Dev utilities** - Lowest risk, easy to verify
   - `test/app/backend/test_helpers.clj` functions
   - `test/app/backend/test_repl.clj` functions
   - `dev/` and `dev/system/` utilities

2. **Batch 2: Admin frontend events/subs** - Medium risk
   - Unused re-frame events and subscriptions in `app.admin.frontend.events.*`
   - Unused re-frame events and subscriptions in `app.admin.frontend.subs.*`

3. **Batch 3: Domain frontend events/subs** - Medium risk
   - Unused re-frame events and subscriptions in `app.domain.frontend.events.*`
   - Unused re-frame events and subscriptions in `app.domain.frontend.subs.*`

4. **Batch 4: Domain backend handlers** - Medium risk
   - Unused handler functions in `app.domain.backend.expenses.handlers.*`

5. **Batch 5: Domain backend services** - Higher risk
   - Unused service functions in `app.domain.backend.expenses.services.*`
   - Unused integrations in `app.domain.backend.expenses.integrations.*`

6. **Batch 6: System and misc** - Variable risk
   - `system/state.clj`
   - `src/build_hooks.clj`
   - Scripts under `scripts/bb/`

### Phase 3: Execute Batches

For each batch:

1. **Pre-flight check**:
   - Verify the var is truly unused via `grep` search across **both** `src/` and `test/`
   - Search for test indirection patterns: `with-redefs`, `requiring-resolve`, `resolve`, var-quote (`#'ns/var`)
   - Check for dynamic usage patterns (e.g., re-frame keyword construction)
   - Note any vars that might be used via indirection

2. **Remove the item**:
   - Use standard edits for `.clj`/`.cljs`/`.cljc` files (`paren_repair` for delimiter errors)
   - **For `unused-public-var` / `unused-private-var`**:
     - Remove the entire `defn`/`def`/`defn-` form
     - For re-frame keywords: remove the entire `reg-event-fx`/`reg-sub` block
   - **For `unused-namespace`**:
     - Remove the entire `:require` entry from the `ns` form
     - If the namespace is the only require in a `:require` block, remove the whole `:require` clause
   - **For `unused-referred-var`**:
     - Remove the var from the `:refer` vector
     - If `:refer` becomes empty, either remove the whole require or switch to alias-only usage
     - Example: `[some.ns :refer [unused-fn used-fn]]` → `[some.ns :refer [used-fn]]` or `[some.ns :as some]`

3. **Validate**:
   - Lint touched files first:
     ```bash
     clj-kondo --lint <touched-file-or-dir>
     ```
   - Run focused tests related to the namespace:
     ```bash
     bb be-test --grep "namespace-pattern"
     ```
   - For frontend changes, run:
     ```bash
     bb fe-test-parallel --grep "namespace-pattern"
     ```
   - Save test output to `tmp/` for review

4. **Handle failures before next batch**:
   - If validation fails with unresolved vars in tests, update tests to current module boundaries (requires + `with-redefs` targets), then re-run.
   - Do not continue to the next batch until failures are understood as either:
     - fixed in this batch, or
     - explicitly documented as pre-existing and out of scope.

5. **Commit checkpoint** (optional):
   - After successful batch validation, create a checkpoint commit:
     ```bash
     git add -A && git commit -m "chore: remove unused vars batch N"
     ```

6. **Proceed to next batch** only if validation passes.

### Phase 4: Final Gate

After all batches:

1. Run full relevant suite(s) once (save output with `tee`):
   ```bash
   mkdir -p tmp
   bb be-test 2>&1 | tee tmp/backend-test-unused-vars-$(date +%H%M%S).txt
   bb fe-test-parallel 2>&1 | tee tmp/frontend-test-unused-vars-$(date +%H%M%S).txt
   ```
2. If one side was untouched, you may skip that side but state why.

## Safety Rules

- **Default scope**: `--domain` + `--admin` only; `--template` and `--shared` are excluded unless explicitly passed
- **Vendor always excluded**: Never remove vars from `vendor/`
- **Explicit opt-in**: Only remove from `template` or `shared` when explicitly requested via flags
- **Preserve metadata**: If a var has a docstring explaining purpose, document why it was removed
- **Check protocol implementations**: Protocol method removals require checking all extenders
- **Re-frame keyword patterns**: Be cautious of dynamically constructed keywords like `(keyword "admin" (str entity "-loading?"))`
- **Test fixtures**: Some test helper vars may be used via `use-fixtures` - verify before removal
- **Namespace stubs**: If removals empty a namespace file, keep a minimal `ns` form unless you also remove every require/site that depends on that namespace path
- **No silent breakage**: A green focused test is not enough; complete final gate before declaring cleanup complete

## Output Format

After each batch, report:

```
## Batch N Complete

**Removed**: 
- `namespace/var-name` (unused-public-var)
- `namespace/require-entry` (unused-namespace)
- `namespace/referred-var` (unused-referred-var)
- ...

**Tests**: PASSED / FAILED
**Output**: tmp/batch-N-test-output.txt
**Follow-up fixes**: none / brief note (e.g. "updated stale test `with-redefs` target")

**Next**: Batch N+1 description
```

## Arguments

This prompt takes no arguments. It reads fresh diagnostics each run.

## Example Invocation

```
Run the remove-unused-vars prompt to clean up unused code from domain and admin paths.
```

```
Run the remove-unused-vars prompt to clean up unused public vars.
```
