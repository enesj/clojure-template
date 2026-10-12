(ns app.template.frontend.components.list.render
  "Render-only pieces for list-view. Kept hook-free so list.cljs can own state/effects."
  (:require
    [app.shared.keywords :as kw]
    [app.template.frontend.components.batch-edit :refer [batch-edit-inline]]
    [app.template.frontend.components.filter :refer [filter-form]]
    [app.template.frontend.components.filter.ui :refer [compact-active-filters]]
    [app.template.frontend.components.list.modals :as list-modals]
    [app.template.frontend.components.list.ui :refer [active-sort-controls
                                                      add-item-section
                                                      header-section]]
    [app.template.frontend.components.messages :refer [error-alert]]
    [app.template.frontend.components.pagination :refer [pagination]]
    [app.template.frontend.components.table :refer [table]]
    [app.template.frontend.events.config :as config-events]
    [app.template.frontend.events.list.batch :as batch-events]
    [app.template.frontend.events.list.filters :as filter-events]
    [app.template.frontend.events.list.ui-state :as ui-events]
    [app.template.frontend.subs.list :as list-subs]
    [app.template.frontend.utils.id :as id-utils]
    [re-frame.core :as rf]
    [uix.core :refer [$]]))

(defn render-list-view-shell
  [{:keys [entity-name entity-kw entity-spec props t
           add-modal-open? resolved-add-modal-title form-entity-spec
           has-custom-add-form? render-add-form handle-add-modal-close handle-add-modal-success
           edit-modal-open? edit-modal-item resolved-edit-modal-title form-entity-spec-edit
           has-custom-edit-form? render-edit-form handle-edit-modal-close handle-edit-modal-success
           batch-edit-inline-state selected-ids error loading? raw-items show-inline-add-form?
           base-props visible-items effective-disallowed-action-mode merged-display-settings
           effective-allow-add? table-width title show-add-form? use-modal-forms? handle-add-click
           active-filters active-inline-filter inline-filter-field-spec inline-filter-value
           handle-filter-close handle-filter-apply set-active-inline-filter set-inline-filter-field-spec
           set-inline-filter-value sorts sort-field-labels ui-state hardcoded-view-options
           selected-count hidden-selected-count toggle-group-pill shell-ref table-height table-headers
           show-highlights? render-row-fn measured-table-height measured-table-width effective-per-page
           on-per-page-change pagination-total-pages pagination-current-page on-page-change]}]
  ($ :div {:class "w-full flex justify-start items-start"
           :id (str "table-" (kw/ensure-name entity-name))}
    ($ :div {:class "p-2 w-full"}
      (list-modals/render-add-modal
        {:add-modal-open? add-modal-open?
         :add-modal-title resolved-add-modal-title
         :entity-name entity-name
         :entity-kw entity-kw
         :entity-spec entity-spec
         :form-entity-spec form-entity-spec
         :has-custom-add-form? has-custom-add-form?
         :render-add-form render-add-form
         :handle-add-modal-close handle-add-modal-close
         :handle-add-modal-success handle-add-modal-success})

      (list-modals/render-edit-modal
        {:edit-modal-open? edit-modal-open?
         :edit-modal-item edit-modal-item
         :edit-modal-title resolved-edit-modal-title
         :entity-name entity-name
         :entity-kw entity-kw
         :entity-spec entity-spec
         :form-entity-spec form-entity-spec
         :form-entity-spec-edit form-entity-spec-edit
         :has-custom-edit-form? has-custom-edit-form?
         :render-edit-form render-edit-form
         :handle-edit-modal-close handle-edit-modal-close
         :handle-edit-modal-success handle-edit-modal-success})

      (when (and (:open? batch-edit-inline-state)
              (seq selected-ids)
              entity-name)
        ($ batch-edit-inline
          {:entity-name (keyword entity-name)
           :selected-ids selected-ids
           :on-close #(rf/dispatch [::batch-events/hide-batch-edit-inline entity-name])}))

      (when error
        ($ error-alert
          {:error error
           :entity-name entity-name}))
      (cond
        (and loading? (empty? raw-items) (not show-inline-add-form?))
        ($ :div ($ :span (t :common/loading)))

        :else
        ($ :div
          (if show-inline-add-form?
            ($ add-item-section base-props)
            (let [items-vec visible-items
                  disallowed-mode effective-disallowed-action-mode
                  disable-mode? (= disallowed-mode :disable)
                  policy-show-add-button? (not (false? (:show-add-button? merged-display-settings)))
                  allowed-add? effective-allow-add?
                  show-add-button? (and policy-show-add-button? (or allowed-add? disable-mode?))
                  add-disabled? (and policy-show-add-button? disable-mode? (not allowed-add?))]
              ($ :div {:class "w-full flex flex-col gap-3"
                       :style (cond-> {}
                                table-width (assoc :max-width (str table-width "px")))}
                ($ header-section
                  {:title title
                   :show-add-form? show-add-form?
                   :set-show-add-form! #(rf/dispatch [::config-events/set-show-add-form %])
                   :set-editing! #(rf/dispatch [::config-events/set-editing %])
                   :entity-name entity-name
                   :show-add-button? show-add-button?
                   :add-disabled? add-disabled?
                   :add-button-label (:add-button-label props)
                   :on-add-click (or (:on-add-click props)
                                   (when use-modal-forms?
                                     handle-add-click))})

                ($ :div {:class "ds-divider"})

                (when (and (seq active-filters) (not active-inline-filter))
                  ($ compact-active-filters
                    {:entity-type entity-name
                     :active-filters active-filters
                     :on-clear-filter (fn [field-id]
                                        (rf/dispatch [::filter-events/clear-filter entity-name field-id]))}))

                (when (and active-inline-filter inline-filter-field-spec)
                  ($ filter-form
                    {:entity-type entity-name
                     :field-spec inline-filter-field-spec
                     :initial-value inline-filter-value
                     :on-close handle-filter-close
                     :on-apply handle-filter-apply
                     :on-field-switch (fn [new-field]
                                        (rf/dispatch [::filter-events/clear-filter entity-name])
                                        (set-active-inline-filter (keyword (:id new-field)))
                                        (set-inline-filter-field-spec new-field)
                                        (set-inline-filter-value ""))}))

                ($ active-sort-controls
                  {:entity-name entity-name
                   :sorts sorts
                   :field-labels sort-field-labels})

                (let [server-mode? (list-subs/server-pagination? ui-state)
                      total-records (when server-mode?
                                      (or (:total-items ui-state)
                                        (:total ui-state)))
                      record-count (or total-records (count visible-items))
                      selected-locked? (and hardcoded-view-options
                                         (contains? hardcoded-view-options :show-selected-rows?))
                      unselected-locked? (and hardcoded-view-options
                                           (contains? hardcoded-view-options :show-unselected-rows?))
                      row-toggles (cond-> []
                                    (not unselected-locked?)
                                    (conj {:id (str "toggle-unselected-rows-" (kw/ensure-name entity-name))
                                           :label (t :list/toggle-unselected-rows)
                                           :active? (:show-unselected-rows? merged-display-settings)
                                           :on-click #(rf/dispatch [::ui-events/toggle-unselected-rows entity-kw])})
                                    (not selected-locked?)
                                    (conj {:id (str "toggle-selected-rows-" (kw/ensure-name entity-name))
                                           :label (t :list/toggle-selected-rows)
                                           :active? (:show-selected-rows? merged-display-settings)
                                           :on-click #(rf/dispatch [::ui-events/toggle-selected-rows entity-kw])}))
                      extra-groups (:extra-settings-toggle-groups props)]
                  ($ :div {:class "flex items-center justify-between gap-4 flex-wrap"}
                    ($ :div {:id (str "selected-count-" (kw/ensure-name entity-name))
                             :class "flex items-center gap-2 text-sm text-base-content/70"}
                      ($ :span {:class "font-semibold"}
                        (str record-count " " (if (= record-count 1)
                                                (t :common/record-singular)
                                                (t :common/record-plural))))
                      (when (pos? selected-count)
                        ($ :span
                          (str "(" selected-count " " (t :common/selected)
                            (when (pos? hidden-selected-count)
                              (str ", " hidden-selected-count " " (t :common/hidden)))
                            ")"))))
                    ($ :div {:class "flex items-center gap-2 flex-wrap"}
                      (map toggle-group-pill extra-groups)
                      (toggle-group-pill {:id (str "toggle-group-row-visibility-" (kw/ensure-name entity-name))
                                          :toggles row-toggles}))))

                ($ :div {:id (str "table-shell-" (kw/ensure-name entity-name))
                         :ref shell-ref
                         :class "w-full flex flex-col overflow-hidden rounded-xl border border-base-300 bg-base-100 shadow-sm"
                         :style {:height (if (and table-height (pos? table-height))
                                           (str table-height "px")
                                           "min(70vh, calc(100vh - 12rem))")
                                 :min-height "150px"
                                 :max-height "calc(100vh - 6rem)"
                                 :resize "both"}}
                  ($ :div {:id (str "table-scroll-viewport-" (kw/ensure-name entity-name))
                           :class "min-h-0 flex-1 overflow-auto scroll-smooth overscroll-contain"
                           :style {:scrollbarGutter "stable"
                                   :WebkitOverflowScrolling "touch"}}
                    ($ table
                      {:headers table-headers
                       :rows items-vec
                       :row-key (fn [item]
                                  (id-utils/extract-entity-id item))
                       :entity-name entity-name
                       :entity-spec entity-spec
                       :measured-table-height measured-table-height
                       :measured-table-width measured-table-width
                       :show-highlights? show-highlights?
                       :actions-column-width (:actions-column-width props)
                       :render-row render-row-fn
                       :render-row-expansion (:render-row-expansion props)
                       :display-settings merged-display-settings
                       :page-display-settings hardcoded-view-options
                       :per-page effective-per-page
                       :on-per-page-change on-per-page-change
                       :rows-per-page-options [5 10 20 25 50 100]
                       :extra-settings-toggles (:extra-settings-toggles props)}))
                  (when (and (get merged-display-settings :show-pagination? true)
                          (> pagination-total-pages 1))
                    ($ :div {:id (str "table-pagination-" (kw/ensure-name entity-name))
                             :class "shrink-0 border-t border-base-300 bg-base-200"}
                      ($ pagination
                        {:current-page pagination-current-page
                         :total-pages pagination-total-pages
                         :on-page-change on-page-change
                         :per-page effective-per-page
                         :on-per-page-change on-per-page-change
                         :rows-per-page-options [5 10 20 25 50 100]
                         :entity-name entity-name}))))))))))))
