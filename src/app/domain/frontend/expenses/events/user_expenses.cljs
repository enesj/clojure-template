(ns app.domain.frontend.expenses.events.user-expenses
  "User expenses events - aggregator namespace.

  This namespace requires all user-expenses sub-namespaces to ensure
  their events are registered with re-frame."
  (:require
    ;; All sub-namespaces - order doesn't matter for registration
    app.domain.frontend.expenses.events.user-expenses.by-month
    app.domain.frontend.expenses.events.user-expenses.by-supplier
    app.domain.frontend.expenses.events.user-expenses.categories
    app.domain.frontend.expenses.events.user-expenses.expense-categories
    app.domain.frontend.expenses.events.user-expenses.cities
    app.domain.frontend.expenses.events.user-expenses.crud
    app.domain.frontend.expenses.events.user-expenses.detail
    app.domain.frontend.expenses.events.user-expenses.expense-contexts
    app.domain.frontend.expenses.events.user-expenses.supplier-detail
    app.domain.frontend.expenses.events.user-expenses.export
    app.domain.frontend.expenses.events.user-expenses.lookups
    app.domain.frontend.expenses.events.user-expenses.manufacturers
    app.domain.frontend.expenses.events.user-expenses.power-tools
    app.domain.frontend.expenses.events.user-expenses.profile
    app.domain.frontend.expenses.events.user-expenses.quick-add-search
    app.domain.frontend.expenses.events.user-expenses.receipts
    app.domain.frontend.expenses.events.user-expenses.expenses
    app.domain.frontend.expenses.events.user-expenses.reference-crud
    app.domain.frontend.expenses.events.user-expenses.recent
    app.domain.frontend.expenses.events.user-expenses.reports
    app.domain.frontend.expenses.events.user-expenses.search
    app.domain.frontend.expenses.events.user-expenses.stores
    app.domain.frontend.expenses.events.user-expenses.subcategories
    app.domain.frontend.expenses.events.user-expenses.summary
    app.domain.frontend.expenses.events.user-expenses.unmapped-aliases
    app.domain.frontend.expenses.events.user-expenses.workspace-dashboard))

;; This namespace exists purely to aggregate requires.
;; All events are registered in the sub-namespaces.
