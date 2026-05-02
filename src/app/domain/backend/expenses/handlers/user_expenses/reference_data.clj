(ns app.domain.backend.expenses.handlers.user-expenses.reference-data
  "Reference data (suppliers, payers) handlers for user expenses."
  (:require
    [app.domain.backend.expenses.handlers.user-expenses.helpers :as h]

    [taoensso.timbre :as log]))

(defn- resolve-service-map
  "Resolve the `service` var in a service namespace and return its value."
  [service-ns]
  (when-let [service-var (requiring-resolve (symbol (name service-ns) "service"))]
    (let [m (var-get service-var)]
      (when (map? m)
        m))))

(defn- resolve-service-op-fn
  "Resolve an operation fn for a service.

  Prefers an explicitly named var when provided (so wrappers/overrides still win),
  otherwise falls back to the `service` map key."
  ([service-ns service-op]
   (resolve-service-op-fn service-ns service-op nil))
  ([service-ns service-op legacy-var]
   (or (when legacy-var
         (requiring-resolve (symbol (name service-ns) (name legacy-var))))
     (when-let [m (resolve-service-map service-ns)]
       (get m service-op))
     (throw (ex-info (str "Could not resolve service op " service-op
                       " in namespace " service-ns)
              {:ns service-ns
               :service-op service-op
               :legacy-var legacy-var})))))

(defn list-suppliers-handler
  "Handler factory for listing suppliers.

  NOTE: This is user-facing API (non-admin)."
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/reference-data-read-roles "Role assignment required")]
        forbidden
        (try
          (let [tenant-id (h/get-tenant-id request)
                params (:query-params request)
                limit (h/parse-page-limit params 100)
                offset (h/parse-page-offset params)
                search (or (h/get-param params :search)
                         (h/get-param params :display-name))
                sort-opts (h/parse-sort-params params)
                created-at-from (h/parse-instant-param (h/get-param params :created-at-from))
                created-at-to (h/parse-instant-param (h/get-param params :created-at-to))
                extra-filters (cond-> []
                                tenant-id
                                (conj [:or
                                       [:in :id {:select-distinct [:sa/supplier_id]
                                                 :from [[:receipts :r]]
                                                 :join [[:supplier_aliases :sa] [:= :sa/id :r/supplier_alias_id]]
                                                 :where [:and [:= :r/tenant_id tenant-id]
                                                         [:is-not :r/supplier_alias_id nil]]}]
                                       [:in :id {:select-distinct [:supplier_id]
                                                 :from [:expenses]
                                                 :where [:and [:= :tenant_id tenant-id]
                                                         [:is-not :supplier_id nil]]}]])
                                created-at-from
                                (conj [:>= :created_at created-at-from])
                                created-at-to
                                (conj [:<= :created_at created-at-to]))
                normalized-key (h/get-param params :normalized-key)
                opts (cond-> {:limit limit
                              :offset offset}
                       (some? search) (assoc :search search)
                    (seq sort-opts) (merge sort-opts)
                       (seq extra-filters) (assoc :extra-filters extra-filters)
                       (some? normalized-key) (assoc :normalized-key normalized-key))
                suppliers-svc (requiring-resolve 'app.domain.backend.expenses.services.suppliers/list-suppliers)
                count-suppliers (requiring-resolve 'app.domain.backend.expenses.services.suppliers/count-suppliers)
                suppliers (vec (suppliers-svc db opts))
                total (long (or (count-suppliers db (select-keys opts [:search :extra-filters :normalized-key]))
                              0))]
            (h/json-response {:data suppliers
                              :total total
                              :limit limit
                              :offset offset}))
          (catch Exception e
            (log/error e "Error listing suppliers")
            (h/json-response {:error "Failed to list suppliers"} 500))))
      (h/unauthorized-response))))

(defn get-supplier-handler
  "Handler factory for getting a single supplier by id.

  NOTE: This is user-facing API (non-admin)."
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/reference-data-read-roles "Role assignment required")]
        forbidden
        (let [supplier-id (or (h/try-parse-uuid (get-in request [:path-params :id]))
                            (h/try-parse-uuid (get-in request [:parameters :path :id])))]
          (if supplier-id
            (try
              (let [get-supplier (resolve-service-op-fn
                                   'app.domain.backend.expenses.services.suppliers
                                   :get
                                   'get-supplier)
                    supplier (get-supplier db supplier-id)]
                (if supplier
                  (h/json-response {:data supplier})
                  (h/not-found-response "Supplier not found")))
              (catch Exception e
                (log/error e "Error getting supplier" {:supplier-id supplier-id})
                (h/json-response {:error "Failed to get supplier"} 500)))
            (h/json-response {:error "Invalid supplier ID"} 400))))
      (h/unauthorized-response))))

