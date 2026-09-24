(ns app.template.frontend.events.routing
  "Routing events; update for navigation side effects."
  (:require
    [app.template.frontend.db.db :refer [common-interceptors]]
    [app.template.frontend.db.paths :as paths]
    [app.template.frontend.routing.router :as router-util]
    [re-frame.core :as rf]
    [reitit.frontend.controllers :as rtfc]
    [reitit.frontend.easy :as rtfe]
    [taoensso.timbre :as log]))

;; Events
(rf/reg-event-fx
  :page/init-home
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :home)}))

(rf/reg-event-fx
  :page/cleanup
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (update-in db (butlast (paths/current-page)) dissoc (last (paths/current-page)))}))

(rf/reg-event-fx
  :page/init-about
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :about)}))

;; User expense tracking page events

(rf/reg-event-db
  :page/init-tenant-select
  common-interceptors
  (fn [db _]
    (assoc-in db (paths/current-page) :tenant-select)))

(rf/reg-event-fx
  :page/init-tenant-members
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :tenant-members)
     :fx [[:dispatch [:app.template.frontend.events.tenant/fetch-members]]
          [:dispatch [:app.template.frontend.events.tenant/fetch-invitations]]]}))

(rf/reg-event-fx
  :page/init-onboarding
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :onboarding)
     :fx [[:dispatch [:app.template.frontend.events.onboarding/fetch-progress]]]}))

(rf/reg-event-fx
  :page/init-expenses-dashboard
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expenses-dashboard)
     :dispatch [:workspace-dashboard/init]}))

(rf/reg-event-fx
  :page/init-expenses-list
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expenses-list)
     ;; Ensure we don't accidentally show the generic CRUD add-form in the
     ;; user expenses list (it's driven by a global template flag).
     :dispatch-n [[:app.template.frontend.events.config/set-show-add-form false]
                  [:app.template.frontend.events.config/set-editing nil]]}))

(rf/reg-event-fx
  :page/init-expense-upload
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-upload)}))

(rf/reg-event-fx
  :page/init-expense-utility-new
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-utility-new)}))

(rf/reg-event-fx
  :page/init-receipts-list
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :receipts-list)}))

(rf/reg-event-fx
  :page/init-receipt-detail
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :receipt-detail)}))

(rf/reg-event-fx
  :page/init-expense-detail
  common-interceptors
  (fn [{:keys [db]} [_ expense-id]]
    {:db (-> db
           (assoc-in (paths/current-page) :expense-detail)
           (assoc-in [:ui :current-expense-id] expense-id))
     :dispatch (when expense-id [:user-expenses/fetch-expense expense-id])}))

(rf/reg-event-fx
  :page/init-expense-reports
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-reports)
     :dispatch [:user-expenses/init-reports]}))

(rf/reg-event-fx
  :page/init-user-profile
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :user-profile)
     :dispatch-n [[:profile/fetch]
                  [:user-expenses/fetch-payers {:limit 100}]
                  [:user-expenses/fetch-expense-categories {:limit 500 :offset 0}]]}))

(rf/reg-event-fx
  :page/init-expense-suppliers
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-suppliers)
     :dispatch-n [[:app.template.frontend.events.config/set-show-add-form false]
                  [:app.template.frontend.events.config/set-editing nil]]}))

(rf/reg-event-fx
  :page/init-expense-payers
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-payers)
     :dispatch-n [[:app.template.frontend.events.config/set-show-add-form false]
                  [:app.template.frontend.events.config/set-editing nil]
                  [:user-expenses/fetch-payers]]}))

(rf/reg-event-fx
  :page/init-expense-contexts
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-contexts)
     :dispatch-n [[:app.template.frontend.events.config/set-show-add-form false]
                  [:app.template.frontend.events.config/set-editing nil]]}))

(rf/reg-event-fx
  :page/init-expense-stores
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-stores)}))

(rf/reg-event-fx
  :page/init-expense-store-aliases
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-store-aliases)}))

(rf/reg-event-fx
  :page/init-expense-items
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-items)}))

(rf/reg-event-fx
  :page/init-expense-articles
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-articles)}))

(rf/reg-event-fx
  :page/init-expense-manufacturers
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-manufacturers)}))

