<!-- ai: {:tags [:frontend :admin :settings :testing] :kind :guide} -->

# Settings Testing Workflow

Use this guide when verifying settings behavior from:

- `/admin/admin-settings` for admin-scope pages
- `/admin/user-settings` for domain-owned user-facing pages

Both scopes share the same page component and editor tabs. The full architecture and config reference is in `./unified-settings.md`.

## Goal

Confirm that a saved setting produces the expected effect on the real page that consumes it, then restore the environment cleanly.

This is a **UI verification workflow**, not a config-file-only or script-only workflow.

## Required setup

- App running locally at `http://localhost:8085`
- `claude-in-chrome` (Claude Code default) or `chrome-devtools` (other clients / fallback) available for real browser interaction
- A plan document and a results document in the repo root or `tmp/`
- At least two browser tabs/pages:
  - settings page
  - live page being verified
- Optional third browser page:
  - `/admin/user-settings` when testing admin lock propagation
  - `/admin/admin-settings` when testing upstream locks from user-settings

## Standard artifacts

Create two working documents before deep testing:

1. `...-VERIFICATION-PLAN.md`
2. `...-VERIFICATION-RESULTS.md`

The results document should:

- refer back to the plan task numbers
- record the exact setting changed
- record the exact observed live-page behavior
- call out whether the setting saved, propagated, and restored

## Assistant input contract

When this workflow is executed through an assistant skill, the incoming prompt should define the test scope.

The prompt should specify as many of these as possible:

- entity, for example `Articles`
- scope, for example `admin` or `user`
- source page, for example `/admin/admin-settings` or `/admin/user-settings`
- consuming page, for example `/admin/articles` or `/t/:tenant/expenses/list`
- settings section, for example `View Options`, `Form Fields`, or `Table Columns`
- exact task(s) or plan references to execute
- any required test data setup or cleanup

If the prompt is partial, the assistant should:

1. keep the scope narrow
2. make the smallest safe assumption
3. state that assumption in the results
4. avoid silently expanding into a full sweep unless requested

## Core rules

### 1. Test through the real UI first

Use the actual settings UI and the actual target page.

- Change settings with real clicks, inputs, and save actions.
- Verify on the real consuming page after reload.
- Use DOM/script checks only to speed up observation after the real UI action.

Do not treat script-only state inspection as a substitute for UI testing.

### 2. Keep the relevant settings form open while validating

When validating a setting on the live page, keep the exact source form visible in another tab:

- `View Options` while testing toggles
- `Form Fields` while testing add/edit forms
- `Table Columns` while testing headers, filterability, sortability, and locks

This avoids guessing about current state and makes drift obvious.

### 3. Change one thing at a time

Each verification cycle should mutate exactly one setting unless the plan explicitly requires a paired scenario.

Good:

- toggle one display option
- change one column label
- lock one column

Avoid:

- stacked edits across multiple tabs before verifying
- changing defaults and locks together unless the test calls for it

### 4. Record baseline before changing anything

Before the first mutation, capture:

- entity badge summary, for example `4 defaults`, `No locks`
- current toggle values
- current form-field selections
- current table-column settings
- live page state:
  - visible headers
  - visible toolbar actions
  - row count
  - pagination text
  - filter controls
  - edit/delete/select state

### 5. Restore after every temporary test

Each temporary change should be restored immediately after verification unless the test sequence explicitly depends on the changed state.

At the end of the run, perform a final restoration pass and record:

- badge summary
- lock count
- remaining sticky values
- any unresolved local overrides

## Browser layout

### Admin-scope testing

| Tab | Page | Purpose |
|-----|------|---------|
| A | `/admin/admin-settings` | Change settings |
| B | `/admin/articles` (or other admin list page) | Verify consuming page effect |
| C (optional) | `/admin/user-settings` | Verify lock propagation downstream |

### User-scope testing

| Tab | Page | Purpose |
|-----|------|---------|
| A | `/admin/user-settings` | Change settings |
| B | `/t/:tenant/expenses/list` (or other user-facing page) | Verify consuming page effect |
| C (optional) | `/admin/admin-settings` | Check upstream admin locks |

### Working pattern

1. Make the setting change on Page A.
2. Save if required.
3. Reload Page B.
4. Verify the user-visible effect.
5. If locks are involved, inspect Page C.
6. Restore the change on Page A.

## Save semantics by scope

Treat save behavior as part of the test, not background noise. Save semantics differ between admin and user scopes.

### Admin scope

| Category | Save behavior |
|----------|---------------|
| View Options (display toggles, per-page, list behavior) | Draft model — requires **"Save settings"** button |
| Form Fields (create/edit field checkboxes) | **Immediate** via PATCH |
| Table Columns structural (label, filterable, sortable, available/visible checkboxes) | **Immediate** via PATCH |
| Table Columns policy (visibility defaults/locks) | Draft model — requires **"Save settings"** button |

### User scope

| Category | Save behavior |
|----------|---------------|
| View Options (display toggles, per-page, list behavior) | Draft model — requires **"Save settings"** button |
| Form Fields (create/edit field checkboxes) | Draft model — requires **"Save settings"** button |
| Table Columns structural (label, filterable, sortable, available/visible checkboxes) | Draft model — requires **"Save settings"** button |
| Table Columns policy (visibility defaults/locks) | Draft model — requires **"Save settings"** button |

