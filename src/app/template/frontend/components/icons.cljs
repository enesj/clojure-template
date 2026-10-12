(ns app.template.frontend.components.icons
  (:require
    [app.template.frontend.components.button :refer [button]]
    [app.template.frontend.i18n :refer [use-t]]
    [uix.core :refer [$ defui]]))

(defui google-icon []
  ($ :svg {:class "w-4 h-4" :viewBox "0 0 24 24" :fill "currentColor"}
    ($ :path {:d "M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" :fill "#4285F4"})
    ($ :path {:d "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" :fill "#34A853"})
    ($ :path {:d "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" :fill "#FBBC05"})
    ($ :path {:d "M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" :fill "#EA4335"})
    ($ :path {:d "M1 1h22v22H1z" :fill "none"})))

(defui github-icon []
  ($ :svg {:class "w-4 h-4" :viewBox "0 0 24 24" :fill "currentColor"}
    ($ :path {:d "M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" :fill "#4285F4"})
    ($ :path {:d "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" :fill "#34A853"})
    ($ :path {:d "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" :fill "#FBBC05"})
    ($ :path {:d "M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" :fill "#EA4335"})
    ($ :path {:d "M1 1h22v22H1z" :fill "none"})))

(defui default-provider-icon []
  ($ :svg {:class "w-4 h-4" :viewBox "0 0 24 24" :fill "currentColor"}
    ($ :path {:d "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"})))

(defui edit-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"})))

(defui delete-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"})))

(defui chevron-double-left-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M11 19l-7-7 7-7m8 14l-7-7 7-7"})))

(defui chevron-left-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M15 19l-7-7 7-7"})))

(defui chevron-right-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M9 5l7 7-7 7"})))

(defui chevron-double-right-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M13 5l7 7-7 7M5 5l7 7-7 7"})))

(defui plus-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M12 6v6m0 0v6m0-6h6m-6 0H6"})))

(defui save-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4"})))

(defui cancel-icon []
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M6 18L18 6M6 6l12 12"})))

(defui view-icon [{:keys [title]}]
  ($ :svg {:class "w-5 h-5"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"
           :title title}
    (when title ($ :title {} title))
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z"})
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"})))

(defui settings-icon [{:keys [class] :or {class "w-5 h-5"}}]
  ($ :svg {:class class
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"})
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"})))

(defui settings-icon-large []
  ($ :svg {:class "w-6 h-6 text-base-content/70"
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"})
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"})))

(defui filter-icon [{:keys [id on-click active? field-id disabled? title]}]
  (let [t (use-t)
        resolved-title (or title (t :common/filter-by))]
    ($ button {:type "button"
               :btn-type :ghost
               :class (str "ds-btn-xs p-0 m-0 ml-1 "
                        (when disabled? "opacity-50 cursor-not-allowed"))
               :id (or id (when field-id (str "filter-icon-" (name field-id))))
               :tab-index (if disabled? -1 0)
               :aria-label resolved-title
               :title resolved-title
               :disabled disabled?
               :style {:line-height "0" :vertical-align "middle"}
               :on-click (fn [e]
                           (.stopPropagation e)
                           (when (and on-click (not disabled?)) (on-click e)))}
      ($ :svg {:class (str "w-4 h-4 hover:text-accent " (when active? "drop-shadow-sm"))
               :viewBox "0 0 24 24"
               :fill "none"
               :stroke (cond
                         disabled? "#d1d5db"  ; Light gray when disabled
                         active? "#3b82f6"    ; Blue when active
                         :else "#6b7280")     ; Gray when inactive
               :stroke-width (if active? "2.5" "1.7")
               :stroke-linecap "round"
               :stroke-linejoin "round"}
        ($ :polygon {:points "22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"})))))

(defui chart-bar [{:keys [class] :or {class "w-5 h-5"}}]
  ($ :svg {:class class
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"})))

(defui arrow-up [{:keys [class] :or {class "w-5 h-5"}}]
  ($ :svg {:class class
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M7 14l5-5 5 5"})))

(defui exclamation-triangle [{:keys [class] :or {class "w-5 h-5"}}]
  ($ :svg {:class class
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.704-.833-2.464 0L4.34 16.5c-.77.833.192 2.5 1.732 2.5z"})))

(defui arrow-path [{:keys [class] :or {class "w-5 h-5"}}]
  ($ :svg {:class class
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"})))

(defui check-circle [{:keys [class] :or {class "w-5 h-5"}}]
  ($ :svg {:class class
           :fill "currentColor"
           :viewBox "0 0 20 20"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:fill-rule "evenodd"
              :d "M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
              :clip-rule "evenodd"})))

(defui mail [{:keys [class] :or {class "w-5 h-5"}}]
  ($ :svg {:class class
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"})))

(defui dashboard-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"})))

(defui users-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"})))

(defui admins-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"})))

(defui user-settings-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M5.121 17.804A13.937 13.937 0 0112 16c2.5 0 4.847.655 6.879 1.804M15 10a3 3 0 11-6 0 3 3 0 016 0z"})
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M12 14c-3.866 0-7 3.134-7 7h14c0-3.866-3.134-7-7-7z"})))

(defui audit-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M9 17v-6a2 2 0 012-2h8M9 17h10M9 17H5a2 2 0 01-2-2V7a2 2 0 012-2h8m6 0v10a2 2 0 01-2 2h-2m-4-12h6"})))

(defui login-events-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M3 17a4 4 0 014-4h10a4 4 0 010 8H7a4 4 0 01-4-4zm7-9a3 3 0 116 0 3 3 0 01-6 0z"})))

(defui expenses-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M3 7h18M3 12h18M3 17h18"})))

(defui receipts-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M9 17V5a2 2 0 012-2h4l2 2h4v12a2 2 0 01-2 2H7a2 2 0 01-2-2v-5"})))

(defui suppliers-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M3 7l9-4 9 4-9 4-9-4zm0 6l9 4 9-4"})))

(defui payers-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M12 8c-1.657 0-3 1.343-3 3s1.343 3 3 3 3-1.343 3-3-1.343-3-3-3zm0 0V4m0 10v4m4-4h4m-8 0H4"})))

(defui articles-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M4 7h16M4 11h16M4 15h16M4 19h16"})))

(defui article-aliases-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M10 13l-2 2a3 3 0 104.243 4.243l2-2M14 11l2-2a3 3 0 00-4.243-4.243l-2 2"})))

(defui unmapped-items-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class
           :fill "none"
           :stroke "currentColor"
           :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M9 20l-5-2.18V5.18L9 7l5-2 7 3.18v11.64L14 17l-5 2z"})
    ($ :path {:stroke-linecap "round"
              :stroke-linejoin "round"
              :stroke-width "2"
              :d "M14 17v-13.18L7 4.5v11.64"})))

(defui expense-items-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"})))

(defui logout-icon [{:keys [class] :or {class "w-5 h-5"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"})))

(defui guide-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"})))

(defui search-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0"})))

(defui document-text-icon [{:keys [class] :or {class "w-6 h-6"}}]
  ($ :svg {:class class :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"
           :xmlns "http://www.w3.org/2000/svg"}
    ($ :path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"})))
