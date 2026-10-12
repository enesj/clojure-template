(ns app.domain.frontend.expenses.pages.user.receipts-list
  "User-facing receipts inbox (review + approve) using template list-view UX."
  (:require
    [app.domain.frontend.expenses.components.receipt-detail-modal :as receipt-detail-ui]
    [app.domain.frontend.expenses.components.user-expense-form :refer [user-expense-add-form-modal]]

    [app.template.frontend.components.button :refer [button]]
    [app.template.frontend.components.icons :refer [check-circle document-text-icon]]
    [app.template.frontend.components.list :refer [list-view]]
    [app.template.frontend.components.list.cells :as list-cells]
    [app.template.frontend.events.list.ui-state :as list-ui-state-events]
    [app.template.frontend.i18n :refer [use-t]]
    [app.template.frontend.subs.list :as list-subs]
    [app.template.frontend.utils.id :as id-utils]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui use-callback use-effect use-state]]
    [uix.re-frame :refer [use-subscribe]]))

(def ^:private stable-receipt-status-filter-options
  [{:value "extracted" :label :receipts/status-extracted}
   {:value "review_required" :label :receipts/status-review-required}
   {:value "failed" :label :receipts/status-failed}])

(defn- receipts-entity-spec
  [t]
  {:id :receipts
   :fields [{:id :original-filename
             :label (t :common/original-filename)
             :type :text}
            {:id :status
             :label (t :common/status)
             :type :select
             :input-type "select"
             :display-source-field :receipt-status-display
             :options (mapv (fn [{:keys [value label]}]
                              {:value value
                               :label (t label)})
                        stable-receipt-status-filter-options)}
            {:id :supplier-guess
             :label (t :common/supplier-guess)
             :type :text}
            {:id :purchased-at-guess
             :label (t :common/purchased-at-guess)
             :type :datetime}
            {:id :total-display
             :label (t :common/total)
             :type :text}

            {:id :created-at
             :label (t :common/created-at)
             :type :datetime}
            {:id :updated-at
             :label (t :common/updated-at)
             :type :datetime}]})