(rf/reg-event-fx
  :page/init-expense-categories
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-categories)}))

(rf/reg-event-fx
  :page/init-expense-categories-catalog
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-categories-catalog)}))

(rf/reg-event-fx
  :page/init-expense-cities
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-cities)}))

(rf/reg-event-fx
  :page/init-expense-subcategories
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-subcategories)}))

(rf/reg-event-fx
  :page/init-expense-article-aliases
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-article-aliases)}))

(rf/reg-event-fx
  :page/init-expense-supplier-aliases
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-supplier-aliases)}))

(rf/reg-event-fx
  :page/init-unmapped-items
  common-interceptors
  (fn [{:keys [db]} _]
    ;; Keep routing init simple: page components handle auth/role gating.
    ;; This avoids relying on a specific db path for session/user.
    {:db (assoc-in db (paths/current-page) :unmapped-items)}))

(rf/reg-event-fx
  :page/init-expense-search
  common-interceptors
  (fn [{:keys [db]} _]
    {:db (assoc-in db (paths/current-page) :expense-search)
     :dispatch [:user-expenses/init-search]}))

(rf/reg-event-db
  :navigated
  common-interceptors
  (fn [db [new-match controllers]]
    (let [data (:data new-match)
          route-name (or (:name data) (:name new-match))]
      (log/info "routing :navigated" {:route-name route-name
                                      :data-keys (keys data)})
      (-> db
        (assoc :current-route new-match)
        (assoc :controllers controllers)))))

(rf/reg-fx
 :routing/push-state
  (fn [route-or-path]
    (try
      (cond
        ;; Preferred: explicit route-name navigation
        (vector? route-or-path) (apply rtfe/push-state route-or-path)
        (keyword? route-or-path) (rtfe/push-state route-or-path)

        ;; Backwards-compatible: allow UI code to pass a string path ("/foo/bar?x=1")
        (string? route-or-path)
        (let [location (.-location js/globalThis)
              origin (or (some-> location .-origin) "http://localhost")
              url (js/URL. route-or-path origin)
              path (.-pathname url)
              query-params (let [sp (js/URLSearchParams. (.-search url))
                                 entries (js/Array.from (.entries sp))]
                             (when (pos? (.-length entries))
                               (into {}
                                 (map (fn [[k v]] [(keyword k) v])
                                   (js->clj entries)))))
              target-url (str path (.-search url) (.-hash url))
              match (router-util/match-by-path path)
              route-name (get-in match [:data :name])
              match (cond-> match
                      query-params (assoc-in [:parameters :query] query-params)
                      (seq (.-hash url)) (assoc-in [:parameters :fragment] (.substring (.-hash url) 1)))]
          (if route-name
            (do
              ;; Dispatch directly from the freshly registered router match instead
              ;; of asking reitit.easy to reverse a route name. In dev, Shadow can
              ;; hot-reload this effect and the route table while reitit.easy still
              ;; holds the startup router, which made new literal paths like
              ;; /expenses/add-utility fall through to older parameter routes.
              (.pushState js/history nil "" target-url)
              (rf/dispatch [:navigate-to match]))
            (do
              (log/warn "No route match for path navigation" {:path route-or-path})
              (when location
                (set! (.-href location) route-or-path)))))

        :else
        (do
          (log/warn "Unsupported navigation target" {:to route-or-path})
          nil))
      (catch :default e
        (log/error e "Failed to navigate" {:to route-or-path})))))

(rf/reg-event-fx
  :navigate-to
  common-interceptors
  (fn [{:keys [db]} [new-match]]
    (cond
      (map? new-match)
      (let [old-match (:current-route db)
            controllers (rtfc/apply-controllers (:controllers old-match) new-match)]
        {:dispatch [:navigated new-match controllers]})

      ;; Programmatic navigation from UI code (pages, buttons)
      (or (string? new-match) (keyword? new-match) (vector? new-match))
      {:routing/push-state new-match}

      (nil? new-match)
      {}

      :else
      (do
        (log/warn "Unsupported :navigate-to target" {:target new-match})
        {}))))

;; Subscriptions
(rf/reg-sub
  :current-route
  (fn [db]
    (:current-route db)))
