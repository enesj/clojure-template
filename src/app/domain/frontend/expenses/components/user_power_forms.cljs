(ns app.domain.frontend.expenses.components.user-power-forms
  "Compatibility facade — requires sub-modules to load all form components.
  Callers using (:refer [...]) continue to work via the def aliases below."
  (:require
    [app.domain.frontend.expenses.components.user-power-forms.article-forms :as af]
    [app.domain.frontend.expenses.components.user-power-forms.category-forms :as cf]
    [app.domain.frontend.expenses.components.user-power-forms.reference-forms :as ref-forms]))

;; ── Article forms ─────────────────────────────────────────────────────────────
(def user-article-add-form-modal af/user-article-add-form-modal)
(def user-article-edit-form-modal af/user-article-edit-form-modal)
(def user-article-alias-edit-form-modal af/user-article-alias-edit-form-modal)

;; ── Category forms ────────────────────────────────────────────────────────────
(def user-category-add-form-modal cf/user-category-add-form-modal)
(def user-category-edit-form-modal cf/user-category-edit-form-modal)
(def user-expense-category-add-form-modal cf/user-expense-category-add-form-modal)
(def user-expense-category-edit-form-modal cf/user-expense-category-edit-form-modal)
(def user-expense-context-add-form-modal cf/user-expense-context-add-form-modal)
(def user-expense-context-edit-form-modal cf/user-expense-context-edit-form-modal)
(def user-subcategory-add-form-modal cf/user-subcategory-add-form-modal)
(def user-subcategory-edit-form-modal cf/user-subcategory-edit-form-modal)

;; ── Reference forms ───────────────────────────────────────────────────────────
(def user-manufacturer-add-form-modal ref-forms/user-manufacturer-add-form-modal)
(def user-manufacturer-edit-form-modal ref-forms/user-manufacturer-edit-form-modal)
(def user-city-add-form-modal ref-forms/user-city-add-form-modal)
(def user-city-edit-form-modal ref-forms/user-city-edit-form-modal)
(def user-supplier-alias-edit-form-modal ref-forms/user-supplier-alias-edit-form-modal)
(def user-store-add-form-modal ref-forms/user-store-add-form-modal)
(def user-store-edit-form-modal ref-forms/user-store-edit-form-modal)
(def user-store-alias-edit-form-modal ref-forms/user-store-alias-edit-form-modal)
