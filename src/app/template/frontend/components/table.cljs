(ns app.template.frontend.components.table
  (:require
    [app.shared.keywords :as kw]
    [app.template.frontend.components.icons :refer [settings-icon]]
    [app.template.frontend.components.settings.list-view-settings :refer [list-view-settings-panel]]
    [app.template.frontend.events.list.settings :as settings-events]
    [app.template.frontend.i18n :refer [use-t]]
    [app.template.frontend.subs.ui :as ui-subs]
    [app.template.frontend.ui.z-scale :as z]
    [uix.core :as uix :refer [$ defui]]
    [uix.dom]
    [uix.re-frame :refer [use-subscribe]]))

(defn get-array-length [cells]
  (cond
    (array? cells) (.-length cells)
    (nil? cells) 0
    (seqable? cells) (count cells)
    :else 1))

(defn ensure-seq [cells]
  (cond
    (array? cells) (array-seq cells)
    (nil? cells) []
    :else (if (seqable? cells) cells [cells])))

(defui resizable-cell
  "A table cell with a resizable handle"
  [{:keys [is-header? index width on-resize children resizable? fixed-width colspan sticky? sticky-position sticky-z-index]}]
  (let [cell-type (if is-header? :th :td)
        [resizing? set-resizing!] (uix/use-state false)
        [cell-width set-cell-width] (uix/use-state (or width "auto"))

        handle-mouse-down (fn [e]
                            (.preventDefault e)
                            (.stopPropagation e)
                            (set-resizing! true)

                            (let [start-x (.-clientX e)
                                  header-element (.. e -target -parentElement)
                                  start-width (.-offsetWidth header-element)

                                  handle-mouse-move (fn [move-event]
                                                      (.preventDefault move-event)
                                                      (let [current-x (.-clientX move-event)
                                                            delta (- current-x start-x)
                                                            new-width (+ start-width delta)]
                                                        (when (> new-width 50)
                                                          (let [width-px (str new-width "px")]
                                                            (set-cell-width width-px)
                                                            (when on-resize
                                                              (on-resize index new-width))))))

                                  cleanup-fn (atom nil)
                                  handle-mouse-up (fn [up-event]
                                                    (.preventDefault up-event)
                                                    (set-resizing! false)
                                                    (when @cleanup-fn
                                                      (@cleanup-fn)))]
                              (reset! cleanup-fn
                                (fn []
                                  (.removeEventListener js/document "mousemove" handle-mouse-move)
                                  (.removeEventListener js/document "mouseup" handle-mouse-up)))

                              (.addEventListener js/document "mousemove" handle-mouse-move)
                              (.addEventListener js/document "mouseup" handle-mouse-up)))

        ;; <th> z-indices are LOCAL to the sticky <thead>'s stacking context,
        ;; so the absolute number doesn't compete globally — it just orders
        ;; siblings inside the header row. Sticky header columns sit above
        ;; non-sticky ones in case of any horizontal overlap.
        header-style (when is-header?
                       {:position "sticky"
                        :top 0
                        :z-index (if sticky? z/local-high z/local-low)})

        ;; Sticky <td> body cells, by contrast, compete in the document's
        ;; root stacking context — their z-index must come from the global
        ;; scale and stay below modal-backdrop.
        sticky-style (when sticky?
                       (merge
                         {:position "sticky"
                          :z-index (cond
                                     is-header? z/local-high
                                     :else (or sticky-z-index z/table-sticky-body))
                          :overflow "visible"}
                         (case sticky-position
                           :left {:left 0}
                           :right {:right 0}
                           {})))

        ;; Sticky cells repaint during vertical scrolling. Keep them opaque and
        ;; avoid transition/backdrop-filter classes so SVG icons do not shimmer
        ;; while the scroll viewport moves.
        header-class (when is-header?
                       (str "bg-base-100 shadow-[inset_0_-1px_0_rgba(15,23,42,0.08)] "
                         (case sticky-position
                           :left "border-r border-base-300/60 "
                           :right "border-l border-base-300/60 "
                           "")))

        sticky-body-class (when (and sticky? (not is-header?))
                            (str "bg-base-100 "
                              (case sticky-position
                                :left "border-r border-base-300/40 shadow-[8px_0_16px_-12px_rgba(15,23,42,0.22)] "
                                :right "border-l border-base-300/40 shadow-[-8px_0_16px_-12px_rgba(15,23,42,0.22)] "
                                "")))]

    (uix/use-effect
      (fn []
        (set-cell-width (or width "auto"))
        js/undefined)
      [width])

    ($ cell-type
      {:key index
       :class (str "table-cell relative "
                (when resizing? "select-none ")
                (when-not is-header? "p-2 ")
                (when is-header? "px-3 py-4 align-top text-left font-medium text-base-content border-r border-base-300/30 last:border-r-0 ")
                header-class
                sticky-body-class)
       :colSpan (when (and colspan (not is-header?)) colspan)
       :data-column-index (when-not is-header? index)
       :style (merge
                {:overflow (if (or is-header? sticky?) "visible" "hidden")}
                (if fixed-width
                  {:width fixed-width
                   :min-width fixed-width
                   :max-width fixed-width
                   :box-sizing "border-box"
                   :position "relative"}
                  {:width cell-width
                   :min-width "50px"
                   :box-sizing "border-box"
                   :position "relative"})
                header-style
                sticky-style)}

      (if is-header?
        ($ :span {:class (str "block h-full w-full whitespace-nowrap truncate "
                           (when sticky? "font-bold text-primary "))}
          children
          (when sticky?
            ($ :span {:class "absolute bottom-0 text-[10px] opacity-60 leading-none"
                      :style {:right (when (= sticky-position :left) "0")
                              :left (when (= sticky-position :right) "0")}}
              "📌")))
        ($ :div {:class "flex items-center h-full w-full min-w-0"
                 :style {:overflow-wrap "anywhere"
                         :word-break "break-word"}}
          children))

      (when (and is-header? resizable? (not sticky?))
        ($ :div
          {:class "absolute top-0 right-0 h-full w-2 cursor-col-resize hover:bg-blue-300 active:bg-blue-500 z-10"
           :on-mouse-down handle-mouse-down})))))