(def ^:private payer-manage-roles
  "Roles allowed to create or delete payers (admin and owner only)."
  #{"admin" "owner"})

(defn list-expense-contexts-handler
  "Handler factory for listing tenant expense contexts available to users.

  By default this returns active contexts for new-entry forms. Pass
  `include_inactive=true` when historical filter UIs need archived/inactive
  contexts too."
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/reference-data-read-roles "Role assignment required")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)]
          (try
            (let [params (:query-params request)
                  limit (h/parse-page-limit params 100)
                  offset (h/parse-page-offset params)
                  search (h/get-param params :search)
                  sort-opts (h/parse-sort-params params)
                  include-inactive? (true? (h/parse-boolean-param params :include_inactive))
                  extra-filters (when-not include-inactive?
                                  [[:= :is_active true]])
                  opts (cond-> {:limit limit
                                :offset offset}
                         tenant-id (assoc :tenant-id tenant-id)
                         (some? search) (assoc :search search)
                         (seq sort-opts) (merge sort-opts)
                         (seq extra-filters) (assoc :extra-filters extra-filters))
                  list-contexts (resolve-service-op-fn
                                  'app.domain.backend.expenses.services.expense-contexts
                                  :list
                                  'list-expense-contexts)
                  count-contexts (resolve-service-op-fn
                                   'app.domain.backend.expenses.services.expense-contexts
                                   :count
                                   'count-expense-contexts)
                  contexts (vec (list-contexts db opts))
                  total (long (or (count-contexts db (select-keys opts [:search :tenant-id :extra-filters]))
                                0))]
              (h/json-response {:data contexts
                                :total total
                                :limit limit
                                :offset offset}))
            (catch Exception e
              (log/error e "Error listing expense contexts")
              (h/json-response {:error "Failed to list expense contexts"} 500)))))
      (h/unauthorized-response))))

(defn list-payers-handler
  "Handler factory for listing payers available to users (tenant-scoped).
   Includes :user_payer_id in the response so the frontend can identify
   which payer belongs to the requesting user."
  [db]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/reference-data-read-roles "Role assignment required")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)]
          (try
            (let [params (:query-params request)
                  limit (h/parse-page-limit params 100)
                  offset (h/parse-page-offset params)
                  search (h/get-param params :search)
                      sort-opts (h/parse-sort-params params)
                  include-inactive? (true? (h/parse-boolean-param params :include_inactive))
                  extra-filters (when-not include-inactive?
                                  [[:= :p/is_active true]])
                  opts (cond-> {:limit limit
                                :offset offset}
                         (some? search) (assoc :search search)
                        (seq sort-opts) (merge sort-opts)
                         tenant-id (assoc :tenant-id tenant-id)
                         (seq extra-filters) (assoc :extra-filters extra-filters))
                  payers-svc (resolve-service-op-fn
                               'app.domain.backend.expenses.services.payers
                               :list
                               'list-payers)
                  count-payers (resolve-service-op-fn
                                 'app.domain.backend.expenses.services.payers
                                 :count)
                  count-opts (cond-> {:search search :tenant-id tenant-id}
                               (seq extra-filters) (assoc :extra-filters extra-filters))
                  payers (vec (payers-svc db opts))
                  total (long (or (count-payers db count-opts) 0))
                  user-payer-id (some->> ((requiring-resolve
                                            'app.domain.backend.expenses.services.payers/get-user-payer-id)
                                          db user-id tenant-id)
                                  str)]
              (h/json-response
                (cond-> {:data   payers
                         :total  total
                         :limit  limit
                         :offset offset}
                  user-payer-id (assoc :user_payer_id user-payer-id))))
            (catch Exception e
              (log/error e "Error listing payers")
              (h/json-response {:error "Failed to list payers"} 500)))))
      (h/unauthorized-response))))

(defn create-supplier-handler
  "Handler factory for creating a supplier (shared catalog).

  Allowed roles: member/admin."
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/reference-data-write-roles "Only members and admins can modify suppliers")]
        forbidden
        (try
          (let [body (h/read-json-body request)
                supplier-data (select-keys body [:display_name :address])
                create-supplier! (resolve-service-op-fn
                                   'app.domain.backend.expenses.services.suppliers
                                   :create!
                                   'create-supplier!)
                supplier (create-supplier! db supplier-data)]
            (h/json-response {:data supplier} 201))
          (catch clojure.lang.ExceptionInfo e
            (log/warn "Validation error creating supplier" {:error (ex-message e) :data (ex-data e)})
            (h/json-response {:error (ex-message e)} 400))
          (catch Exception e
            (log/error e "Error creating supplier")
            (h/json-response {:error "Failed to create supplier"} 500))))
      (h/unauthorized-response))))

