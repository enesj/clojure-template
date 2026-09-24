(ns app.mobile.frontend.core
  "Mobile app entry point — bootstrap, routing, root view."
  (:require
    [ajax.core :as ajax]
    [app.mobile.frontend.auth-redirect :as auth-redirect]
    [app.mobile.frontend.events]
    [app.mobile.frontend.layout :refer [mobile-layout]]
    [day8.re-frame.http-fx]
    [app.mobile.frontend.pages.forgot-password :refer [forgot-password-page]]
    [app.mobile.frontend.pages.login :refer [login-page]]
    [app.mobile.frontend.pages.dashboard :refer [dashboard-page]]
    [app.mobile.frontend.pages.expense-detail :refer [expense-detail-page]]
    [app.mobile.frontend.pages.expense-list :refer [expenses-page]]
    [app.mobile.frontend.pages.manual-entry :refer [manual-entry-page]]
    [app.mobile.frontend.pages.more :refer [more-page]]
    [app.mobile.frontend.pages.receipt-list :refer [receipt-list-page]]
    [app.mobile.frontend.pages.receipt-review :refer [receipt-review-page]]
    [app.mobile.frontend.pages.reports :refer [reports-page]]
    [app.mobile.frontend.pages.tenant-select :refer [tenant-select-page]]
    [app.mobile.frontend.pages.upload :refer [camera-capture-page upload-page]]
    [app.mobile.frontend.pages.utility-expense :refer [utility-expense-page]]
    [app.mobile.frontend.routes :as routes]
    [app.template.frontend.events.bootstrap :as bootstrap]
    [clojure.string :as str]
    [re-frame.core :as rf]
    [taoensso.timbre :as log]
    [uix.core :refer [$ defui]]
    [uix.dom :as uix.dom]
    [uix.re-frame :refer [use-subscribe]]))

;; ========================================================================
;; Page Resolver
;; ========================================================================

(defui current-page []
  (let [current-view (use-subscribe [:mobile/current-view])
        ;; Auth views and dedicated camera capture render outside the tab shell
        shellless-views #{:m/login :m/forgot-password :m/tenant-select :m/upload-camera}
        shellless-view? (contains? shellless-views current-view)

        page-el (case current-view
                  :m/login ($ login-page)
                  :m/forgot-password ($ forgot-password-page)
                  :m/tenant-select ($ tenant-select-page)

                  :m/dashboard ($ dashboard-page)
                  :m/expenses ($ expenses-page)
                  :m/expense-detail ($ expense-detail-page)
                  :m/upload ($ upload-page)
                  :m/upload-camera ($ camera-capture-page)
                  :m/upload-review ($ receipt-review-page)
                  :m/upload-manual ($ manual-entry-page)
                  :m/upload-utility ($ utility-expense-page)
                  :m/reports ($ reports-page)
                  :m/receipts ($ receipt-list-page)
                  :m/search ($ expenses-page)
                  :m/more ($ more-page)

                  ;; Default: show dashboard
                  ($ dashboard-page))]
    (if shellless-view?
      page-el
      ($ mobile-layout {:children page-el}))))

;; ========================================================================
;; Mount
;; ========================================================================

(defonce root
  (uix.dom/create-root (js/document.getElementById "mobile-app")))

(defn mount-ui []
  (uix.dom/render-root ($ current-page) root))

^{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn ^:dev/after-load after-load []
  (rf/clear-subscription-cache!)
  (mount-ui))

;; ========================================================================
;; Navigation event (used by bottom tabs)
;; ========================================================================

(rf/reg-event-fx
  :mobile/navigate
  (fn [_ [_ path]]
    ;; Use pushState for SPA navigation
    (.pushState js/window.history nil "" path)
    ;; Dispatch popstate to trigger reitit
    (.dispatchEvent js/window (js/PopStateEvent. "popstate"))
    {}))

;; ========================================================================
;; Auth events (delegating to existing API)
;; ========================================================================

(rf/reg-event-fx
  :mobile/login
  (fn [{:keys [db]} [_ {:keys [email password]} callbacks]]
    {:http-xhrio {:method :post
                  :uri "/api/v1/auth/login"
                  :params {:email email :password password}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/login-success callbacks]
                  :on-failure [:mobile/login-failure callbacks]}}))

