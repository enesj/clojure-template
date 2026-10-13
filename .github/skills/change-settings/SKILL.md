---
name: change-settings
description: "Apply settings changes through the real admin/user settings UI using browser automation (claude-in-chrome in Claude Code, chrome-devtools in other clients) — no code modifications. Use when changing, enabling, disabling, locking, or configuring settings in admin or user settings pages."
---

# change-settings

Use this when someone asks to change, enable, disable, lock, or configure a setting on:

- `/admin/admin-settings` (admin-scope settings)
- `/admin/user-settings` (user-scope settings)

This skill makes **permanent changes** through the real browser UI. It does not modify code, edit config files, or manipulate app-db directly.

## Source of truth

- `docs/admin/frontend/unified-settings.md` (architecture, tabs, save semantics, element IDs)
- `docs/admin/frontend/settings-testing.md` (pitfalls, local overrides awareness)

## Input contract

The prompt **must** specify:

- **Page**: `/admin/admin-settings` or `/admin/user-settings`
- **Entity**: which entity to configure (e.g., Articles, Receipts, Suppliers)
- **What to change**: the setting name/value (e.g., "lock delete off", "set per-page to 50", "enable filtering")

The prompt **may** specify:

- **Tab**: View Options, Form Fields, or Table Columns (inferred from the setting if not stated)
- **Multiple changes**: several settings on the same entity in one run

If the prompt is ambiguous about scope (admin vs user), ask before proceeding.

## Workflow

1. **Navigate** to the specified settings page using browser automation: `claude-in-chrome` in Claude Code (real Chrome session, already logged in), or `chrome-devtools` in other clients (Copilot, Gemini, zcode). Equivalent steps below apply to either tool.
2. **Enter edit mode** — click "Edit Settings".
3. **Select the entity** from the entity dropdown.
4. **Switch to the correct tab** if needed (View Options, Form Fields, or Table Columns).
5. **Apply the change**:
   - Display toggles: click the Default or Lock button to cycle through tristate (Inherit → On → Off → Inherit).
   - Per-page: select value from dropdown.
   - List behavior: select from dropdown (form display, disallowed action mode, action gates).
   - Form fields: click checkbox.
   - Table columns structural: click checkbox, type label, toggle filterable/sortable.
   - Table columns policy: click Default/Lock visibility button.
6. **Save** if the change requires it (see save semantics below).
7. **Confirm** the change persisted by reading the updated state from the UI.
8. **Report** what was changed, the before/after state, and whether save succeeded.

Do **not** verify on consuming pages — the user will do that.
Do **not** restore changes — they are intentional and permanent.
Do **not** modify any source code, config files, or app-db state.

## Save semantics

### Admin scope (`/admin/admin-settings`)

| Category | Save behavior |
|----------|---------------|
| View Options (display toggles, per-page, list behavior) | Click **"Save settings"** button |
| Form Fields (create/edit checkboxes) | **Immediate** — no save button needed |
| Table Columns structural (label, filterable, sortable, checkboxes) | **Immediate** — no save button needed |
| Table Columns policy (visibility defaults/locks) | Click **"Save settings"** button |

### User scope (`/admin/user-settings`)

| Category | Save behavior |
|----------|---------------|
| All changes | Click **"Save settings"** button — nothing saves automatically |

## Tristate toggle reference

- **Defaults**: Inherit → Default On → Default Off → Inherit
- **Locks**: Inherit → Locked On → Locked Off → Inherit

To reach a target state, click the button the correct number of times through the cycle. Read the current state first to determine how many clicks are needed.

## Key element patterns

Use `take_snapshot` to find interactive elements. Common patterns:

- Entity dropdown: `<select>` with entity options
- Tab links: text "📋 View Options", "📄 Form Fields", "📊 Table Columns"
- Toggle buttons: labeled with current state (e.g., "Default On", "Inherit", "Locked Off")
- Save button: text "Save settings"
- Discard button: text "Discard changes"

See `docs/admin/frontend/unified-settings.md` § Key Element IDs for stable ID patterns.

## Error handling

- If the settings page fails to load, report the error and stop.
- If a save fails (error alert appears), report the error message and the current dirty state.
- If a toggle does not reach the expected state after cycling, report the actual state reached.
- If the entity is not found in the dropdown, list available entities and stop.
