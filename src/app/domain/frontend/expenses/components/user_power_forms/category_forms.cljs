(ns app.domain.frontend.expenses.components.user-power-forms.category-forms
  "Category, expense-category, and subcategory modal forms."
  (:require
    [app.template.frontend.components.form :refer [form]]
    [app.template.frontend.utils.id :as id-utils]
    [app.shared.adapters.normalization :as normalization]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui]]
    [uix.re-frame :refer [use-subscribe]]))

(def ^:private category-form-spec
  [{:id :name
    :type :text
    :label "Name"
    :required true
    :placeholder "e.g. Beverages"}
   {:id :description
    :type :textarea
    :label "Description"
    :required false
    :placeholder "Optional"}])

(defui user-category-add-form-modal
  [{:keys [on-success on-cancel]}]
  (let [form-error (use-subscribe [:user-expenses/form-error])]
    ($ :div {:class "space-y-4"}
      (when form-error
        ($ :div {:class "ds-alert ds-alert-error"}
          ($ :span form-error)))

      ($ form
        {:entity-name "categories"
         :entity-spec category-form-spec
         :editing false
         :initial-values {}
         :on-cancel on-cancel
         :on-submit (fn [{:keys [values]}]
                      (rf/dispatch [:user-expenses/create-category-modal values on-success]))
         :button-text "Save Category"}))))

(defui user-category-edit-form-modal
  [{:keys [item on-success on-cancel]}]
  (let [form-error (use-subscribe [:user-expenses/form-error])
        dynamic-spec (use-subscribe [:form-entity-specs/by-name :categories true])
        item (normalization/convert-db-keys->app-keys item)
        category-id (id-utils/extract-entity-id item)
        initial-values (-> {}
                         (assoc :name (or (:name item) ""))
                         (assoc :description (or (:description item) ""))
                         (assoc :id (or (:id item) "")))]
    ($ :div {:class "space-y-4"}
      (when form-error
        ($ :div {:class "ds-alert ds-alert-error"}
          ($ :span form-error)))

      ($ form
        {:entity-name "categories"
         :entity-spec (when-not (seq dynamic-spec) category-form-spec)
         :editing true
         :initial-values initial-values
         :on-cancel on-cancel
         :on-submit (fn [{:keys [values]}]
                      (rf/dispatch [:user-expenses/update-category-modal
                                    (some-> category-id str)
                                    values
                                    on-success]))
         :button-text "Save Category"}))))

(def ^:private expense-category-form-spec
  [{:id :name
    :type :text
    :label "Name"
    :required true
    :placeholder "e.g. Utilities"}
   {:id :exclude-from-reports
    :type :checkbox
    :label "Exclude from reports"
    :required false}
   {:id :is-default
    :type :checkbox
    :label "Default expense category"
    :required false}])

(defui user-expense-category-add-form-modal
  [{:keys [on-success on-cancel]}]
  (let [form-error (use-subscribe [:user-expenses/form-error])]
    ($ :div {:class "space-y-4"}
      (when form-error
        ($ :div {:class "ds-alert ds-alert-error"}
          ($ :span form-error)))

      ($ form
        {:entity-name "expense-categories"
         :entity-spec expense-category-form-spec
         :editing false
         :initial-values {:exclude-from-reports false
                          :is-default false}
         :on-cancel on-cancel
         :on-submit (fn [{:keys [values]}]
                      (rf/dispatch [:user-expenses/create-expense-category-modal values on-success]))
         :button-text "Save Expense Category"}))))

(defui user-expense-category-edit-form-modal
  [{:keys [item on-success on-cancel]}]
  (let [form-error (use-subscribe [:user-expenses/form-error])
        dynamic-spec (use-subscribe [:form-entity-specs/by-name :expense-categories true])
        item (normalization/convert-db-keys->app-keys item)
        expense-category-id (id-utils/extract-entity-id item)
        ;; Accept either kebab-case (post-normalization) or snake_case (raw).
        exclude-from-reports? (boolean (or (:exclude-from-reports item)
                                         (:exclude_from_reports item)))
        is-default? (boolean (or (:is-default item)
                               (:is_default item)))
        initial-values (-> {}
                         (assoc :name (or (:name item) ""))
                         (assoc :exclude-from-reports exclude-from-reports?)
                         (assoc :is-default is-default?)
                         (assoc :id (or (:id item) "")))]
    ($ :div {:class "space-y-4"}
      (when form-error
        ($ :div {:class "ds-alert ds-alert-error"}
          ($ :span form-error)))

      ($ form
        {:entity-name "expense-categories"
         :entity-spec (when-not (seq dynamic-spec) expense-category-form-spec)
         :editing true
         :initial-values initial-values
         :on-cancel on-cancel
         :on-submit (fn [{:keys [values]}]
                      (rf/dispatch [:user-expenses/update-expense-category-modal
                                    (some-> expense-category-id str)
                                    values
                                    on-success]))
         :button-text "Save Expense Category"}))))