(rf/reg-event-fx
  :mobile/login-success
  (fn [{:keys [db]} [_ callbacks response]]
    ;; Check auth status to determine if tenant selection is needed
    {:http-xhrio {:method :get
                  :uri "/auth/status"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/auth-status-loaded]
                  :on-failure [:mobile/auth-status-loaded nil]}}))

(rf/reg-event-fx
  :mobile/auth-status-loaded
  (fn [{:keys [db]} [_ response]]
    (let [authenticated? (:authenticated response)
          tenant-required? (:tenant-selection-required response)
          available-tenants (:available-tenants response)
          current-view (get-in db [:current-route :data :view])
          current-path (some-> js/window .-location .-pathname)
          redirect-path (auth-redirect/auth-status-navigation-path authenticated? tenant-required? current-view current-path)]
      {:db (-> db
             (assoc-in [:session :authenticated?] authenticated?)
             (assoc-in [:session :loading?] false)
             (assoc-in [:session :available-tenants] available-tenants)
             (assoc-in [:session :tenant-selection-required] tenant-required?))
       :fx (cond-> []
             redirect-path
             (conj [:dispatch [:mobile/navigate redirect-path]]))})))

(rf/reg-event-fx
  :mobile/login-failure
  (fn [_ [_ callbacks response]]
    (when-let [on-error (:on-error callbacks)]
      (on-error (or (get-in response [:response :message])
                  "Login failed")))
    {}))

(rf/reg-event-fx
  :mobile/forgot-password
  (fn [_ [_ {:keys [email]} callbacks]]
    {:http-xhrio {:method :post
                  :uri "/api/v1/auth/forgot-password"
                  :params {:email email}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/forgot-password-success callbacks]
                  :on-failure [:mobile/forgot-password-failure callbacks]}}))

(rf/reg-event-fx
  :mobile/forgot-password-success
  (fn [_ [_ callbacks _]]
    (when-let [on-success (:on-success callbacks)]
      (on-success))
    {}))

(rf/reg-event-fx
  :mobile/forgot-password-failure
  (fn [_ [_ callbacks _]]
    (when-let [on-error (:on-error callbacks)]
      (on-error))
    {}))

(rf/reg-event-fx
  :mobile/select-tenant
  (fn [_ [_ tenant-id]]
    {:http-xhrio {:method :post
                  :uri "/api/v1/tenant/switch"
                  :params {:tenant-id (str tenant-id)}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/tenant-selected]
                  :on-failure [:mobile/tenant-select-failed]}}))

(rf/reg-event-fx
  :mobile/tenant-selected
  (fn [_ _]
    {:fx [[:dispatch [:mobile/navigate "/m/dashboard"]]]}))

(rf/reg-event-fx
  :mobile/tenant-select-failed
  (fn [_ [_ response]]
    (log/error "Tenant selection failed" response)
    {}))

;; ========================================================================
;; Init
;; ========================================================================

^{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn init []
  (log/info "Mobile app initializing...")
  (rf/dispatch-sync [:mobile/initialize-db])
  ;; Extract CSRF token
  (when-let [token (some-> js/document
                     (.querySelector "meta[name='csrf-token']")
                     (.getAttribute "content"))]
    (rf/dispatch-sync [:app.template.frontend.events.bootstrap/extract-csrf-token]))
  (routes/init-routes!)
  ;; Check auth status on load
  (rf/dispatch [:mobile/check-auth])
  (mount-ui))

(rf/reg-event-fx
  :mobile/check-auth
  (fn [_ _]
    {:http-xhrio {:method :get
                  :uri "/auth/status"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:mobile/auth-status-loaded]
                  :on-failure [:mobile/auth-check-failed]}}))

(rf/reg-event-fx
  :mobile/auth-check-failed
  (fn [{:keys [db]} _]
    {:db (-> db
           (assoc-in [:session :authenticated?] false)
           (assoc-in [:session :loading?] false))
     :fx [[:dispatch [:mobile/navigate "/m/login"]]]}))
