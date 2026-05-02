(ns app.domain.frontend.expenses.pages.user.expenses-list
  "User-facing expense list page.

   Uses template list-view UX but fetches data via user-scoped endpoints.

   IMPORTANT: This page is configured for :server pagination mode so sorting is
   always server-side across the full dataset (not just the currently loaded
   page)."
  (:require
    [app.domain.frontend.expenses.components.manual-expense-form.core :as manual-form]
    [app.domain.frontend.expenses.components.user-expense-form :refer [user-expense-edit-form-modal]]
    [app.domain.frontend.expenses.events.expense-items :as expense-items-events]
    app.domain.frontend.expenses.events.source-filter
    app.domain.frontend.expenses.events.user-expenses.receipts
    [app.domain.frontend.expenses.pages.user.expense-detail :refer [expense-detail-page]]
    [app.domain.frontend.expenses.pages.user.receipts-list :as receipts-list-page]
    [app.domain.frontend.expenses.subs.expense-items :as expense-items-subs]
    [app.template.frontend.components.button :refer [button]]
    [app.template.frontend.components.confirm-dialog :as confirm-dialog]
    [app.template.frontend.components.dropdown.action :as dropdown]
    [app.template.frontend.components.icons :refer [chevron-right-icon delete-icon edit-icon view-icon]]
    [app.template.frontend.components.list :refer [list-view]]
    [app.template.frontend.components.modal-wrapper :refer [modal-wrapper]]
    [app.template.frontend.events.list.ui-state :as list-ui-state-events]
    [app.template.frontend.i18n :refer [use-t]]
    [app.template.frontend.utils.id :as id-utils]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui use-callback use-effect use-state]]
    [uix.re-frame :refer [use-subscribe]]

    ;; Ensure subs are registered
    app.domain.frontend.expenses.subs.user-expenses))

;; =============================================================================
;; Modal form renderers
;; =============================================================================

(defn- expense-receipt-id
  [expense]
  (or (:receipt-id expense)
    (:receipt_id expense)))

(defn- manual-expense?
  [expense]
  (nil? (expense-receipt-id expense)))

(defn- render-add-form
  [{:keys [on-success on-cancel]}]
  ($ manual-form/manual-expense-form
    {:on-cancel on-cancel
     :on-submit (fn [form-data]
                  (rf/dispatch [:user-expenses/create-expense-modal
                                form-data
                                on-success]))}))

(defn- render-edit-form
  [item {:keys [on-success on-cancel]}]
  (let [expense-id (id-utils/extract-entity-id item)
        receipt-id-str (some-> (expense-receipt-id item) str)]
    (if receipt-id-str
      ($ :div {:class "space-y-4 p-2"}
        ($ :div {:class "ds-alert ds-alert-info"}
          ($ :span "This expense was created from a receipt. Edit the linked receipt instead."))
        ($ :div {:class "flex justify-end gap-2"}
          (when on-cancel
            ($ button {:id (str "btn-close-linked-expense-edit-" expense-id)
                       :btn-type :ghost
                       :on-click (fn [_]
                                   (on-cancel))}
              "Close"))
          ($ button {:id (str "btn-open-linked-receipt-" receipt-id-str)
                     :btn-type :primary
                     :on-click (fn [_]
                                 (when on-cancel
                                   (on-cancel))
                                 (rf/dispatch [:user-expenses/open-receipt-detail-modal receipt-id-str]))}
            "Edit receipt")))
      ;; IMPORTANT: do NOT pass list-row data as :initial-data.
      ;;
      ;; The list row is intentionally "summary" data and often does not include
      ;; nested detail like :items. Fork forms do not re-initialize when initial
      ;; values change, so the first open could render without line items.
      ;;
      ;; We let the modal fetch the full detail and mount the form once it is loaded.
      ($ user-expense-edit-form-modal
        {:expense-id expense-id
         :initial-data nil
         :on-success on-success
         :on-cancel on-cancel}))))

