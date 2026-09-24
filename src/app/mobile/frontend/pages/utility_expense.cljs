(ns app.mobile.frontend.pages.utility-expense
  "Mobile guided workflow for adding utility bills as normal expenses."
  (:require
    [ajax.core :as ajax]
    [app.domain.frontend.expenses.shared.manual-entry.core :as manual-entry]
    [app.mobile.frontend.components.header :refer [mobile-header]]
    [app.mobile.frontend.pages.manual-entry.helpers :as manual-entry-helpers]
    [app.template.frontend.api.http :as http]
    [app.template.frontend.i18n :refer [use-t]]
    [clojure.string :as str]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui] :as uix]
    [uix.re-frame :refer [use-subscribe]]))

;; ============================================================================
;; Reference data events
;; ============================================================================

(defn- response-data [response]
  (vec (or (:data response) [])))

(rf/reg-event-fx
  :mobile/fetch-utility-subcategories
  (fn [{:keys [db]} _]
    {:db (-> db
           (assoc-in [:mobile :utility-expense :subcategories-loading?] true)
           (assoc-in [:mobile :utility-expense :subcategories-error] nil))
     :http-xhrio {:method :get
                  :uri "/api/v1/expenses/subcategories"
                  :params {:managed-taxonomy-only true
                           :limit 100
                           :offset 0}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/utility-subcategories-loaded]
                  :on-failure [:mobile/utility-subcategories-failed]}}))

(rf/reg-event-db
  :mobile/utility-subcategories-loaded
  (fn [db [_ response]]
    (-> db
      (assoc-in [:mobile :utility-expense :subcategories] (response-data response))
      (assoc-in [:mobile :utility-expense :subcategories-loading?] false)
      (assoc-in [:mobile :utility-expense :subcategories-error] nil))))

(rf/reg-event-db
  :mobile/utility-subcategories-failed
  (fn [db [_ error]]
    (-> db
      (assoc-in [:mobile :utility-expense :subcategories] [])
      (assoc-in [:mobile :utility-expense :subcategories-loading?] false)
      (assoc-in [:mobile :utility-expense :subcategories-error]
        (http/extract-error-message error)))))

(rf/reg-event-fx
  :mobile/fetch-utility-suppliers
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:mobile :utility-expense :suppliers-loading?] true)
     :http-xhrio {:method :get
                  :uri "/api/v1/expenses/suppliers"
                  :params {:limit 500 :offset 0}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/utility-suppliers-loaded]
                  :on-failure [:mobile/utility-suppliers-failed]}}))

(rf/reg-event-db
  :mobile/utility-suppliers-loaded
  (fn [db [_ response]]
    (-> db
      (assoc-in [:mobile :utility-expense :suppliers] (response-data response))
      (assoc-in [:mobile :utility-expense :suppliers-loading?] false))))

(rf/reg-event-db
  :mobile/utility-suppliers-failed
  (fn [db _]
    (-> db
      (assoc-in [:mobile :utility-expense :suppliers] [])
      (assoc-in [:mobile :utility-expense :suppliers-loading?] false))))

(rf/reg-event-fx
  :mobile/fetch-utility-payers
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:mobile :utility-expense :payers-loading?] true)
     :http-xhrio {:method :get
                  :uri "/api/v1/expenses/payers"
                  :params {:limit 500 :offset 0}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/utility-payers-loaded]
                  :on-failure [:mobile/utility-payers-failed]}}))

(rf/reg-event-db
  :mobile/utility-payers-loaded
  (fn [db [_ response]]
    (-> db
      (assoc-in [:mobile :utility-expense :payers] (response-data response))
      (assoc-in [:mobile :utility-expense :payers-loading?] false))))

(rf/reg-event-db
  :mobile/utility-payers-failed
  (fn [db _]
    (-> db
      (assoc-in [:mobile :utility-expense :payers] [])
      (assoc-in [:mobile :utility-expense :payers-loading?] false))))

(rf/reg-event-fx
  :mobile/fetch-utility-expense-contexts
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:mobile :utility-expense :contexts-loading?] true)
     :http-xhrio {:method :get
                  :uri "/api/v1/expenses/expense-contexts"
                  :params {:limit 500 :offset 0}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/utility-expense-contexts-loaded]
                  :on-failure [:mobile/utility-expense-contexts-failed]}}))

(rf/reg-event-db
  :mobile/utility-expense-contexts-loaded
  (fn [db [_ response]]
    (-> db
      (assoc-in [:mobile :utility-expense :expense-contexts] (response-data response))
      (assoc-in [:mobile :utility-expense :contexts-loading?] false))))

(rf/reg-event-db
  :mobile/utility-expense-contexts-failed
  (fn [db _]
    (-> db
      (assoc-in [:mobile :utility-expense :expense-contexts] [])
      (assoc-in [:mobile :utility-expense :contexts-loading?] false))))

