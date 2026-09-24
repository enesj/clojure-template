# Utility expense entry workflow plan

## Scope

Implement the first usable slice of the guided **Add utility bill** workflow.

The workflow is a user-entry convenience only. It must save a standard `Expense` with standard `ExpenseItem` rows so existing reporting, filtering, exports, and permissions continue to work.

## In scope

- A user-facing route/page for adding a utility bill.
- Utility kind selection from active `Utilities` article subcategories.
- Context selection from existing tenant `ExpenseContext` records.
- Normal bill details: amount, paid date, payer, optional supplier/provider, currency, and notes.
- Backend endpoint that validates the chosen utility subcategory and creates a normal expense.
- Automatic tenant `ExpenseCategory` named `Utilities` when missing.
- Automatic canonical utility article for the selected utility kind, e.g. `Electricity bill`, attached to the chosen `Utilities` subcategory.
- Expense item persistence with `article_id` so article-category reports can classify the bill as Utilities/Electricity/etc.

## Out of scope for this slice

- Billing period, due date, and account/reference number fields.
- Automatic bill payment or automatic expense creation.
- Full recurring/template UI.
- Multiple utility lines in one guided form. The Allium spec allows it, but this first UI slice records one utility kind per save.
- Creating/managing expense contexts or suppliers inline from this workflow.

## Happy path

1. User opens **Add utility bill**.
2. App loads active utility subcategories, payers, suppliers, contexts, and currencies.
3. User chooses utility kind, context, payer, date, amount, optional supplier/provider, currency, and notes.
4. Backend verifies the utility kind is an active subcategory under the `Utilities` article category and visible to the tenant.
5. Backend ensures tenant expense category `Utilities` exists.
6. Backend ensures an article such as `Electricity bill` exists and is classified under the chosen utility subcategory.
7. Backend creates a regular expense with one regular expense item linked to that article.
8. UI navigates back to the expense list.

## Edge cases

- Missing or inactive utility kind: reject with validation error.
- Utility kind from another article category: reject with validation error.
- Missing payer, date, or positive amount: reject with validation error.
- Existing generated article has no subcategory: assign it to the chosen utility subcategory.
- Existing generated article has a different subcategory: reject instead of silently reclassifying unrelated historical data.

## Implementation steps

1. Add backend utility expense service and handler.
2. Add route before generic `/:id` expense route.
3. Preserve `article_id` on expense items in the generic expense service.
4. Add frontend utility endpoint/event.
5. Add user route/page and page registry entry.
6. Add shortcut buttons from dashboard/list and navigation entry.
7. Add focused backend tests and run frontend compile/config checks.
