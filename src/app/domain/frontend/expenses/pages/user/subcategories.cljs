(ns app.domain.frontend.expenses.pages.user.subcategories
  "Power-user subcategories list (used for articles)."
  (:require
    [app.domain.frontend.expenses.components.page-guard :refer [expenses-page-guard]]
    [app.domain.frontend.expenses.components.user-power-forms :refer [user-subcategory-add-form-modal
                                                                      user-subcategory-edit-form-modal]]
    [app.template.frontend.components.button :refer [button]]
    [app.template.frontend.components.confirm-dialog :as confirm-dialog]
    [app.template.frontend.components.icons :refer [delete-icon edit-icon]]
    [app.template.frontend.components.list :refer [list-view]]
    [app.template.frontend.events.list.ui-state :as list-ui-state-events]
    [app.template.frontend.i18n :refer [use-t]]
    [app.template.frontend.utils.id :as id-utils]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui use-callback use-effect]]
    [uix.re-frame :refer [use-subscribe]]
    app.domain.frontend.expenses.subs.user-expenses))

(defn- render-add-form
  [{:keys [on-success on-cancel]}]
  ($ user-subcategory-add-form-modal
    {:on-success on-success
     :on-cancel on-cancel}))

(defn- render-edit-form
  [item {:keys [on-success on-cancel]}]
  ($ user-subcategory-edit-form-modal
    {:item item
     :on-success on-success
     :on-cancel on-cancel}))

(defn- render-actions
  [t item]
  (let [subcategory-id (id-utils/extract-entity-id item)
        subcategory-id-str (some-> subcategory-id str)
        on-edit-click (:on-edit-click item)
        system-default? (or (true? (:is-system-default item))
              (true? (:is_system_default item)))
        active-value (if (contains? item :is-active)
                 (:is-active item)
                 (:is_active item))
        active? (not (false? active-value))
        show-edit? (not (false? (:show-edit? item)))
        show-delete? (not (false? (:show-delete? item)))
        edit-disabled? (or system-default? (true? (:edit-disabled? item)))
        delete-disabled? (or system-default? (not active?) (true? (:delete-disabled? item)))
        item-data (dissoc item :show-edit? :show-delete? :edit-disabled? :delete-disabled? :on-edit-click)]
    ($ :div {:class "flex items-center justify-center gap-2"}
      (when show-edit?
        ($ button
          {:id (str "btn-edit-subcategories-" subcategory-id-str)
           :btn-type :primary
           :shape "circle"
           :disabled edit-disabled?
           :on-click (fn [e]
                       (.stopPropagation e)
                       (when-not edit-disabled?
                         (when on-edit-click
                           (on-edit-click item-data))))}
          ($ edit-icon)))

      (when show-delete?
        ($ button
          {:id (str "btn-delete-subcategories-" subcategory-id-str)
           :btn-type :danger
           :shape "circle"
           :disabled delete-disabled?
           :on-click (fn [e]
                       (.stopPropagation e)
                       (when-not delete-disabled?
                         (confirm-dialog/show-confirm
                           {:title (t :subcategories/delete-title)
                            :message (t :subcategories/delete-msg)
                            :on-confirm #(rf/dispatch [:user-expenses/delete-subcategory subcategory-id-str])
                            :on-cancel nil})))}
          ($ delete-icon))))))

(defui subcategories-page
  []
  (let [t (use-t)
        entity-name :subcategories
        entity-spec (use-subscribe [:entity-specs/by-name entity-name])
        refresh-list (use-callback
                       (fn []
                         ;; Ensure only tenant-manageable categories are available for FK selects.
                         (rf/dispatch [:user-expenses/fetch-categories {:managed-taxonomy-only true}])
                         (rf/dispatch [:user-expenses/refresh-subcategories-list
                                       {:managed-taxonomy-only true
                                        :include-disabled true}]))
                       [])]
    (use-effect
      (fn []
        (rf/dispatch [::list-ui-state-events/set-pagination-mode entity-name :server])
        (rf/dispatch [::list-ui-state-events/set-refresh-event entity-name [:user-expenses/refresh-subcategories-list]])
        (refresh-list)
        js/undefined)
      [refresh-list])

    ($ expenses-page-guard
      {:capability :expenses/subcategories.manage
       :children
       ($ :div {:class "min-h-screen bg-base-100"}
         ($ :header {:class "bg-white border-b border-base-200"}
           ($ :div {:class "w-full px-4 py-4 sm:py-6"}
             ($ :div {:class "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4"}
               ($ :div
                 ($ :h1 {:class "text-xl sm:text-2xl font-bold text-base-content"} (t :subcategories/title))
                 ($ :p {:class "text-sm text-base-content/70"}
                   (t :subcategories/subtitle)))
               ($ :div {:class "flex gap-2 flex-wrap"}
                 ($ button {:id "btn-back-expenses-dashboard-subcategories"
                            :btn-type :ghost
                            :on-click #(rf/dispatch [:navigate-to "/expenses"])}
                   (t :subcategories/btn-dashboard))))))

         ($ :main {:class "w-full px-4 py-6"}
           ($ list-view
             {:entity-name entity-name
              :entity-spec entity-spec
              :render-add-form render-add-form
              :render-edit-form render-edit-form
              :on-add-success refresh-list
              :on-edit-success refresh-list
              :render-actions (fn [item] (render-actions t item))})))})))