;; ============================================================================
;; Submit events
;; ============================================================================

(rf/reg-event-fx
  :mobile/create-utility-expense
  (fn [{:keys [db]} [_ payload]]
    {:db (-> db
           (assoc-in [:mobile :utility-expense :saving?] true)
           (assoc-in [:mobile :utility-expense :error] nil))
     :http-xhrio {:method :post
                  :uri "/api/v1/expenses/utility-bills"
                  :params payload
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/create-utility-expense-success]
                  :on-failure [:mobile/create-utility-expense-failure]}}))

(rf/reg-event-fx
  :mobile/create-utility-expense-success
  (fn [{:keys [db]} _]
    {:db (-> db
           (assoc-in [:mobile :utility-expense :saving?] false)
           (assoc-in [:mobile :utility-expense :error] nil))
     :fx [[:dispatch [:mobile/show-toast :mobile/toast-utility-created]]
          [:dispatch [:mobile/navigate "/m/expenses"]]]}))

(rf/reg-event-db
  :mobile/create-utility-expense-failure
  (fn [db [_ error]]
    (-> db
      (assoc-in [:mobile :utility-expense :saving?] false)
      (assoc-in [:mobile :utility-expense :error]
        (or (http/extract-error-message error)
          "Failed to create utility bill")))))

;; ============================================================================
;; Subscriptions
;; ============================================================================

(rf/reg-sub
  :mobile/utility-subcategories
  (fn [db _]
    (get-in db [:mobile :utility-expense :subcategories] [])))

(rf/reg-sub
  :mobile/utility-suppliers
  (fn [db _]
    (get-in db [:mobile :utility-expense :suppliers] [])))

(rf/reg-sub
  :mobile/utility-payers
  (fn [db _]
    (get-in db [:mobile :utility-expense :payers] [])))

(rf/reg-sub
  :mobile/utility-expense-contexts
  (fn [db _]
    (get-in db [:mobile :utility-expense :expense-contexts] [])))

(rf/reg-sub
  :mobile/utility-reference-loading?
  (fn [db _]
    (boolean
      (or (get-in db [:mobile :utility-expense :subcategories-loading?])
        (get-in db [:mobile :utility-expense :suppliers-loading?])
        (get-in db [:mobile :utility-expense :payers-loading?])
        (get-in db [:mobile :utility-expense :contexts-loading?])))))

(rf/reg-sub
  :mobile/utility-subcategories-error
  (fn [db _]
    (get-in db [:mobile :utility-expense :subcategories-error])))

(rf/reg-sub
  :mobile/utility-saving?
  (fn [db _]
    (get-in db [:mobile :utility-expense :saving?] false)))

(rf/reg-sub
  :mobile/utility-error
  (fn [db _]
    (get-in db [:mobile :utility-expense :error])))

;; ============================================================================
;; Helpers
;; ============================================================================

(defn- today-string []
  (let [d (js/Date.)
        pad (fn [n] (.padStart (str n) 2 "0"))]
    (str (.getFullYear d) "-" (pad (inc (.getMonth d))) "-" (pad (.getDate d)))))

(defn- row-id [row]
  (some-> (or (:id row) (:db/id row)) str))