(defn update-supplier-handler
  "Handler factory for updating a supplier (shared catalog).

  Allowed roles: member/admin."
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/reference-data-write-roles "Only members and admins can modify suppliers")]
        forbidden
        (let [supplier-id (or (h/try-parse-uuid (get-in request [:path-params :id]))
                            (h/try-parse-uuid (get-in request [:parameters :path :id])))]
          (if supplier-id
            (try
              (let [body (h/read-json-body request)
                    updates (select-keys body [:display_name :address])
                    update-supplier! (resolve-service-op-fn
                                       'app.domain.backend.expenses.services.suppliers
                                       :update!
                                       'update-supplier!)
                    supplier (update-supplier! db supplier-id updates)]
                (if supplier
                  (h/json-response {:data supplier})
                  (h/not-found-response "Supplier not found")))
              (catch clojure.lang.ExceptionInfo e
                (log/warn "Validation error updating supplier" {:error (ex-message e) :data (ex-data e)})
                (h/json-response {:error (ex-message e)} 400))
              (catch Exception e
                (log/error e "Error updating supplier" {:supplier-id supplier-id})
                (h/json-response {:error "Failed to update supplier"} 500)))
            (h/json-response {:error "Invalid supplier ID"} 400))))
      (h/unauthorized-response))))

(defn batch-delete-suppliers-handler
  "Handler factory for deleting multiple suppliers (shared catalog).

  Allowed roles: member/admin.

  Expects JSON body like:
  {:ids [<uuid> ...]}

  Returns:
  {:data {:deleted-count n :deleted-ids [...] :errors [...]}}"
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/reference-data-write-roles "Only members and admins can modify suppliers")]
        forbidden
        (try
          (let [body (h/read-body-params request)
                raw-ids (or (:ids body)
                          (:supplier_ids body)
                          (:supplier-ids body)
                          (:supplierIds body)
                          [])
                ids (->> raw-ids (map h/try-parse-uuid) (filter some?) vec)]
            (cond
              (empty? raw-ids)
              (h/json-response {:error "No supplier ids provided"} 400)

              (empty? ids)
              (h/json-response {:error "One or more supplier ids are invalid"} 400)

              :else
              (let [delete-supplier! (requiring-resolve 'app.domain.backend.expenses.services.suppliers/delete-supplier!)
                    deleted-ids (atom [])
                    errors (atom [])]
                (doseq [supplier-id ids]
                  (try
                    (if (boolean (delete-supplier! db supplier-id))
                      (swap! deleted-ids conj (str supplier-id))
                      (swap! errors conj {:id (str supplier-id)
                                          :error "not found"}))
                    (catch org.postgresql.util.PSQLException e
                      (let [sql-state (.getSQLState e)]
                        (swap! errors conj {:id (str supplier-id)
                                            :error (if (= "23503" sql-state)
                                                     "foreign key constraint"
                                                     "database error")
                                            :sql-state sql-state})))
                    (catch Exception e
                      (swap! errors conj {:id (str supplier-id)
                                          :error (.getMessage e)}))))
                (h/json-response {:data {:deleted-count (count @deleted-ids)
                                         :deleted-ids (vec @deleted-ids)
                                         :errors (vec @errors)}}))))
          (catch Exception e
            (log/error e "Error batch deleting suppliers")
            (h/json-response {:error "Failed to delete suppliers"} 500))))
      (h/unauthorized-response))))

(defn create-payer-handler
  "Handler factory for creating a payer (tenant-scoped).

  Allowed roles: admin/owner only. Members cannot create payers —
  their system payer is provisioned automatically on invitation accept."
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request payer-manage-roles "Only admins and owners can create payers")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)]
          (try
            (let [body (h/read-json-body request)
                  payer-data (-> (select-keys body [:label :is_default])
                               (cond-> tenant-id (assoc :tenant_id tenant-id))
                               (cond-> (contains? body :is_default)
                                 (update :is_default boolean)))
                  create-payer! (requiring-resolve 'app.domain.backend.expenses.services.payers/create-payer!)
                  payer (create-payer! db payer-data {:tenant-id tenant-id})]
              (h/json-response {:data payer} 201))
            (catch clojure.lang.ExceptionInfo e
              (log/warn "Validation error creating payer" {:error (ex-message e) :data (ex-data e)})
              (h/json-response {:error (ex-message e)} 400))
            (catch Exception e
              (log/error e "Error creating payer")
              (h/json-response {:error "Failed to create payer"} 500)))))
      (h/unauthorized-response))))