(def ^:private receipt-processing-statuses
  #{"uploaded" "parsing" "parsed" "extracting" "refining"})

(defn- receipt-processing?
  [status]
  (contains? receipt-processing-statuses status))

(defn- receipt-refine-pending?
  [receipt]
  (let [refine-pending (or (:refine-pending receipt)
                         (:refine_pending receipt)
                         (get-in receipt [:raw-extract-json :refine-pending])
                         (get-in receipt [:raw-extract-json :refine_pending])
                         (get-in receipt [:raw_extract_json :refine_pending]))]
    (true? refine-pending)))

(defn- pad2
  [n]
  (let [s (str (or n 0))]
    (if (= 1 (count s))
      (str "0" s)
      s)))

(defn- format-duration-ms
  "Format a duration in milliseconds as H:MM:SS or M:SS."
  [ms]
  (let [ms (max 0 (long (or ms 0)))
        total-sec (quot ms 1000)
        sec (mod total-sec 60)
        total-min (quot total-sec 60)
        min (mod total-min 60)
        hrs (quot total-min 60)]
    (if (pos? hrs)
      (str hrs ":" (pad2 min) ":" (pad2 sec))
      (str total-min ":" (pad2 sec)))))

(defn- format-duration
  [started-at last-checked]
  (when (and started-at last-checked
          (instance? js/Date started-at)
          (instance? js/Date last-checked))
    (format-duration-ms (- (.getTime last-checked) (.getTime started-at)))))

(def ^:private receipt-detail-ctx
  {:receipt-sub :user-expenses/receipt
   :receipt-detail-loading-sub :user-expenses/receipt-detail-loading?
   :receipt-action-loading-sub :user-expenses/receipt-action-loading?
   :receipts-error-sub :user-expenses/receipts-error
   :modal-open-sub :user-expenses/receipt-detail-modal-open?
   :modal-id-sub :user-expenses/receipt-detail-modal-id
   :fetch-receipt-event :user-expenses/fetch-receipt
   :close-modal [:user-expenses/close-receipt-detail-modal]
   :approve-form user-expense-add-form-modal})

(defui receipt-detail-modal
  []
  ($ receipt-detail-ui/receipt-detail-modal
    {:id "user-receipt-detail-modal"
     :ctx receipt-detail-ctx}))

(defn- receipt-linked?
  [receipt]
  (some? (or (:expense-id receipt)
           (:expense_id receipt)
           (:receipts/expense-id receipt)
           (:receipts/expense_id receipt))))

(declare receipt-purged?)

(defn- receipt-ocr-allowed?
  "Check if OCR action should be shown for a receipt.
   Returns true for statuses where OCR makes sense and the receipt is not already linked to an expense."
  [receipt]
  (let [status (or (:status receipt) (:receipts/status receipt))]
    (and (not (receipt-linked? receipt))
      (contains? #{"uploaded" "failed" "review_required" "extracted" "parsing" "parsed" "extracting"}
        status))))

(defn- receipt-post-allowed?
  "Return true when a receipt can be posted directly from the list row."
  [receipt]
  (let [status (or (:status receipt) (:receipts/status receipt))]
    (and (not (receipt-linked? receipt))
      (not (receipt-purged? receipt))
      (not (receipt-refine-pending? receipt))
      (contains? #{"extracted" "review_required"} status))))

(defn- render-receipt-actions
  [t can-ocr? action-loading? open-receipt-detail! receipt]
  ($ :div {:class "flex items-center gap-2"}
    (when (not (false? (:show-edit? receipt)))
      ($ list-cells/edit-button
        {:entity-name :receipts
         :item-id (id-utils/extract-entity-id receipt)
         :item receipt
         :disabled? (boolean (:edit-disabled? receipt))
         :on-edit-click open-receipt-detail!}))
    (when (not (false? (:show-delete? receipt)))
      ($ list-cells/delete-button
        {:entity-name :receipts
         :item-id (id-utils/extract-entity-id receipt)
         :disabled? (boolean (:delete-disabled? receipt))}))
    (when (receipt-post-allowed? receipt)
      (let [receipt-id (id-utils/extract-entity-id receipt)]
        ($ button {:id (str "btn-post-receipt-" receipt-id)
                   :type "button"
                   :btn-type :success
                   :shape "circle"
                   :title (if can-ocr?
                            (t :receipts/post-one-tooltip)
                            (t :receipts/tooltip-no-post))
                   :aria-label (t :receipts/post-one)
                   :disabled (or (not can-ocr?) action-loading?)
                   :on-click (fn [e]
                               (.stopPropagation e)
                               (.preventDefault e)
                               (when (and can-ocr? (not action-loading?))
                                 (rf/dispatch [:user-expenses/post-selected [receipt-id]])))}
          ($ check-circle))))
    (when (receipt-ocr-allowed? receipt)
      (let [receipt-id (id-utils/extract-entity-id receipt)]
        ($ button {:id (str "btn-parse-receipt-" receipt-id)
                   :type "button"
                   :btn-type :info
                   :shape "circle"
                   :title (if can-ocr?
                            (t :receipts/ocr-tooltip)
                            (t :receipts/tooltip-no-ocr))
                   :aria-label (t :receipts/parse-ocr-label)
                   :disabled (or (not can-ocr?) action-loading?)
                   :on-click (fn [e]
                               (.stopPropagation e)
                               (.preventDefault e)
                               (when (and can-ocr? (not action-loading?))
                                 (rf/dispatch [:user-expenses/ocr-receipt receipt-id])))}
          ($ document-text-icon))))))

(defn- receipt-purged?
  [receipt]
  (some? (or (:file-purged-at receipt)
           (:file_purged_at receipt)
           (:receipts/file-purged-at receipt)
           (:receipts/file_purged_at receipt))))

(defn- ->number
  [value]
  (cond
    (number? value) value
    (string? value) (let [n (js/parseFloat (.replace value "," "."))]
                      (when-not (js/isNaN n) n))
    :else nil))

(defn- fmt-amount
  [amount]
  (cond
    (nil? amount) nil
    (number? amount) (.toFixed (js/Number. amount) 2)
    (string? amount) (let [n (->number amount)]
                       (if (some? n)
                         (.toFixed (js/Number. n) 2)
                         amount))
    :else (str amount)))

(defn- amounts-different?
  [a b]
  (let [a* (->number a)
        b* (->number b)]
    (and (some? a*)
      (some? b*)
      (> (js/Math.abs (- a* b*)) 0.009))))

(defn receipt-total-display
  [receipt]
  (let [total (or (:total-amount-guess receipt)
                (:total_amount_guess receipt)
                (:receipts/total-amount-guess receipt)
                (:receipts/total_amount_guess receipt))
        lines-total (or (:lines-total-amount-guess receipt)
                      (:lines_total_amount_guess receipt)
                      (:receipts/lines-total-amount-guess receipt)
                      (:receipts/lines_total_amount_guess receipt))
        currency (or (:currency-guess receipt)
                   (:currency_guess receipt)
                   (:receipts/currency-guess receipt)
                   (:receipts/currency_guess receipt))
        display-amount (or total lines-total)
        amount-str (fmt-amount display-amount)
        lines-str (fmt-amount lines-total)
        currency-str (when (and (string? currency) (not (empty? currency))) currency)
        suffix (when currency-str (str " " currency-str))]
    (cond
      (nil? amount-str) nil
      (and (some? total)
        (some? lines-str)
        (amounts-different? total lines-total))
      (str amount-str suffix " (lines " lines-str ")")

      :else
      (str amount-str suffix))))

(def ^:private receipt-status->label-key
  {"uploaded" :receipts/status-uploaded
   "parsing" :receipts/status-parsing
   "parsed" :receipts/status-parsed
   "extracting" :receipts/status-extracting
   "extracted" :receipts/status-extracted
   "refining" :receipts/status-refining
   "review_required" :receipts/status-review-required
   "approved" :receipts/status-approved
   "posted" :receipts/status-posted
   "failed" :receipts/status-failed
   "purged" :receipts/show-purged})

(defn- receipt-status-display
  [t receipt]
  (let [status (cond
                 (receipt-purged? receipt) "purged"
                 (receipt-refine-pending? receipt) "refining"
                 :else (or (:status receipt)
                         (:receipts/status receipt)))]
    (or (some-> (get receipt-status->label-key status) t)
      status)))

(defn- present-receipt
  [t receipt]
  (let [total-display (receipt-total-display receipt)
        status-display (receipt-status-display t receipt)]
    (cond-> receipt
      (some? status-display)
      (assoc :receipt-status-display status-display)

      (some? total-display)
      (assoc :total-display total-display))))

(defui receipts-list-page
  []
  (let [t (use-t)
        error (use-subscribe [:user-expenses/receipts-error])
        form-error (use-subscribe [:user-expenses/form-error])
        receipts (vec (or (use-subscribe [:user-expenses/receipts]) []))
        display-receipts (mapv #(present-receipt t %) receipts)
        show-purged? (use-subscribe [:user-expenses/show-purged-receipts?])
        purged-count (long (or (use-subscribe [:user-expenses/purged-receipts-total]) 0))
        show-purged-toggle? (pos? purged-count)
        selected-receipt-ids (or (use-subscribe [::list-subs/selected-ids :receipts]) #{})
        selected-count (count selected-receipt-ids)
        action-loading? (true? (use-subscribe [:user-expenses/receipt-action-loading?]))
        can-ocr? (boolean (use-subscribe [:expenses/can-write?]))
        processing-count (->> receipts
                           (filter (fn [receipt]
                                     (receipt-processing? (:status receipt))))
                           count)
        refining-count (->> receipts
                         (filter receipt-refine-pending?)
                         count)
        processing? (or (pos? processing-count) (pos? refining-count))
        show-status-bar? (or processing? show-purged-toggle?)
        processing-label (if (pos? processing-count) (t :receipts/processing) (t :receipts/refining))
        processing-total (if (pos? processing-count) processing-count refining-count)
        [processing-started-at set-processing-started-at!] (use-state nil)
        [last-checked set-last-checked!] (use-state nil)
        refresh! (use-callback
                   (fn []
                     (rf/dispatch [:user-expenses/refresh-receipts-list])
                     (set-last-checked! (js/Date.)))
                   [])
        check-processing-complete! (use-callback
                                     (fn []
                                       (rf/dispatch [:user-expenses/check-receipts-processing-complete])
                                       (set-last-checked! (js/Date.)))
                                     [])
        parse-selected! (use-callback
                          (fn [e]
                            (.preventDefault e)
                            (rf/dispatch [:user-expenses/ocr-selected (vec selected-receipt-ids)]))
                          [selected-receipt-ids])
        post-selected! (use-callback
                         (fn [e]
                           (.preventDefault e)
                           (rf/dispatch [:user-expenses/post-selected (vec selected-receipt-ids)]))
                         [selected-receipt-ids])
        open-receipt-detail-from-edit! (use-callback
                                         (fn [receipt]
                                           (when-let [receipt-id (id-utils/extract-entity-id receipt)]
                                             (rf/dispatch [:user-expenses/open-receipt-detail-modal receipt-id])))
                                         [])]

    (use-effect
      (fn []
        (rf/dispatch [:app.template.frontend.events.list/clear-selection :receipts])
        (rf/dispatch [::list-ui-state-events/set-pagination-mode :receipts :server])
        (rf/dispatch [::list-ui-state-events/set-refresh-event :receipts [:user-expenses/refresh-receipts-list]])
        (refresh!)
        (rf/dispatch [:user-expenses/fetch-payers {:limit 100 :offset 0}])
        js/undefined)
      [refresh!])

    (use-effect
      (fn []
        (when processing?
          (let [handle (js/setInterval check-processing-complete! 3000)]
            (fn []
              (js/clearInterval handle)))))
      [check-processing-complete! processing?])

    (use-effect
      (fn []
        (cond
          (and processing? (nil? processing-started-at))
          (let [now (js/Date.)]
            (set-processing-started-at! now)
            (set-last-checked! now))

          (and (not processing?) (or processing-started-at last-checked))
          (do
            (set-processing-started-at! nil)
            (set-last-checked! nil)))
        js/undefined)
      [processing? processing-started-at last-checked])

    ($ :<>
      ($ :div {:class "min-h-screen bg-base-100"}
        ($ :header {:class "bg-white border-b border-base-200"}
          ($ :div {:class "w-full px-4 py-4 sm:py-6"}
            ($ :div {:class "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4"}
              ($ :div
                ($ :h1 {:class "text-xl sm:text-2xl font-bold text-base-content"} (t :receipts/title))
                ($ :p {:class "text-sm text-base-content/70"}
                  (t :receipts/subtitle))))))
        ($ :main {:class "w-full px-4 py-6"}
          ($ :div {:class "ds-card ds-bg-base-100 ds-shadow-xl"}
            ($ :div {:class "ds-card-body p-0"}
              (when error
                ($ :div {:class "px-4 pt-4"}
                  ($ :div {:class "ds-alert ds-alert-error"}
                    ($ :span (str error)))))

              (when form-error
                ($ :div {:class "px-4 pt-4"}
                  ($ :div {:class "ds-alert ds-alert-error flex items-center justify-between"}
                    ($ :span (str form-error))
                    ($ :button {:id "btn-clear-receipts-form-error"
                                :type "button"
                                :class "ds-btn ds-btn-ghost ds-btn-xs"
                                :on-click (fn [e]
                                            (.preventDefault e)
                                            (rf/dispatch [:user-expenses/clear-form-error]))}
                      "✕"))))

              ($ :div {:class (str "flex items-center gap-2 px-4 pt-4 "
                                (if show-status-bar? "justify-between" "justify-end"))}
                (when show-status-bar?
                  ($ :div {:class "flex items-center gap-4 flex-wrap"}
                    (when processing?
                      ($ :div {:id "receipt-processing-banner"
                               :class "flex items-center gap-2"}
                        ($ :span {:class "ds-loading ds-loading-spinner ds-loading-xs"})
                        ($ :span {:class "text-sm"}
                          (str processing-label " " (t :receipts/receipts processing-total) "…"))
                        (when-let [duration (format-duration processing-started-at last-checked)]
                          ($ :span {:class "text-xs text-base-content/60"}
                            (str (t :receipts/duration) ": " duration)))
                        ($ :button {:id "btn-refresh-user-receipts"
                                    :class "ds-btn ds-btn-ghost ds-btn-xs"
                                    :type "button"
                                    :on-click (fn [e]
                                                (.preventDefault e)
                                                (refresh!))}
                          (t :receipts/refresh))))

                    (when show-purged-toggle?
                      ($ :label {:class "flex items-center gap-2 cursor-pointer"}
                        ($ :input {:type "checkbox"
                                   :id "toggle-show-purged-receipts"
                                   :class "ds-toggle ds-toggle-sm"
                                   :checked show-purged?
                                   :on-change #(rf/dispatch [:user-expenses/toggle-show-purged-receipts])})
                        ($ :span {:class "text-sm text-base-content/70"}
                          (str (t :receipts/show-purged) " (" purged-count ")"))))))

                ($ :div {:class "flex items-center gap-2"}
                  ($ :button {:id "btn-batch-parse-user-receipts"
                              :class "ds-btn ds-btn-outline ds-btn-sm"
                              :type "button"
                              :title (cond
                                       (not can-ocr?) (t :receipts/tooltip-no-ocr)
                                       (pos? selected-count) (t :receipts/tooltip-ocr-selected)
                                       :else (t :receipts/tooltip-select-first))
                              :disabled (or (not can-ocr?) action-loading? (zero? selected-count))
                              :on-click parse-selected!}
                    (str (t :receipts/parse-selected)
                      (when (pos? selected-count)
                        (str " (" selected-count ")"))))
                  ($ :button {:id "btn-batch-post-user-receipts"
                              :class "ds-btn ds-btn-primary ds-btn-sm"
                              :type "button"
                              :title (cond
                                       (not can-ocr?) (t :receipts/tooltip-no-post)
                                       (pos? selected-count) (t :receipts/tooltip-post-selected)
                                       :else (t :receipts/tooltip-select-first-post))
                              :disabled (or (not can-ocr?) action-loading? (zero? selected-count))
                              :on-click post-selected!}
                    (str (t :receipts/post-selected)
                      (when (pos? selected-count)
                        (str " (" selected-count ")"))))))

              ($ :div {:class "w-full pb-0 [&>div>table]:w-full"}
                ($ list-view
                  {:entity-name :receipts
                   :entity-spec (receipts-entity-spec t)
                   :rows-override display-receipts

                   :render-actions (fn [receipt]
                                     (render-receipt-actions t can-ocr? action-loading? open-receipt-detail-from-edit! receipt))
                   ;; Four circular action controls need ~184px plus cell padding; the default 150px would clip Parse.
                   :actions-column-width "12.5rem"
                   :on-add-click #(rf/dispatch [:navigate-to "/expenses/upload"])}))))))
      ($ receipt-detail-modal))))
