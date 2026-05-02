# Utility and Recurring Expenses Implementation Plan

## Goal

Implement the workflows specified in `specs/allium/drafts/expenses/guided-utility-and-recurring-expenses.candidate.allium` across both web and mobile:

- Reusable expense contexts for reporting/filtering.
- Guided utility expense entry, allowing multiple utility bill lines in one normal expense.
- Tenant-shared expense templates.
- Subscriptions & recurring workflows with in-app reminders and editable prefilled manual entry.

## Non-goals for first release

- Automatic expense creation from recurrence schedules.
- Email, push, SMS, or external reminder delivery.
- Utility billing period, due date, meter readings, consumption analytics, or provider integrations.
- Per-line-item expense context overrides.
- Personal/private templates.

## Decisions

- Utility bills remain normal expenses.
- Utility workflow is an input convenience, not a new expense type.
- Expense context is stored at the expense level.
- Expense templates are tenant-shared.
- Recurring reminders are in-app only.
- Utility reports use `purchased_at` / paid date for v1.
- Mobile, internet, hosting, and AI tools default to `Subscriptions`.
- Donations can be recurring without necessarily belonging to `Subscriptions`.
- Web and mobile share domain rules; only presentation/navigation may differ.

## Implementation status

### Completed

- Added schema and migration `0076_schema.edn` for expense contexts, expense templates, template lines, recurring reminders, and `expenses.expense_context_id`.
- Applied migration `0076_schema.edn` to dev and test databases.
- Added generic admin routes/services for expense contexts, templates, template lines, and recurring reminders.
- Added expense context persistence for normal expense create/update paths.
- Added expense context names to expense list/detail responses.
- Added user-facing active expense context reference endpoint at `/api/v1/expenses/expense-contexts`.
- Added user-facing expense context CRUD endpoints for the main app (`GET/POST /api/v1/expenses/expense-contexts`, `PUT /api/v1/expenses/expense-contexts/:id`, `DELETE /api/v1/expenses/expense-contexts/batch`).
- Added expense context filtering for user expense list/count, date highlights, report filters, and legacy summary/month/supplier report endpoints.
- Added missing-context filtering via `expense-context-missing=true` or `missing-expense-context=true`.
- Added the web Expense Contexts user page at `/expense-contexts`, including sidebar navigation, list-view configuration, add/edit/delete modal plumbing, and translations.
- Added Expense Context selectors to manual smart entry, receipt approval, and manual expense edit forms.
- Added Expense Context columns/filterability to the user expenses list configuration.
- Added Expense Context multi-select filtering to user expense reports.

### Next

- Continue backend template/reminder workflows: recurrence helper, reminder generation, skip/snooze/record actions.

## Data model changes

### Add `expense_contexts`

Tenant-scoped reusable reporting/filtering dimension.

Suggested fields:

- `id`
- `tenant_id`
- `name`
- `description`
- `is_active`
- `created_by_subject_ref` or equivalent creator field if existing patterns use subject refs
- `created_at`
- `updated_at`

Constraints/indexes:

- unique `(tenant_id, name)`
- index `(tenant_id)`
- index active contexts if existing query style benefits from it

### Add `expense_context_id` to `expenses`

- Nullable FK to `expense_contexts/id`.
- `ON DELETE SET NULL` to preserve expenses if a context is removed.
- Index for filtering/reporting.

### Add `expense_templates`

Tenant-shared templates for utility, recurring, and generic prefill.

Suggested fields:

- `id`
- `tenant_id`
- `name`
- `kind`: `utility`, `recurring`, `generic`
- `status`: `active`, `archived`
- `created_by_subject_ref`
- `default_expense_category_id`
- `default_expense_context_id`
- `default_supplier_id`
- `default_payer_id`
- `default_currency`
- `default_notes`
- `recurrence_frequency`: nullable; `weekly`, `monthly`, `quarterly`, `yearly`, `custom`
- `recurrence_interval`: nullable/default `1`
- `next_due_date`: nullable date
- `reminder_days_before`: default `0`
- `created_at`
- `updated_at`

Constraints/indexes:

- unique active-ish name per tenant if compatible with existing UX
- index `(tenant_id, status)`
- index `(tenant_id, kind)`
- index recurring due/reminder lookup fields

### Add `expense_template_lines`

Reusable line patterns for templates.

Suggested fields:

- `id`
- `template_id`
- `label`
- `article_id`
- `default_amount`
- `sort_order`
- `is_active`
- `created_at`
- `updated_at`