(defn update-payer-handler
  "Handler factory for updating a payer (tenant-scoped).

  Role rules:
  - admin/owner: update label/default on any payer.
  - member: may only update the :label of their own provisioned payer."
  [db]
  (fn [request]
    (if-let [user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request h/reference-data-write-roles "Role assignment required")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)
              role      (h/get-user-role request)
              payer-id  (or (h/try-parse-uuid (get-in request [:path-params :id]))
                          (h/try-parse-uuid (get-in request [:parameters :path :id])))]
          (if payer-id
            (try
              (cond
                ;; admin/owner: update label/default and activation state
                (contains? payer-manage-roles role)
                (let [body          (h/read-json-body request)
                      updates       (-> (select-keys body [:label :is_default :is_active])
                                      (cond-> (contains? body :is_default)
                                        (update :is_default boolean))
                                      (cond-> (contains? body :is_active)
                                        (update :is_active boolean)))
                      update-payer! (requiring-resolve 'app.domain.backend.expenses.services.payers/update-payer!)
                      payer         (update-payer! db payer-id updates {:tenant-id tenant-id})]
                  (if payer
                    (h/json-response {:data payer})
                    (h/not-found-response "Payer not found")))

                ;; member: only their own payer, only the label field
                (= role "member")
                (let [own-payer-id ((requiring-resolve
                                      'app.domain.backend.expenses.services.payers/get-user-payer-id)
                                    db user-id tenant-id)]
                  (if (= (str payer-id) (str own-payer-id))
                    (let [body  (h/read-json-body request)
                          label (:label body)]
                      (if (seq (str label))
                        (let [update-payer! (requiring-resolve 'app.domain.backend.expenses.services.payers/update-payer!)
                              payer         (update-payer! db payer-id {:label label} {:tenant-id tenant-id})]
                          (if payer
                            (h/json-response {:data payer})
                            (h/not-found-response "Payer not found")))
                        (h/json-response {:error "Label is required"} 400)))
                    (h/forbidden-response "You can only edit your own payer")))

                :else
                (h/forbidden-response "Insufficient permissions"))
              (catch clojure.lang.ExceptionInfo e
                (log/warn "Validation error updating payer" {:error (ex-message e) :data (ex-data e)})
                (h/json-response {:error (ex-message e)} 400))
              (catch Exception e
                (log/error e "Error updating payer" {:payer-id payer-id})
                (h/json-response {:error "Failed to update payer"} 500)))
            (h/json-response {:error "Invalid payer ID"} 400))))
      (h/unauthorized-response))))

(defn batch-delete-payers-handler
  "Handler factory for deleting multiple payers (tenant-scoped).

  Allowed roles: admin/owner only.

  Expects JSON body like:
  {:ids [<uuid> ...]}

  Returns:
  {:data {:deleted-count n :deleted-ids [...] :errors [...]}}"
  [db]
  (fn [request]
    (if-let [_user-id (h/get-user-id request)]
      (if-let [forbidden (h/ensure-role request payer-manage-roles "Only admins and owners can delete payers")]
        forbidden
        (let [tenant-id (h/get-tenant-id request)]
          (try
            (let [body (h/read-body-params request)
                  raw-ids (or (:ids body)
                            (:payer_ids body)
                            (:payer-ids body)
                            (:payerIds body)
                            [])
                  ids (->> raw-ids (map h/try-parse-uuid) (filter some?) vec)]
              (cond
                (empty? raw-ids)
                (h/json-response {:error "No payer ids provided"} 400)

                (empty? ids)
                (h/json-response {:error "One or more payer ids are invalid"} 400)

                :else
                (let [delete-payer! (resolve-service-op-fn
                                      'app.domain.backend.expenses.services.payers
                                      :delete!
                                      'delete-payer!)
                      deleted-ids (atom [])
                      errors (atom [])]
                  (doseq [payer-id ids]
                    (try
                      (if (boolean (delete-payer! db payer-id {:tenant-id tenant-id}))
                        (swap! deleted-ids conj (str payer-id))
                        (swap! errors conj {:id (str payer-id)
                                            :error "not found"}))
                      (catch org.postgresql.util.PSQLException e
                        (let [sql-state (.getSQLState e)]
                          (swap! errors conj {:id (str payer-id)
                                              :error (if (= "23503" sql-state)
                                                       "foreign key constraint"
                                                       "database error")})))
                      (catch Exception e
                        (swap! errors conj {:id (str payer-id)
                                            :error (.getMessage e)}))))
                  (h/json-response
                    {:data {:deleted-count (count @deleted-ids)
                            :deleted-ids @deleted-ids
                            :errors @errors}}))))
            (catch Exception e
              (h/json-response {:error (.getMessage e)} 500)))))
      (h/unauthorized-response "Authentication required"))))