(defui row
  {:prop-types {:cells {:type :array :required true}
                :class {:type :string}
                :num-columns {:type :number}
                :entity-name {:type :string}
                :row-index {:type :number}
                :is-header? {:type :boolean}
                :column-widths {:type :array}
                :on-column-resize {:type :function}
                :resizable-columns {:type :array}
                :fixed-width-columns {:type :array}
                :sticky-columns {:type :array}}}
  [{:keys [cells class num-columns is-header? entity-name row-index column-widths on-column-resize resizable-columns fixed-width-columns sticky-columns] :as _props}]
  (let [cell-count (get-array-length cells)
        colspan (when (= cell-count 1) num-columns)
        cells-seq (ensure-seq cells)]

    ($ :tr {:class class
            :id (when (and entity-name row-index)
                  (str "row-" (kw/ensure-name entity-name) "-" row-index))}
      (map-indexed
        (fn [index cell]
          ;;_ (println "cell: " (cond
          ;;                      (fn? cell) (cell)
          ;;                      (vector? cell) (first cell) ;; Handle vector form of components
          ;;                      :else cell))
          ;; Determine if this column should be sticky
          (let [sticky-info (get sticky-columns index)
                sticky? (boolean sticky-info)
                sticky-position (when sticky? (:position sticky-info))
                ;; Right-side body cells get a higher z-index than the
                ;; left-side sticky cells so the actions column always
                ;; visually wins where they overlap at row ends.
                sticky-z-index (when sticky?
                                 (cond
                                   (and (not is-header?) (= sticky-position :right)) z/table-sticky-actions
                                   :else (:z-index sticky-info)))]
            ;; Always use resizable-cell to maintain consistent hook ordering
            ($ resizable-cell
              {:key index
               :is-header? is-header?
               :index index
               :width (get column-widths index)
               :on-resize on-column-resize
               :resizable? (and is-header? (get resizable-columns index true) (not sticky?)) ; Not resizable if sticky
               :fixed-width (get fixed-width-columns index nil)
               :sticky? sticky?
               :sticky-position sticky-position
               :sticky-z-index sticky-z-index
               :colspan (when colspan colspan)}
              (cond
                (fn? cell) (cell)
                (vector? cell) (first cell)                 ;; Handle vector form of components
                :else cell))))
        cells-seq))))

