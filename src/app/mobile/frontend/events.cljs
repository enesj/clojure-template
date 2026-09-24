(ns app.mobile.frontend.events
  "Core re-frame events for the mobile app."
  (:require
    [app.mobile.frontend.db.defaults :as defaults]
    [re-frame.core :as rf]))

;; ========================================================================
;; Database Initialization
;; ========================================================================

(rf/reg-event-db
  :mobile/initialize-db
  (fn [_ _]
    defaults/default-db))

;; ========================================================================
;; Routing
;; ========================================================================

(rf/reg-event-db
  :mobile/navigated
  (fn [db [_ match]]
    (let [view (get-in match [:data :view])]
      (-> db
        (assoc :current-route match)
        (assoc-in [:mobile :active-tab]
          (case view
            :m/dashboard :dashboard
            :m/expenses :expenses
            :m/expense-detail :expenses
            :m/search :expenses
            :m/upload :upload
            :m/upload-camera :upload
            :m/upload-review :upload
            :m/upload-manual :upload
            :m/upload-utility :upload
            :m/reports :reports
            :m/more :more
            :m/receipts :more
                      ;; default
            :dashboard))))))

;; ========================================================================
;; Subscriptions
;; ========================================================================

(rf/reg-sub
  :locale
  (fn [db _]
    (get db :locale :bs)))

(rf/reg-sub
  :mobile/current-route
  (fn [db _]
    (:current-route db)))

(rf/reg-sub
  :mobile/current-view
  :<- [:mobile/current-route]
  (fn [route _]
    (get-in route [:data :view])))

(rf/reg-sub
  :mobile/active-tab
  (fn [db _]
    (get-in db [:mobile :active-tab])))

(rf/reg-sub
  :session-loading?
  (fn [db _]
    (get-in db [:session :loading?] false)))

(rf/reg-sub
  :available-tenants
  (fn [db _]
    (get-in db [:session :available-tenants])))

(rf/reg-sub
  :mobile/pending-review-count
  (fn [db _]
    (get-in db [:mobile :pending-review-count] 0)))

(rf/reg-sub
  :mobile/offline-queue-count
  (fn [db _]
    (count (get-in db [:mobile :offline-queue] []))))

(rf/reg-sub
  :mobile/sync-status
  (fn [db _]
    (get-in db [:mobile :sync-status] :idle)))
