(ns app.mobile.frontend.routes
  "Client-side routing for the mobile app. All paths under /m/*."
  (:require
    [re-frame.core :as rf]
    [reitit.coercion.malli :as rcm]
    [reitit.frontend :as rtf]
    [reitit.frontend.easy :as rtfe]))

(def mobile-routes
  ["/m"
   ;; Auth
   ["/login"
    {:name :m/login
     :view :m/login}]
   ["/forgot-password"
    {:name :m/forgot-password
     :view :m/forgot-password}]
   ["/tenant-select"
    {:name :m/tenant-select
     :view :m/tenant-select}]

   ;; Main tabs
   ["/dashboard"
    {:name :m/dashboard
     :view :m/dashboard}]
   ["/expenses"
    {:name :m/expenses
     :view :m/expenses}]
   ["/expenses/add-utility"
    {:name :m/expense-utility-new
     :view :m/upload-utility}]
   ["/expenses/:id"
    {:name :m/expense-detail
     :view :m/expense-detail}]
   ["/reports"
    {:name :m/reports
     :view :m/reports}]
   ["/receipts"
    {:name :m/receipts
     :view :m/receipts}]
   ["/search"
    {:name :m/search
     :view :m/search}]

   ;; Capture flow
   ["/upload"
    {:name :m/upload
     :view :m/upload}]
   ["/upload/camera"
    {:name :m/upload-camera
     :view :m/upload-camera}]
   ["/upload/review/:id"
    {:name :m/upload-review
     :view :m/upload-review}]
   ["/upload/manual"
    {:name :m/upload-manual
     :view :m/upload-manual}]
   ["/upload/utility"
    {:name :m/upload-utility
     :view :m/upload-utility}]

   ;; More
   ["/more"
    {:name :m/more
     :view :m/more}]])

(def router
  (rtf/router mobile-routes {:data {:coercion rcm/coercion}}))

(defn on-navigate [match _]
  (when match
    (rf/dispatch [:mobile/navigated match])))

(defn init-routes! []
  (rtfe/start! router on-navigate {:use-fragment false}))
