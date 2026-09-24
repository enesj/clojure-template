(ns app.template.frontend.routes
  "Client routes; update when adding pages."
  (:require
    [app.admin.frontend.core :as admin-core]
    [app.template.frontend.routing.router :as router-util]
    [app.template.frontend.routes.data :as routes-data]
    [clojure.string :as str]
    [re-frame.core :as rf]
    [reitit.coercion.malli :as rcm]
    [reitit.frontend :as rtf]
    [reitit.frontend.easy :as rtfe]
    [taoensso.timbre :as log]))

(def routes routes-data/app-routes)

;; ============================================================================
;; Slug prefix helpers — /t/{slug}/... is cosmetic, session is source of truth
;; ============================================================================

(def ^:private slug-prefix-re #"^/t/([^/]+)(/.*)$")

(defn strip-slug-prefix
  "If path starts with /t/{slug}/, return [slug inner-path]. Otherwise nil."
  [path]
  (when-let [m (re-matches slug-prefix-re path)]
    [(nth m 1) (nth m 2)]))

(defn- prefer-literal-route-conflicts
  "Resolve reitit path conflicts by preferring literal segments over parameterised ones.
  This lets routes like /expenses/new win over /expenses/:id without blowing up the router."
  [conflicts]
  (let [literal-routes (filter #(not (str/includes? (first %) ":")) conflicts)
        param-routes   (filter #(str/includes? (first %) ":") conflicts)]
    (concat literal-routes param-routes)))

;; Define the router instance early so it's available to usages below
(def router
  (rtf/router routes {:data {:coercion rcm/coercion}
                      :conflicts prefer-literal-route-conflicts}))

;; Keep the path-matching indirection in sync when Shadow hot-reloads this
;; namespace. Without this, newly added literal routes can be matched by an old
;; router and fall through to parameterised routes such as /expenses/:expense-id.
(router-util/set-router! router)

(defn on-navigate
  "Handle navigation to new routes with proper error handling"
  [new-match]
  ;; Debug the raw match to diagnose route recognition issues
  (when ^boolean goog.DEBUG
    (try
      ;; Logging code removed for brevity or if desired
      (catch :default _e
        (js/console.warn "on-navigate: logging failed"))))

  ;; Always update the route state first so UI can react. If nil, try manual match.
  (if new-match
    (rf/dispatch [:navigate-to new-match])
    (let [path (.-pathname js/window.location)
          manual (rtf/match-by-path router path)]
      (when ^boolean goog.DEBUG
        (log/info "on-navigate: new-match nil, manual match attempt"
          {:path path :manual-name (or (get-in manual [:data :name]) (:name manual))}))
      (when manual
        (rf/dispatch [:navigate-to manual]))))

  ;; Perform any route-specific side effects when we can detect the name
  (when (map? (or new-match (rtf/match-by-path router (.-pathname js/window.location))))
    (let [route-name (or (get-in new-match [:data :name]) (:name new-match))
          route-name-str (when route-name (name route-name))]

      (when route-name-str
        ;; Lazily initialize admin module when navigating to admin routes
        (when (str/starts-with? route-name-str "admin-")
          (admin-core/init-admin!))
        (case route-name
          ;; Handle home page
          :home (rf/dispatch [:page/init-home])
          :home-explicit (rf/dispatch [:page/init-home])

          ;; Default - do nothing special
          nil)))))

;; This function must be exported for core.cljs to use
(defn init-routes! []
  ;; Register the router with the utility namespace so routing events can match paths
  (router-util/set-router! router)

  ;; Detect /t/{slug}/... prefix on initial load. Strip it before the router sees
  ;; the path, and store the slug in app-db for link generation.
  (when-let [[slug inner-path] (strip-slug-prefix (.-pathname js/window.location))]
    (let [search (.-search js/window.location)]
      (log/info "Slug prefix detected, stripping" {:slug slug :inner-path inner-path})
      ;; Store slug for link generation
      (rf/dispatch-sync [:tenant/set-url-slug slug])
      ;; Replace URL so reitit sees the real path, preserving query string
      (.replaceState js/history nil "" (str inner-path search))))

  (rtfe/start!
    router
    on-navigate
    {:use-fragment false}))