(def table-props
  {:headers {:type :array :required true}
   :rows {:type :array :required true}
   :row-key {:type :function :required true}
   :render-row {:type :function :required true}
   :entity-name {:type :string :required false}
   :entity-spec {:type :any :required false}
   :editing {:type :any :required false}
   :show-highlights? {:type :boolean :required false}
   :measured-table-height {:type :number :required false}
   :measured-table-width {:type :number :required false}
   :actions-column-width {:type :string :required false}
   :pagination {:type :any :required false}})

(def sticky-thead-props
  "Props for the sticky `<thead>` wrapper. Uses inline `:style` for the
  z-index because Tailwind v4's JIT scans literal class names in source —
  building `z-[X]` from a constant via `str` would not produce any CSS."
  {:class "sticky top-0 bg-base-100"
   :style {:z-index z/table-sticky-header}})

(def settings-row-cell-class
  "px-2 py-1 bg-base-200")

(defui table
  {:prop-types table-props}
  [{:keys [headers rows row-key render-row render-row-expansion editing entity-name entity-spec measured-table-height measured-table-width _display-settings _page-display-settings
           per-page on-per-page-change rows-per-page-options extra-settings-toggles actions-column-width]
    :as props}]
  (let [t (use-t)
        header-cells (ensure-seq headers)
        header-count (count header-cells)
        ;; FIXED: Use proper nil check instead of falsy check to handle explicit false values
        show-highlights? (if (contains? props :show-highlights?)
                           (:show-highlights? props)
                           true)
        [column-widths set-column-widths] (uix/use-state (vec (repeat header-count nil)))
        [settings-panel-visible? set-settings-panel-visible!] (uix/use-state false)
        [hovering-icon? set-hovering-icon!] (uix/use-state false)

        ;; Subscribe to table width configuration
        table-width (use-subscribe [::settings-events/table-width (some-> entity-name keyword)])

        ;; Determine which columns should be resizable
        ;; By default, all columns are resizable except select, edit, and delete columns
        resizable-columns (let [header-count (count header-cells)]
                            (vec
                              (map-indexed
                                (fn [idx _]
                                  ;; First column (select) and last column (actions) are not resizable
                                  (not (or (= idx 0)
                                         (= idx (- header-count 1)))))
                                header-cells)))

        ;; Set fixed widths for non-resizable columns
        fixed-width-columns (let [header-count (count header-cells)]
                              (vec
                                (map-indexed
                                  (fn [idx _]
                                    (cond
                                      ;; Select column gets fixed width
                                      (= idx 0) "50px"
                                      ;; Actions column gets more width for circular buttons
                                      (= idx (- header-count 1)) (or actions-column-width "150px")
                                      ;; All other columns are dynamically sized
                                      :else nil))
                                  header-cells)))

        ;; Configure sticky columns (first and last columns).
        ;; The :z-index here is the *body cell* value passed through to
        ;; resizable-cell. Header cells ignore it (they use the local
        ;; thead-stacking-context value via header-style/sticky-style),
        ;; and right-side body cells get overridden to table-sticky-actions
        ;; in the row component below.
        sticky-columns (let [header-count (count header-cells)]
                         (vec
                           (map-indexed
                             (fn [idx _]
                               (cond
                                 ;; First column (select) sticks to left
                                 (= idx 0) {:position :left :z-index z/table-sticky-body}
                                 ;; Last column (actions) sticks to right
                                 (= idx (- header-count 1)) {:position :right :z-index z/table-sticky-body}
                                 ;; Other columns are not sticky
                                 :else nil))
                             header-cells)))

        handle-column-resize (fn [index width]
                               (set-column-widths
                                 (fn [current-widths]
                                   (assoc current-widths index (str width "px")))))

        ;; Use entity name for list-view-settings if available
        effective-entity-name (when entity-name (keyword entity-name))

        ;; Get hardcoded settings from view-options.edn via subscription
        ;; These are settings that are locked by admins and can't be changed by users
        hardcoded-settings (use-subscribe [::ui-subs/hardcoded-view-options effective-entity-name])]

    ($ :div {:id (when entity-name (str "table-" (kw/ensure-name entity-name)))
             :class "w-full"
             :style (cond-> {}
                      table-width (assoc :max-width (str table-width "px")))}
      ($ :table {:class "ds-table relative border-separate"
                 :style {:table-layout "fixed"
                         :border-spacing "0"
                         :min-width "800px"}}
        ($ :thead sticky-thead-props
          ($ row
            {:key "header"
             :cells header-cells
             :class "text-base"
             :is-header? true
             :num-columns (count header-cells)
             :entity-name entity-name
             :column-widths column-widths
             :resizable-columns resizable-columns
             :fixed-width-columns fixed-width-columns
             :sticky-columns sticky-columns
             :on-column-resize handle-column-resize})

          ;; List view settings row - always visible between header and body
          ($ :tr {:class "list-view-settings-row bg-base-200"}
            ($ :td {:colSpan header-count
                    :class settings-row-cell-class}
              ($ :div {:class "flex flex-nowrap gap-2 justify-left overflow-x-auto"}
                ($ :span {:id "btn-settings-toggle"
                          :class "flex items-center text-primary ml-2 p-1 cursor-pointer relative z-100"
                          :on-mouse-enter #(set-hovering-icon! true)
                          :on-mouse-leave #(set-hovering-icon! false)
                          :on-click #(set-settings-panel-visible! (not settings-panel-visible?))}
                  ($ settings-icon {:active? settings-panel-visible?}))
                (when (and hovering-icon? (not settings-panel-visible?))
                  ($ :span {:class "relative whitespace-nowrap px-2 py-1 rounded shadow-md z-10"}
                    (t :list/settings-tooltip)))
                (when settings-panel-visible?
                  ($ list-view-settings-panel {:entity-name effective-entity-name
                                               :current-entity-name effective-entity-name
                                               :entity-spec entity-spec
                                               :compact? true
                                               :measured-table-height measured-table-height
                                               :measured-table-width measured-table-width
                                               ;; Pass hardcoded settings from view-options.edn
                                               ;; These controls will be hidden in the settings panel
                                               :hardcoded-display-settings hardcoded-settings
                                               ;; Pass rows per page props so it's always available
                                               :per-page per-page
                                               :on-per-page-change on-per-page-change
                                               :rows-per-page-options rows-per-page-options
                                               :extra-toggles extra-settings-toggles}))))))
        ($ :tbody
          (mapcat
            (fn [[idx row-data]]
              (let [row-id (row-key row-data)
                    rendered-result (render-row row-data editing)
                    ;; Extract cells and highlight flags from the result
                    cells (if (and (map? rendered-result) (:cells rendered-result))
                            (:cells rendered-result)
                            rendered-result)
                    recently-updated? (boolean
                                        (and (map? rendered-result)
                                          (:recently-updated? rendered-result)))
                    recently-created? (boolean
                                        (and (map? rendered-result)
                                          (:recently-created? rendered-result)))
                    selected? (boolean
                                (and (map? rendered-result)
                                  (:selected? rendered-result)))
                    is-api-failure? (boolean
                                      (and (map? rendered-result)
                                        (:is-api-failure? rendered-result)))
                    ;; Apply different highlight classes based on status and if highlights are shown
                    ;; API failure rows are always highlighted red regardless of the highlight toggle
                    highlight-class (cond
                                      is-api-failure? " bg-red-100/70 hover:bg-red-200/70"
                                      (not show-highlights?) ""
                                      recently-updated? " bg-green-200/50"
                                      recently-created? " bg-blue-200/50"
                                      :else "")
                    selection-class (if selected? " bg-primary/5" "")
                    main-row ($ row
                               {:key (str "row-" idx "-" row-id)
                                :cells cells
                                :class (str "ds-hover"
                                         highlight-class
                                         selection-class
                                         (when-let [row-class (and (map? rendered-result)
                                                                (:row-class rendered-result))]
                                           (str " " row-class)))
                                :num-columns header-count
                                :is-header? false
                                :entity-name entity-name
                                :row-index idx
                                :column-widths column-widths
                                :resizable-columns resizable-columns
                                :fixed-width-columns fixed-width-columns
                                :sticky-columns sticky-columns})
                    expansion-content (when render-row-expansion
                                        (render-row-expansion row-data))]
                (if expansion-content
                  [main-row
                   ($ :tr {:key (str "exp-" idx "-" row-id)}
                     ($ :td {:colSpan header-count :class "p-0"}
                       expansion-content))]
                  [main-row])))
            (map-indexed vector (ensure-seq rows))))))))

;; Removed the batch edit form from here