(defn- render-actions
  "Row action dropdown (admin-style) for user expenses.

   Supports an optional :on-view callback for opening a custom modal.
   Supports expand chevron when :on-toggle-expand is provided and item has items."
  [t item {:keys [on-view on-toggle-expand expanded? power-user? open-linked-receipt!]}]
  (let [expense-id (id-utils/extract-entity-id item)
        receipt-id-str (some-> (expense-receipt-id item) str)
        item-count (or (:item-count item) (:item_count item) 0)
        show-expand? (and power-user? (pos? item-count))
        on-edit-click (:on-edit-click item)
        manual? (manual-expense? item)
        show-edit? (not (false? (:show-edit? item)))
        show-delete? (not (false? (:show-delete? item)))
        item-data (dissoc item :show-edit? :show-delete? :edit-disabled? :delete-disabled? :on-edit-click)
        edit-disabled? (true? (:edit-disabled? item))
        delete-disabled? (true? (:delete-disabled? item))]
    ($ :div {:class "flex items-center justify-center gap-2"}
      (when show-expand?
        ($ button
          {:id (str "btn-expand-expenses-" expense-id)
           :btn-type :ghost
           :shape "circle"
           :title (if expanded? "Collapse items" "Expand items")
           :on-click (fn [e]
                       (.stopPropagation e)
                       (on-toggle-expand expense-id))}
          ($ :span {:class (str "inline-flex transition-transform duration-150"
                             (when expanded? " rotate-90"))}
            ($ chevron-right-icon))))

      (when show-edit?
        (if manual?
          ($ button
            {:id (str "btn-edit-expenses-" expense-id)
             :btn-type :primary
             :shape "circle"
             :disabled edit-disabled?
             :on-click (fn [e]
                         (.stopPropagation e)
                         (when-not edit-disabled?
                           (if on-edit-click
                             (on-edit-click item-data)
                             (rf/dispatch [:navigate-to (str "/expenses/" expense-id "?edit=true")]))))}
            ($ edit-icon))
          ($ button
            {:id (str "btn-edit-receipt-for-expense-" expense-id)
             :btn-type :primary
             :shape "circle"
             :title "Edit linked receipt"
             :disabled edit-disabled?
             :on-click (fn [e]
                         (.stopPropagation e)
                         (when-not edit-disabled?
                           (when (and open-linked-receipt! receipt-id-str)
                             (open-linked-receipt! receipt-id-str))))}
            ($ edit-icon))))

      (when show-delete?
        ($ button
          {:id (str "btn-delete-expenses-" expense-id)
           :btn-type :danger
           :shape "circle"
           :disabled delete-disabled?
           :on-click (fn [e]
                       (.stopPropagation e)
                       (when-not delete-disabled?
                         (confirm-dialog/show-confirm
                           {:title (t :expenses-list/delete-title)
                            :message (t :expenses-list/delete-msg)
                            :on-confirm #(rf/dispatch [:user-expenses/delete-expense expense-id])
                            :on-cancel nil})))
           :title (when delete-disabled? "Delete not available")}
          ($ delete-icon)))

      ($ dropdown/action-dropdown
        {:entity-id expense-id
         :trigger-label "⋯"
         :position :portal
         :actions
         [{:group-title (t :common/view)
           :items [{:id "view"
                    :icon ($ view-icon {:title (t :expenses-list/view-details)})
                    :label (t :expenses-list/view-details)
                    :on-click (fn [e]
                                (.stopPropagation e)
                                (if on-view
                                  (on-view item)
                                  (rf/dispatch [:navigate-to (str "/expenses/" expense-id)])))}]}]}))))

;; =============================================================================
;; Main Page
;; =============================================================================

(defui expense-items-expand-row
  "Renders line items for a single expense in readonly mode.
   Used as the row-expansion panel in the expense list."
  [{:keys [expense-id]}]
  (let [items (use-subscribe [::expense-items-subs/items-for-expense expense-id])
        loading? (use-subscribe [::expense-items-subs/loading-for-expense? expense-id])]
    ($ :div {:class "px-6 py-3 bg-base-50 border-b border-base-200"}
      (if loading?
        ;; Skeleton rows while fetching
        ($ :div {:class "space-y-2 py-1"}
          (for [i (range 3)]
            ($ :div {:key i :class "h-4 bg-base-200 rounded animate-pulse"})))
        ;; Readonly items table
        (if (empty? items)
          ($ :div {:class "text-sm text-base-content/50 py-1"} "No items")
          ($ :table {:class "w-full text-sm"}
            ($ :thead
              ($ :tr {:class "text-left text-xs text-base-content/50 uppercase border-b border-base-200"}
                ($ :th {:class "py-1.5 pr-4 font-medium"} "Article")
                ($ :th {:class "py-1.5 pr-4 font-medium text-right"} "Qty")
                ($ :th {:class "py-1.5 pr-4 font-medium text-right"} "Unit Price")
                ($ :th {:class "py-1.5 font-medium text-right"} "Total")))
            ($ :tbody
              (for [item items]
                ($ :tr {:key (or (:id item) (str item))
                        :class "border-b border-base-100 last:border-0"}
                  ($ :td {:class "py-1.5 pr-4 text-base-content"}
                    (or (:article-canonical-name item)
                      (:article_canonical_name item)
                      ($ :span {:class "text-base-content/30"} "—")))
                  ($ :td {:class "py-1.5 pr-4 text-right tabular-nums text-base-content/70"}
                    (or (:qty item) "—"))
                  ($ :td {:class "py-1.5 pr-4 text-right tabular-nums text-base-content/70"}
                    (if-let [up (or (:unit-price item) (:unit_price item))]
                      (str up)
                      "—"))
                  ($ :td {:class "py-1.5 text-right tabular-nums font-medium"}
                    (or (:line-total item) (:line_total item) "—")))))))))))

