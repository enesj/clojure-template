(ns app.mobile.frontend.pages.upload
  "Mobile upload page — camera/gallery capture, manual entry, pending reviews.
  Compatibility facade: delegates to focused sub-namespaces."
  (:require
    [app.mobile.frontend.components.header :refer [mobile-header]]
    [app.mobile.frontend.pages.receipt-review :refer [pending-receipts-section]]
    [app.mobile.frontend.pages.upload.camera-capture :as camera-capture]
    [app.mobile.frontend.pages.upload.camera-utils :as camera-utils]
    ;; Side-effect require: event/sub registrations happen on load
    [app.mobile.frontend.pages.upload.events]
    [app.template.frontend.i18n :refer [use-t]]
    [re-frame.core :as rf]
    [uix.core :refer [$ defui use-ref]]
    [uix.re-frame :refer [use-subscribe]]))

;; ========================================================================
;; Re-exports for backward compatibility (tests + external callers)
;; ========================================================================

;; camera-capture components (re-exported via :refer above)
;; camera-capture-page, capture-button, toast-banner

(def camera-capture-page camera-capture/camera-capture-page)
(def capture-button camera-capture/capture-button)
(def toast-banner camera-capture/toast-banner)

;; camera-utils — pure functions used by tests
(def camera-constraints              camera-utils/camera-constraints)
(def media-devices-supported?        camera-utils/media-devices-supported?)
(def first-video-track               camera-utils/first-video-track)
(def stop-stream!                    camera-utils/stop-stream!)
(def torch-supported?                camera-utils/torch-supported?)
(def camera-error-message            camera-utils/camera-error-message)
(def attach-stream!                  camera-utils/attach-stream!)
(def upload-file-from-blob           camera-utils/upload-file-from-blob)
(def max-capture-dimension           camera-utils/max-capture-dimension)
(def bounded-capture-dimensions      camera-utils/bounded-capture-dimensions)
(def capture-current-frame!          camera-utils/capture-current-frame!)
(def apply-torch!                    camera-utils/apply-torch!)
(def default-live-camera-zoom        camera-utils/default-live-camera-zoom)
(def camera-zoom-range               camera-utils/camera-zoom-range)
(def preferred-camera-zoom           camera-utils/preferred-camera-zoom)
(def apply-camera-zoom!              camera-utils/apply-camera-zoom!)
(def apply-default-camera-zoom!      camera-utils/apply-default-camera-zoom!)
(def supported-fill-light-modes      camera-utils/supported-fill-light-modes)
(def flash-supported?                camera-utils/flash-supported?)

(def default-flash-enabled? camera-utils/default-flash-enabled?)
(def try-capture-with-flash!         camera-utils/try-capture-with-flash!)
(def detect-flash-support!           camera-utils/detect-flash-support!)
(def device-flash-mode?              camera-utils/device-flash-mode?)
(def native-camera-capture?          camera-utils/native-camera-capture?)
(def min-preview-zoom                camera-utils/min-preview-zoom)
(def max-preview-zoom                camera-utils/max-preview-zoom)
(def clamp-number                    camera-utils/clamp-number)
(def point-distance                  camera-utils/point-distance)
(def point-midpoint                  camera-utils/point-midpoint)
(def format-camera-zoom-label        camera-utils/format-camera-zoom-label)
(def format-camera-zoom-range-label  camera-utils/format-camera-zoom-range-label)

;; ========================================================================
;; Upload page
;; ========================================================================