(def ^:private expense-context-form-spec
  [{:id :name
    :type :text
    :label "Name"
    :required true
    :placeholder "e.g. Home, Rental Apartment, Utilities"}
   {:id :description
    :type :textarea
    :label "Description"
    :required false
    :placeholder "Optional"}
   {:id :is-active
    :type :checkbox
    :label "Active"
    :required false}])

(defui user-expense-context-add-form-modal
  [{:keys [on-success on-cancel]}]
  (let [form-error (use-subscribe [:user-expenses/form-error])]
    ($ :div {:class "space-y-4"}
      (when form-error
        ($ :div {:class "ds-alert ds-alert-error"}
          ($ :span form-error)))

      ($ form
        {:entity-name "expense-contexts"
         :entity-spec expense-context-form-spec
         :editing false
         :initial-values {:is-active true}
         :on-cancel on-cancel
         :on-submit (fn [{:keys [values]}]
                      (rf/dispatch [:user-expenses/create-expense-context-modal values on-success]))
         :button-text "Save Expense Context"}))))

(defui user-expense-context-edit-form-modal
  [{:keys [item on-success on-cancel]}]
  (let [form-error (use-subscribe [:user-expenses/form-error])
        item (normalization/convert-db-keys->app-keys item)
        context-id (id-utils/extract-entity-id item)
        initial-values (-> {}
                         (assoc :name (or (:name item) ""))
                         (assoc :description (or (:description item) ""))
                         (assoc :is-active (if (contains? item :is-active)
                                             (boolean (:is-active item))
                                             true))
                         (assoc :id (or (:id item) "")))]
    ($ :div {:class "space-y-4"}
      (when form-error
        ($ :div {:class "ds-alert ds-alert-error"}
          ($ :span form-error)))

      ($ form
        {:entity-name "expense-contexts"
         :entity-spec expense-context-form-spec
         :editing true
         :initial-values initial-values
         :on-cancel on-cancel
         :on-submit (fn [{:keys [values]}]
                      (rf/dispatch [:user-expenses/update-expense-context-modal
                                    (some-> context-id str)
                                    values
                                    on-success]))
         :button-text "Save Expense Context"}))))

(def ^:private subcategory-form-spec
  [{:id :category_id
    :type :select
    :label "Category"
    :required true
    :options ["categories" "name"]}
   {:id :name
    :type :text
    :label "Name"
    :required true
    :placeholder "e.g. Coffee"}
   {:id :description
    :type :textarea
    :label "Description"
    :required false
    :placeholder "Optional"}])

(defui user-subcategory-add-form-modal
  [{:keys [on-success on-cancel]}]
  (let [form-error (use-subscribe [:user-expenses/form-error])]
    ($ :div {:class "space-y-4"}
      (when form-error
        ($ :div {:class "ds-alert ds-alert-error"}
          ($ :span form-error)))

      ($ form
        {:entity-name "subcategories"
         :entity-spec subcategory-form-spec
         :editing false
         :initial-values {}
         :on-cancel on-cancel
         :on-submit (fn [{:keys [values]}]
                      (rf/dispatch [:user-expenses/create-subcategory-modal values on-success]))
         :button-text "Save Subcategory"}))))

(defui user-subcategory-edit-form-modal
  [{:keys [item on-success on-cancel]}]
  (let [form-error (use-subscribe [:user-expenses/form-error])
        dynamic-spec (use-subscribe [:form-entity-specs/by-name :subcategories true])
        item (normalization/convert-db-keys->app-keys item)
        subcategory-id (id-utils/extract-entity-id item)
        category-id (or (:category-id item)
                      (:category_id item)
                      (:categoryId item))
        initial-values (-> {}
                         (assoc :category_id (some-> category-id str))
                         (assoc :name (or (:name item) ""))
                         (assoc :description (or (:description item) ""))
                         (assoc :id (or (:id item) "")))]
    ($ :div {:class "space-y-4"}
      (when form-error
        ($ :div {:class "ds-alert ds-alert-error"}
          ($ :span form-error)))

      ($ form
        {:entity-name "subcategories"
         :entity-spec (when-not (seq dynamic-spec) subcategory-form-spec)
         :editing true
         :initial-values initial-values
         :on-cancel on-cancel
         :on-submit (fn [{:keys [values]}]
                      (rf/dispatch [:user-expenses/update-subcategory-modal
                                    (some-> subcategory-id str)
                                    values
                                    on-success]))
         :button-text "Save Subcategory"}))))