(defui expenses-list-page
  []
  (let [t (use-t)
        entity-name :expenses
        [viewing-id set-viewing-id!] (use-state nil)
        [expanded-ids set-expanded-ids!] (use-state #{})
        power-user? (boolean (use-subscribe [:expenses/power-user?]))
        {:keys [show-manual? show-receipts?]} (use-subscribe [:expenses/source-filter])
        ;; Use shared entity specs when available; fall back to nil which
        ;; list-view can still handle for basic rendering.
        entity-spec (use-subscribe [:entity-specs/by-name entity-name])
        ;; Subscribe to current expense being viewed in modal
        current-expense (use-subscribe [:user-expenses/current-expense])
        refresh-list (use-callback
                       (fn []
                         (rf/dispatch [:user-expenses/refresh-expenses-list]))
                       [])
        toggle-expand (use-callback
                        (fn [expense-id]
                          (set-expanded-ids!
                            (fn [ids]
                              (if (contains? ids expense-id)
                                (disj ids expense-id)
                                (conj ids expense-id))))
                          (rf/dispatch [::expense-items-events/fetch-items-for-expense expense-id]))
                        [])
        open-linked-receipt! (use-callback
                               (fn [receipt-id]
                                 (when-let [receipt-id* (some-> receipt-id str)]
                                   (rf/dispatch [:user-expenses/open-receipt-detail-modal receipt-id*])))
                               [])]

    ;; Initial load + list-view wiring for server pagination/sorting
    (use-effect
      (fn []
        (rf/dispatch [::list-ui-state-events/set-pagination-mode :expenses :server])
        (rf/dispatch [::list-ui-state-events/set-refresh-event :expenses [:user-expenses/refresh-expenses-list]])
        (refresh-list)

        ;; Reference data for FK columns in the list-view (supplier & payer)
        (rf/dispatch [:user-expenses/fetch-suppliers {:limit 100 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-payers {:limit 100 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-expense-categories {:limit 500 :offset 0}])
        (rf/dispatch [:user-expenses/fetch-expense-contexts {:limit 500 :offset 0}])
        js/undefined)
      [refresh-list])

    ($ :div {:class "min-h-screen bg-base-100"}
      ;; Header
      ($ :header {:class "bg-white border-b border-base-200"}
        ($ :div {:class "w-full px-4 py-4 sm:py-6"}
          ($ :div {:class "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4"}
            ($ :div
              ($ :h1 {:class "text-xl sm:text-2xl font-bold text-base-content"} (t :expenses-list/title))
              ($ :p {:class "text-sm text-base-content/70"}
                (t :expenses-list/subtitle)))
            ($ :div {:class "flex gap-2"}
              ($ button {:btn-type :ghost
                         :on-click #(rf/dispatch [:navigate-to "/expenses"])}
                (t :expenses-list/btn-dashboard))))))

      ;; Main content: list-view backed by shared entity store
      ($ :main {:class "w-full px-4 py-6"}
        ($ list-view
          {:entity-name entity-name
           :entity-spec entity-spec
           :render-add-form render-add-form
           :render-edit-form render-edit-form
           :on-add-success refresh-list
           :on-edit-success refresh-list
           :row-class-fn (fn [item]
                           (when (manual-expense? item) "bg-slate-200/70"))
           :extra-settings-toggle-groups
           [{:id "toggle-group-expense-source"
             :toggles [{:id "toggle-show-manual-expenses"
                        :label (t :expenses-list/source-manual)
                        :active? show-manual?
                        :on-click #(rf/dispatch [:expenses/toggle-source-filter
                                                 :manual
                                                 [:user-expenses/refresh-expenses-list]])}
                       {:id "toggle-show-receipt-expenses"
                        :label (t :expenses-list/source-receipts)
                        :active? show-receipts?
                        :on-click #(rf/dispatch [:expenses/toggle-source-filter
                                                 :receipts
                                                 [:user-expenses/refresh-expenses-list]])}]}]
           :render-actions (fn [item]
                             (let [expense-id (id-utils/extract-entity-id item)]
                               (render-actions t item
                                 {:on-view #(set-viewing-id! (id-utils/extract-entity-id %))
                                  :on-toggle-expand toggle-expand
                                  :expanded? (contains? expanded-ids expense-id)
                                  :power-user? power-user?
                                  :open-linked-receipt! open-linked-receipt!})))
           :render-row-expansion (when power-user?
                                   (fn [item]
                                     (let [expense-id (id-utils/extract-entity-id item)
                                           item-count (or (:item-count item) (:item_count item) 0)]
                                       (when (and (pos? item-count)
                                               (contains? expanded-ids expense-id))
                                         ($ expense-items-expand-row {:expense-id expense-id})))))}))

      (when viewing-id
        (let [supplier-name (or (:supplier_display_name current-expense) "Expense Details")]
          ($ modal-wrapper
            {:id (str "modal-view-" viewing-id)
             :visible? true
             :title supplier-name
             :breadcrumbs [{:label "Expenses" :href "/expenses"}
                           {:label "All Expenses" :href "/expenses/list"}
                           {:label supplier-name}]
             :size :large
             :draggable? true
             :resizable? true
             :close-button-id (str "btn-close-expense-details-" viewing-id)
             :on-close #(set-viewing-id! nil)}
            ($ expense-detail-page {:expense-id viewing-id
                                    :in-modal? true}))))

      ($ receipts-list-page/receipt-detail-modal))))