Constraints/indexes:

- index `(template_id, sort_order)`

### Add `recurring_expense_reminders`

In-app reminder records. These are not expenses.

Suggested fields:

- `id`
- `template_id`
- `due_date`
- `status`: `pending`, `recorded`, `skipped`, `snoozed`
- `reminded_at`
- `snoozed_until`
- `recorded_expense_id`
- `skipped_at`
- `created_at`
- `updated_at`

Constraints/indexes:

- unique `(template_id, due_date)`
- index pending/snoozed reminders for tenant via template join or denormalized tenant if chosen

## Backend implementation phases

### Phase 1 — Schema and generic admin/reference access

1. Update canonical DB model inputs under `resources/db/domain/models.edn`.
2. Generate migrations using the repo migration workflow.
3. Apply migrations to both dev and test databases.
4. Expose `expense_contexts` through existing admin/reference-data patterns.
5. Add expense context support to expense create/update/list payloads.

### Phase 2 — Expense context filtering/reporting

1. Include `expense_context_id` in expense list queries and response shapes.
2. Add filter support for context and missing context.
3. Update summary/report queries where relevant.
4. Ensure archived contexts remain filterable for historical expenses.

### Phase 3 — Templates and recurring reminders backend

1. Add CRUD/service functions for expense templates and template lines.
2. Add recurrence helper for next due date.
3. Add reminder generation for due in-app reminders.
4. Add endpoints/actions to:
   - list templates
   - create/update/archive template
   - set recurrence
   - list pending reminders
   - open reminder prefill
   - skip reminder
   - snooze reminder
   - record reminder after expense creation

### Phase 4 — Guided utility expense backend

1. Add endpoint/action for utility draft submit or reuse existing expense submit with prefilled payload.
2. Ensure one submitted utility entry can create multiple `expense_items`.
3. Default utility category to `Utilities` when available.
4. Require or strongly prompt expense context for utility workflow.
5. Use `purchased_at` only; no billing period/due date fields in v1.

## Frontend implementation phases

### Phase 5 — Shared frontend state/helpers

1. Add entity specs and normalized keys for expense contexts, templates, template lines, reminders.
2. Add shared request builders/events/subscriptions for context lists and filters.
3. Add stable component IDs for all interactive controls.

### Phase 6 — Web UI

1. Add context management/list selection in relevant web forms.
2. Add utility entry workflow with multiple bill lines.
3. Add Subscriptions & recurring page/section:
   - templates
   - in-app reminders
   - skip/snooze
   - open prefilled draft
4. Add context filter to list/report surfaces.

### Phase 7 — Mobile UI

1. Add mobile context selection in manual/guided entry.
2. Add mobile utility entry page/surface.
3. Add mobile Subscriptions & recurring page/surface.
4. Ensure mobile uses same backend/domain contracts as web.

## Seed/reference data

Add or ensure reference data for:

- Expense category: `Utilities`
- Expense category: `Subscriptions`
- Article category: `Utilities`
- Utility subcategories:
  - `Electricity`
  - `Water`
  - `Gas`
  - `Heating`

If these are tenant/user configurable today, seed conservatively and avoid overwriting user-managed data.

## Validation plan

### Backend focused checks

- Create/list/update/archive expense contexts.
- Create expense with `expense_context_id`.
- Filter expenses by context and missing context.
- Create utility expense with multiple lines.
- Create recurring template and generate in-app reminder.
- Open reminder prefill and record normal expense.
- Skip and snooze reminder.

### Frontend focused checks

- Web utility workflow creates one expense with multiple line items.
- Mobile utility workflow creates the same shape.
- Web recurring reminder opens editable prefilled form.
- Mobile recurring reminder opens editable prefilled form.
- Context filters work on web and mobile/list surfaces.
- Archived contexts remain visible for historical filtering but not as default new-entry choices.

## Rollout sequence

1. Schema + backend context support.
2. Expense context filters.
3. Templates/reminders backend.
4. Web workflows.
5. Mobile workflows.
6. Report/dashboard polish.

## Risks and mitigations

- **Category explosion**: keep context separate from utility kind; templates can have user-friendly names like `Home electricity`.
- **Recurring automation surprise**: reminders never create expenses automatically in v1.
- **Mobile/web drift**: share backend rules and request builders where possible; document allowed presentation differences only.
- **Overcomplicated utility form**: defer billing period, due date, meter readings, and consumption until explicitly needed.