(defn- row-label [row & ks]
  (or (some #(some-> (get row %) str str/trim not-empty) ks)
    (row-id row)
    ""))

(defn- category-name [subcategory]
  (or (:category-name subcategory)
    (:category_name subcategory)
    (:subcategories/category-name subcategory)
    (:subcategories/category_name subcategory)))

(defn- active-subcategory? [subcategory]
  (not (false? (or (:is-active subcategory)
                 (:is_active subcategory)
                 (:subcategories/is-active subcategory)
                 (:subcategories/is_active subcategory)
                 true))))

(defn- utilities-subcategories [subcategories]
  (->> subcategories
    (filter #(and (= "Utilities" (category-name %))
               (active-subcategory? %)))
    (sort-by #(str/lower-case (row-label % :name :subcategories/name)))
    vec))

(defn- positive-amount? [value]
  (let [n (js/parseFloat (str value))]
    (and (not (js/isNaN n))
      (pos? n))))

(defn- option [row label-keys]
  {:value (row-id row)
   :label (apply row-label row label-keys)})

(defn- select-options [rows label-keys]
  (->> rows
    (mapv #(option % label-keys))
    (filterv #(and (seq (:value %)) (seq (:label %))))))

;; ============================================================================
;; Field components
;; ============================================================================

(defui select-field [{:keys [id label value on-change options placeholder required? disabled?]}]
  ($ :label {:class "form-control w-full"}
    ($ :div {:class "ds-label"}
      ($ :span {:class "ds-label-text font-medium"}
        label
        (when required?
          ($ :span {:class "text-error ml-1"} "*"))))
    ($ :select {:id id
                :class "ds-select ds-select-bordered w-full"
                :value (or value "")
                :disabled disabled?
                :on-change #(on-change (-> % .-target .-value))}
      (when placeholder
        ($ :option {:value ""} placeholder))
      (for [{:keys [value label]} options]
        ($ :option {:key value :value value} label)))))

(defui input-field [{:keys [id label type value on-change placeholder required? min step]}]
  ($ :label {:class "form-control w-full"}
    ($ :div {:class "ds-label"}
      ($ :span {:class "ds-label-text font-medium"}
        label
        (when required?
          ($ :span {:class "text-error ml-1"} "*"))))
    ($ :input {:id id
               :class "ds-input ds-input-bordered w-full"
               :type (or type "text")
               :value (or value "")
               :placeholder placeholder
               :min min
               :step step
               :on-change #(on-change (-> % .-target .-value))})))

(defui textarea-field [{:keys [id label value on-change placeholder]}]
  ($ :label {:class "form-control w-full"}
    ($ :div {:class "ds-label"}
      ($ :span {:class "ds-label-text font-medium"} label))
    ($ :textarea {:id id
                  :class "ds-textarea ds-textarea-bordered w-full min-h-24"
                  :value (or value "")
                  :placeholder placeholder
                  :on-change #(on-change (-> % .-target .-value))})))

;; ============================================================================
;; Page
;; ============================================================================

(defui utility-expense-page []
  (let [t (use-t)
        subcategories (use-subscribe [:mobile/utility-subcategories])
        suppliers (use-subscribe [:mobile/utility-suppliers])
        payers (use-subscribe [:mobile/utility-payers])
        expense-contexts (use-subscribe [:mobile/utility-expense-contexts])
        reference-loading? (use-subscribe [:mobile/utility-reference-loading?])
        subcategories-error (use-subscribe [:mobile/utility-subcategories-error])
        saving? (use-subscribe [:mobile/utility-saving?])
        submit-error (use-subscribe [:mobile/utility-error])
        utility-kinds (utilities-subcategories subcategories)
        [form set-form!] (uix/use-state {:utility-subcategory-id ""
                                         :supplier-id ""
                                         :payer-id ""
                                         :expense-context-id ""
                                         :purchased-at (today-string)
                                         :amount ""
                                         :currency "BAM"
                                         :notes ""})
        utility-options (select-options utility-kinds [:name :subcategories/name])
        supplier-options (select-options suppliers [:display-name :display_name :name :label :suppliers/display_name])
        payer-options (select-options payers [:label :payers/label :display-name :display_name :name :payers/display_name])
        context-options (select-options expense-contexts [:name :expense-contexts/name])
        currency-options (mapv (fn [currency] {:value currency :label currency}) manual-entry-helpers/currency-options)
        default-payer-id (or (some-> (manual-entry/payer-default-id payers) str)
                           (some-> (first payers) row-id))
        can-submit? (and (not saving?)
                      (seq (:utility-subcategory-id form))
                      (seq (:payer-id form))
                      (seq (:purchased-at form))
                      (positive-amount? (:amount form)))
        set-field! (fn [k v]
                     (set-form! #(assoc % k v)))
        submit! (fn [event]
                  (.preventDefault event)
                  (when can-submit?
                    (rf/dispatch
                      [:mobile/create-utility-expense
                       (cond-> {:utility-subcategory-id (:utility-subcategory-id form)
                                :payer-id (:payer-id form)
                                :purchased-at (:purchased-at form)
                                :amount (:amount form)
                                :currency (:currency form)}
                         (seq (:supplier-id form)) (assoc :supplier-id (:supplier-id form))
                         (seq (:expense-context-id form)) (assoc :expense-context-id (:expense-context-id form))
                         (seq (:notes form)) (assoc :notes (:notes form)))])))]
    (uix/use-effect
      (fn []
        (rf/dispatch [:mobile/fetch-utility-subcategories])
        (rf/dispatch [:mobile/fetch-utility-suppliers])
        (rf/dispatch [:mobile/fetch-utility-payers])
        (rf/dispatch [:mobile/fetch-utility-expense-contexts])
        js/undefined)
      [])

    (uix/use-effect
      (fn []
        (when-let [default-id (some-> (first utility-kinds) row-id)]
          (set-form! (fn [current-form]
                       (if (str/blank? (:utility-subcategory-id current-form))
                         (assoc current-form :utility-subcategory-id default-id)
                         current-form))))
        js/undefined)
      [utility-kinds])

    (uix/use-effect
      (fn []
        (when default-payer-id
          (set-form! (fn [current-form]
                       (if (str/blank? (:payer-id current-form))
                         (assoc current-form :payer-id default-payer-id)
                         current-form))))
        js/undefined)
      [default-payer-id])

    ($ :<>
      ($ mobile-header {:title (t :mobile/add-utility "Add utility bill")
                        :show-back? true
                        :on-back #(.back js/window.history)})
      ($ :form {:id "mobile-utility-expense-form"
                :class "p-4 space-y-4 pb-28"
                :on-submit submit!}
        ($ :div {:class "bg-base-100 rounded-xl p-4 shadow-sm space-y-1"}
          ($ :p {:class "text-xs font-semibold uppercase tracking-wide text-primary"}
            (t :utility-expense/eyebrow))
          ($ :h2 {:class "text-xl font-bold"}
            (t :utility-expense/title))
          ($ :p {:class "text-sm text-base-content/60"}
            (t :utility-expense/subtitle)))

        (when reference-loading?
          ($ :div {:id "mobile-utility-reference-loading"
                   :class "ds-alert text-sm"}
            ($ :span {:class "ds-loading ds-loading-spinner ds-loading-sm"})
            ($ :span (t :mobile/uploading "Loading..."))))

        (when subcategories-error
          ($ :div {:id "mobile-utility-reference-error"
                   :class "ds-alert ds-alert-error text-sm"}
            ($ :span subcategories-error)))

        (when submit-error
          ($ :div {:id "mobile-utility-submit-error"
                   :class "ds-alert ds-alert-error text-sm"}
            ($ :span submit-error)))

        (when-not (seq utility-options)
          ($ :div {:id "mobile-utility-no-kinds"
                   :class "ds-alert ds-alert-warning text-sm"}
            ($ :span (t :utility-expense/no-utility-kinds))))

        ($ :section {:class "bg-base-100 rounded-xl p-4 shadow-sm space-y-3"}
          ($ :div
            ($ :h3 {:class "font-semibold"} (t :utility-expense/bill-section))
            ($ :p {:class "text-sm text-base-content/60"} (t :utility-expense/bill-section-help)))
          ($ select-field {:id "mobile-utility-kind"
                           :label (t :utility-expense/kind-label)
                           :value (:utility-subcategory-id form)
                           :on-change #(set-field! :utility-subcategory-id %)
                           :options utility-options
                           :placeholder (t :utility-expense/kind-placeholder)
                           :required? true
                           :disabled? (not (seq utility-options))})
          ($ input-field {:id "mobile-utility-amount"
                          :label (t :utility-expense/amount-label)
                          :type "number"
                          :value (:amount form)
                          :on-change #(set-field! :amount %)
                          :placeholder "0.00"
                          :min "0.01"
                          :step "0.01"
                          :required? true})
          ($ input-field {:id "mobile-utility-date"
                          :label (t :utility-expense/date-label)
                          :type "date"
                          :value (:purchased-at form)
                          :on-change #(set-field! :purchased-at %)
                          :required? true})
          ($ select-field {:id "mobile-utility-currency"
                           :label (t :utility-expense/currency-label)
                           :value (:currency form)
                           :on-change #(set-field! :currency %)
                           :options currency-options
                           :required? true}))

        ($ :section {:class "bg-base-100 rounded-xl p-4 shadow-sm space-y-3"}
          ($ :div
            ($ :h3 {:class "font-semibold"} (t :utility-expense/context-section))
            ($ :p {:class "text-sm text-base-content/60"} (t :utility-expense/context-section-help)))
          ($ select-field {:id "mobile-utility-payer"
                           :label (t :utility-expense/payer-label)
                           :value (:payer-id form)
                           :on-change #(set-field! :payer-id %)
                           :options payer-options
                           :placeholder (t :utility-expense/payer-placeholder)
                           :required? true})
          ($ select-field {:id "mobile-utility-supplier"
                           :label (t :utility-expense/supplier-label)
                           :value (:supplier-id form)
                           :on-change #(set-field! :supplier-id %)
                           :options supplier-options
                           :placeholder (t :utility-expense/supplier-placeholder)})
          ($ select-field {:id "mobile-utility-context"
                           :label (t :utility-expense/context-label)
                           :value (:expense-context-id form)
                           :on-change #(set-field! :expense-context-id %)
                           :options context-options
                           :placeholder (t :utility-expense/context-placeholder)})
          ($ textarea-field {:id "mobile-utility-notes"
                             :label (t :utility-expense/notes-label)
                             :value (:notes form)
                             :on-change #(set-field! :notes %)
                             :placeholder (t :utility-expense/notes-placeholder)}))

        ($ :div {:class "sticky bottom-20 z-30 bg-base-200/95 backdrop-blur pt-2 pb-3"}
          ($ :button {:id "btn-save-mobile-utility"
                      :type "submit"
                      :class (str "ds-btn ds-btn-primary w-full "
                               (when saving? "ds-loading"))
                      :disabled (not can-submit?)}
            (when-not saving?
              (t :utility-expense/save))))))))