(defui upload-page []
  (let [t (use-t)
        loading? (use-subscribe [:mobile/upload-loading?])
        error (use-subscribe [:mobile/upload-error])
        offline-count (use-subscribe [:mobile/offline-queue-count])
        gallery-ref (use-ref nil)]
    ($ :<>
      ($ mobile-header {:title (t :mobile/tab-upload "Upload")})
      ($ toast-banner)

      ($ :div {:class "p-4 space-y-4"}
        (when loading?
          ($ :div {:class "fixed inset-0 z-40 bg-base-100/80 flex items-center justify-center"}
            ($ :div {:class "text-center"}
              ($ :span {:class "ds-loading ds-loading-spinner ds-loading-lg text-primary"})
              ($ :p {:class "mt-2 text-sm text-base-content/60"} (t :mobile/uploading)))))

        (when error
          ($ :div {:class "ds-alert ds-alert-error text-sm"}
            ($ :span error)))

        ($ :div {:class "space-y-3"}
          ($ :h2 {:class "text-sm font-semibold text-base-content/70 uppercase tracking-wide"}
            (t :mobile/capture-receipt "Capture Receipt"))

          ($ :input {:id "input-gallery-upload-mobile"
                     :ref gallery-ref
                     :type "file"
                     :accept "image/*"
                     :class "hidden"
                     :on-change (fn [e]
                                  (when-let [file (-> e .-target .-files (aget 0))]
                                    (rf/dispatch [:mobile/upload-receipt file])
                                    (set! (.-value (.-target e)) "")))})

          ($ capture-button
            {:id "btn-take-photo-upload-mobile"
             :icon-path "M6.827 6.175A2.31 2.31 0 015.186 7.23c-.38.054-.757.112-1.134.175C2.999 7.58 2.25 8.507 2.25 9.574V18a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9.574c0-1.067-.75-1.994-1.802-2.169a47.865 47.865 0 00-1.134-.175 2.31 2.31 0 01-1.64-1.055l-.822-1.316a2.192 2.192 0 00-1.736-1.039 48.774 48.774 0 00-5.232 0 2.192 2.192 0 00-1.736 1.039l-.821 1.316z M16.5 12.75a4.5 4.5 0 11-9 0 4.5 4.5 0 019 0z"
             :label (t :mobile/take-photo "Take Photo")
             :sublabel (t :mobile/take-photo-sub "Open the dedicated camera screen")
             :disabled? loading?
             :on-click #(rf/dispatch [:mobile/navigate "/m/upload/camera"])})

          ($ capture-button
            {:id "btn-gallery-upload-mobile"
             :icon-path "M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0022.5 18.75V5.25A2.25 2.25 0 0020.25 3H3.75A2.25 2.25 0 001.5 5.25v13.5A2.25 2.25 0 003.75 21z"
             :label (t :mobile/from-gallery "Choose from Gallery")
             :sublabel (t :mobile/from-gallery-sub "Select existing photo")
             :disabled? loading?
             :on-click #(when-let [el @gallery-ref] (.click el))}))

        ($ :div {:class "ds-divider text-base-content/40 text-xs"} (t :common/or "OR"))

        ($ :div {:class "space-y-3"}
          ($ :h2 {:class "text-sm font-semibold text-base-content/70 uppercase tracking-wide"}
            (t :mobile/manual-entry "Manual Entry"))
          ($ capture-button
            {:id "btn-manual-upload-mobile"
             :icon-path "M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10"
             :label (t :mobile/add-manually "Add Expense Manually")
             :sublabel (t :mobile/add-manually-sub "Enter details without receipt")
             :on-click #(rf/dispatch [:mobile/navigate "/m/upload/manual"])})
          ($ capture-button
            {:id "btn-utility-upload-mobile"
             :icon-path "M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18M9 7.5h1.5M9 11.25h1.5M9 15h1.5m3-7.5H15m-1.5 3.75H15m-1.5 3.75H15"
             :label (t :mobile/add-utility "Add utility bill")
             :sublabel (t :mobile/add-utility-sub "Electricity, water, gas or heating")
             :on-click #(rf/dispatch [:mobile/navigate "/m/upload/utility"])}))

        ($ pending-receipts-section)

        (when (and offline-count (pos? offline-count))
          ($ :div {:class "bg-warning/10 border border-warning/30 rounded-xl p-4 mt-4"}
            ($ :p {:class "text-sm font-medium text-warning"}
              (str offline-count " " (t :mobile/receipts-queued "receipts queued offline")))
            ($ :button {:id "btn-sync-offline-upload-mobile"
                        :type "button"
                        :class "ds-btn ds-btn-warning ds-btn-sm mt-2"
                        :on-click #(rf/dispatch [:mobile/trigger-sync])}
              (t :mobile/sync-now "Sync Now"))))))))