**Key difference:** In user scope, **all** changes are draft-based. Nothing saves immediately.

If the UI shows a save failure or a value silently reverts:

1. record it
2. reload the page
3. verify persisted state before concluding anything about the consuming page

## Standard test loop

For each plan task:

1. Record the current source setting.
2. Record the current live-page state.
3. Change the setting.
4. Save if required.
5. Reload the consuming page.
6. Verify the expected visible effect.
7. Record pass/fail/inconclusive with the exact task reference.
8. Restore the original setting.
9. Re-verify restoration when the setting is known to be sticky or masked.

## What to verify by category

### Display toggles

Always verify both:

- the control visibility itself
- the actual behavior that depends on it

Examples:

- `Edit` / `Delete`: row action buttons
- `Selection`: row + header checkboxes and batch actions
- `Filtering`: filter icons, filter inputs, filtering panel presence
- `Pagination`: page controls and page-count text
- `Add Button`: add/new action visibility
- `Highlights`: actual highlight rendering on real rows

### Form Fields

Compare the checklist directly against the opened `Add` and `Edit` forms.

- Do not assume the saved checklist is honored.
- Reopen the form after a field toggle.
- Record both checklist keys and live labels.
- If the form still shows an unchecked field, record that as a propagation failure.

### Table Columns

Verify these separately:

- visibility
- display label
- filterable
- sortable
- lock propagation

Do not collapse them into one "table columns works" result.

### List Behavior

Test with observable UI effects:

- `Form display`: modal vs inline
- `Disallowed action mode`: hidden vs disabled
- `Action gates`: whether the action is actually gated for the active route/user

If a gate test has no observable effect, record that explicitly instead of forcing a pass/fail label.

**Scope-specific notes:**
- Admin routes: action gates are **bypassed** (`gate-allows-action?` returns `true` when `admin-route? = true`). Gate effects are only observable on user-facing pages.
- User routes: gates evaluate against the user's **membership role**. Test with a user whose role does not match the gate to observe hide/disable behavior.

### Locks

A lock test is incomplete unless it checks **both**:

1. the consuming page behavior
2. the downstream settings UI where the user would normally try to override it

#### Admin → user lock propagation

When testing a lock set in `/admin/admin-settings`:

1. Verify the lock takes effect on the admin consuming page
2. Navigate to `/admin/user-settings` → select the same entity → View Options tab
3. Verify the locked setting shows as **"Enforced"** (non-interactive static text) rather than a tristate button
4. If possible, verify on the live user-facing page (requires admin settings loaded in app-db)

#### User-scope locks

When testing a lock set in `/admin/user-settings`:

1. Verify the lock takes effect on the user-facing consuming page
2. Verify the user cannot override it via browser-local preferences

## Known pitfalls and lessons learned

### Local `ui-entity-prefs` can mask admin defaults

Browser-local list preferences can override admin defaults and make a good setting look broken.

Before calling a default-setting test failed:

1. detect whether local overrides exist
2. clear them if the test requires a clean default state
3. record that you cleared them
4. restore them afterward if they existed before the test

### Admin settings and user settings are different layers

Do not assume a value visible in `/admin/admin-settings` automatically appears in `/admin/user-settings`.

Propagation itself is a test surface.

This especially matters for:

- locks (admin locks should appear as "Enforced" in user settings)
- column labels
- column policy state

### Keep evidence for sticky values

If a field refuses to restore cleanly, do not hide that fact during cleanup.

Record:

- the exact sticky value
- where it remains visible
- where it does **not** propagate

Example pattern:

- saved admin label remains `TEST LABEL`
- live page still renders the old header text

### Highlight testing may require real data mutation

If highlight behavior depends on create/update success flows:

1. create a clearly named temporary record
2. verify the live row highlight
3. edit it if updated-state highlighting is separate
4. delete the temporary record

If CRUD controls are blocked, record that limitation and distinguish it from the highlight-rendering result itself.

### Disabled actions may come from gates, not display toggles

If `Add`, `Edit`, or `Delete` appear disabled:

do not assume the basic display toggle is wrong.

Also inspect:

- `Disallowed action mode`
- action gates
- route-specific gate behavior (admin routes bypass gates)

### User-scope save requires explicit save for all tabs

Unlike admin scope where form-fields and table-columns structural changes save immediately, user scope requires an explicit **"Save settings"** click for all changes. If a user-scope test appears to not persist, check whether the save button was clicked.

## Output contract for assistants

Any assistant running settings verification should:

1. create or reuse a plan document
2. create or reuse a results document
3. test through the real UI
4. keep settings and live pages open side by side
5. record findings against plan task IDs
6. distinguish:
   - saved correctly
   - propagated correctly
   - restored correctly
7. call out local-override hazards
8. leave the environment restored except for explicitly documented sticky failures
9. treat the user prompt as the scope definition unless the prompt explicitly asks for a broad/full verification pass
10. when testing user-scope settings, pair with the correct user-facing consuming page (not an admin page)

## Reference

- Architecture and config details: `./unified-settings.md`
- Prior admin test results: `ADMIN-SETTINGS-TEST-RESULTS.md`, `ADMIN-ARTICLES-SETTINGS-VERIFICATION-RESULTS.md`, `ADMIN-ARTICLES-SETTINGS-POST-OPTIMIZATION-RESULTS.md`
- Prior user test results: `USER-SETTINGS-OPTIMIZATION-RESULTS.md`
