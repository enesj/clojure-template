(ns app.template.backend.routes.admin.settings-bootstrap-test
  (:require
    [app.backend.fixtures :as fixtures]
    [app.template.backend.routes.admin.settings-bootstrap :as settings-bootstrap]
    [app.template.backend.routes.admin.settings-io :as settings-io]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [next.jdbc :as jdbc]))

(use-fixtures :each fixtures/with-transaction-rollback)

(defn- clear-runtime-configs!
  [db]
  (jdbc/execute! db ["DELETE FROM frontend_runtime_configs"]))

(deftest bootstrap-seeds-runtime-configs-from-defaults
  (testing "bootstrap seeds missing channels so effective reads have defaults"
    (let [db fixtures/*test-db*
          admin-defaults {:view-options {:admins {:display-locks {:show-edit? true}}}
                          :form-fields {:admins {:create-fields [:email]}}
                          :table-columns {:admins {:available-columns ["id" "email"]}}}
          user-defaults {:entities {:expenses {:title "Expenses"}}
                         :view-options {:expenses {:display-defaults {:show-filtering? true}}}
                         :form-fields {:expenses {:create-fields ["total_amount"]}}
                         :table-columns {:expenses {:available-columns ["id" "total_amount"]}}}]
      (clear-runtime-configs! db)
      (clojure.core/with-redefs-fn
        {#'settings-bootstrap/admin-defaults (fn [config-key]
                                               (get admin-defaults config-key))
         #'settings-bootstrap/user-defaults (fn [config-key]
                                              (get user-defaults config-key))}
        (fn []
          (is (= :ok (settings-bootstrap/bootstrap-runtime-configs! db)))
          (is (= (:view-options admin-defaults) (settings-io/read-view-options db)))
          (is (= (:form-fields admin-defaults) (settings-io/read-form-fields db)))
          (is (= ["id" "email"]
                (get-in (settings-io/read-table-columns db) [:admins :available-columns])))
          (is (= (:entities user-defaults) (settings-io/read-user-entities db)))
          (is (= (:view-options user-defaults) (settings-io/read-user-view-options db)))
          (is (= (:form-fields user-defaults) (settings-io/read-user-form-fields db)))
          (is (= (:table-columns user-defaults) (settings-io/read-user-table-columns db))))))))

        (deftest bootstrap-backfills-expense-category-default-config-on-existing-rows
          (testing "bootstrap additively reconciles stale expense-category config without clobbering existing customizations"
            (let [db fixtures/*test-db*
              stale-form-fields {:expense-categories {:create-fields ["name" "exclude_from_reports"]
                          :edit-fields ["name" "exclude_from_reports"]
                          :field-config {:name {:type "text" :label "Custom Name"}
                                 :exclude_from_reports {:type "checkbox" :label "Exclude from reports"}}}
                     :suppliers {:create-fields ["display_name"]}}
              stale-table-columns {:expense-categories {:available-columns ["name" "exclude_from_reports" "created_at" "updated_at" "id" "tenant_id"]
                            :default-visible-columns ["name" "exclude_from_reports" "created_at"]
                            :filterable-columns ["name" "exclude_from_reports" "created_at"]
                            :sortable-columns ["name" "exclude_from_reports" "created_at" "updated_at"]
                            :always-visible ["name"]
                            :column-metadata {:name {:label "Custom Name"}
                                  :exclude_from_reports {:label "Exclude from reports"}
                                  :created_at {:label-key :common/created-at}
                                  :updated_at {:label-key :common/updated-at}
                                  :id {:label-key :common/id}}}
                   :suppliers {:available-columns ["display_name"]}}
              expense-category-form-default {:expense-categories {:create-fields ["name" "exclude_from_reports" "is_default"]
                              :edit-fields ["name" "exclude_from_reports" "is_default"]
                              :field-config {:name {:type "text" :label "Name"}
                                     :exclude_from_reports {:type "checkbox" :label "Exclude from reports"}
                                     :is_default {:type "checkbox" :label "Default expense category"}}}}
              expense-category-table-default {:expense-categories {:available-columns ["name" "exclude_from_reports" "is_default" "created_at" "updated_at" "id" "tenant_id"]
                               :default-visible-columns ["name" "is_default" "exclude_from_reports" "created_at"]
                               :filterable-columns ["name" "is_default" "exclude_from_reports" "created_at"]
                               :sortable-columns ["name" "is_default" "exclude_from_reports" "created_at" "updated_at"]
                               :always-visible ["name"]
                               :column-metadata {:name {:label-key :common/expense-category-name}
                                     :exclude_from_reports {:label "Exclude from reports"}
                                     :is_default {:label-key :common/is-default}
                                     :created_at {:label-key :common/created-at}
                                     :updated_at {:label-key :common/updated-at}
                                     :id {:label-key :common/id}}}}
              admin-defaults {:view-options {}
                  :form-fields expense-category-form-default
                  :table-columns expense-category-table-default}
              user-defaults {:entities {}
                 :view-options {}
                 :form-fields expense-category-form-default
                 :table-columns expense-category-table-default}]
          (clear-runtime-configs! db)
          (settings-io/write-form-fields! db stale-form-fields)
          (settings-io/write-table-columns! db stale-table-columns)
          (settings-io/write-user-form-fields! db stale-form-fields)
          (settings-io/write-user-table-columns! db stale-table-columns)
          (clojure.core/with-redefs-fn
            {#'settings-bootstrap/admin-defaults (fn [config-key]
                           (get admin-defaults config-key))
             #'settings-bootstrap/user-defaults (fn [config-key]
                          (get user-defaults config-key))}
            (fn []
              (is (= :ok (settings-bootstrap/bootstrap-runtime-configs! db)))
              (doseq [form-fields [(settings-io/read-form-fields db)
                   (settings-io/read-user-form-fields db)]]
            (is (= ["name" "exclude_from_reports" "is_default"]
              (get-in form-fields [:expense-categories :create-fields])))
            (is (= ["name" "exclude_from_reports" "is_default"]
              (get-in form-fields [:expense-categories :edit-fields])))
            (is (= "Custom Name"
              (get-in form-fields [:expense-categories :field-config :name :label])))
            (is (= {:type "checkbox" :label "Default expense category"}
              (get-in form-fields [:expense-categories :field-config :is_default])))
            (is (= ["display_name"]
              (get-in form-fields [:suppliers :create-fields]))))
              (doseq [table-columns [(settings-io/read-table-columns db)
                     (settings-io/read-user-table-columns db)]]
            (is (= ["name" "exclude_from_reports" "is_default" "created_at" "updated_at" "id" "tenant_id"]
              (get-in table-columns [:expense-categories :available-columns])))
            (is (= ["name" "is_default" "exclude_from_reports" "created_at"]
              (get-in table-columns [:expense-categories :default-visible-columns])))
            (is (= ["name" "is_default" "exclude_from_reports" "created_at"]
              (get-in table-columns [:expense-categories :filterable-columns])))
            (is (= ["name" "is_default" "exclude_from_reports" "created_at" "updated_at"]
              (get-in table-columns [:expense-categories :sortable-columns])))
            (is (= {:label "Custom Name"}
              (get-in table-columns [:expense-categories :column-metadata :name])))
            (is (= {:label-key :common/is-default}
              (get-in table-columns [:expense-categories :column-metadata :is_default])))
            (is (= ["display_name"]
              (get-in table-columns [:suppliers :available-columns])))))))))

(deftest bootstrap-fails-closed-when-defaults-cannot-be-built
  (testing "bootstrap throws instead of silently seeding empty runtime config"
    (let [db fixtures/*test-db*]
      (clear-runtime-configs! db)
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"Bootstrap: failed to seed runtime frontend configs"
            (clojure.core/with-redefs-fn
              {#'settings-bootstrap/admin-defaults
               (fn [_] (throw (ex-info "boom" {:phase :admin-defaults})))}
              (fn []
                (settings-bootstrap/bootstrap-runtime-configs! db))))))))

(deftest bootstrap-backfills-expense-context-default-config-on-existing-user-rows
  (testing "bootstrap additively reconciles stale user config with missing expense-contexts"
    (let [db fixtures/*test-db*
        stale-user-entities {:expenses {:title "Expenses"}}
        stale-user-view-options {:expenses {:display-defaults {:show-filtering? true}}}
        stale-user-table-columns {:suppliers {:available-columns ["display_name"]
                               :default-visible-columns ["display_name"]
                               :filterable-columns ["display_name"]
                               :sortable-columns ["display_name"]
                               :always-visible ["display_name"]}}
        user-defaults {:entities {:expenses {:title "Expenses"}
                       :expense-contexts {:title "Expense Contexts"}}
               :view-options {:expense-contexts {:display-defaults {:show-add-button? true
                                           :show-edit? true
                                           :show-delete? true}
                        :list-config {:form-display :modal
                                 :disallowed-action-mode :disable
                                 :action-gates {:add :expenses/expense-contexts.manage}}}}
               :table-columns {:expense-contexts {:available-columns ["name" "description" "is_active" "created_at" "updated_at" "id" "tenant_id"]
                                   :default-visible-columns ["name" "description" "is_active" "created_at"]
                                   :filterable-columns ["name" "description" "is_active" "created_at"]
                                   :sortable-columns ["name" "is_active" "created_at" "updated_at"]
                                   :always-visible ["name"]
                                   :column-metadata {:name {:label-key :common/name}
                                               :description {:label-key :common/description}
                                               :is_active {:label-key :common/active}}}
                         :suppliers {:available-columns ["display_name"]
                                   :default-visible-columns ["display_name"]
                                   :filterable-columns ["display_name"]
                                   :sortable-columns ["display_name"]
                                   :always-visible ["display_name"]}}}]
      (clear-runtime-configs! db)
      (settings-io/write-user-entities! db stale-user-entities)
      (settings-io/write-user-view-options! db stale-user-view-options)
      (settings-io/write-user-table-columns! db stale-user-table-columns)
      (clojure.core/with-redefs-fn
        {#'settings-bootstrap/user-defaults (fn [config-key]
                                 (get user-defaults config-key))
         #'settings-bootstrap/admin-defaults (fn [_] {})}
        (fn []
          (is (= :ok (settings-bootstrap/bootstrap-runtime-configs! db)))
          (is (= {:title "Expense Contexts"}
              (get-in (settings-io/read-user-entities db) [:expense-contexts])))
          (is (= :expenses/expense-contexts.manage
              (get-in (settings-io/read-user-view-options db)
              [:expense-contexts :list-config :action-gates :add])))
          (is (= {:label-key :common/name}
              (get-in (settings-io/read-user-table-columns db)
              [:expense-contexts :column-metadata :name])))
          (is (= ["display_name"]
              (get-in (settings-io/read-user-table-columns db)
              [:suppliers :available-columns]))))))))

          (deftest bootstrap-backfills-article-taxonomy-default-config-on-existing-user-rows
            (testing "bootstrap reconciles stale taxonomy user config without clobbering unrelated entities"
              (let [db fixtures/*test-db*
                stale-user-view-options {:categories {:display-defaults {:show-add-button? true
                                 :show-edit? true
                                 :show-delete? true}}
                         :suppliers {:display-defaults {:per-page 100}}}
                stale-user-form-fields {:subcategories {:create-fields ["category_id" "name" "description"]
                            :edit-fields ["category_id" "name" "description"]
                            :field-config {:name {:type "text" :label "Custom Name"}
                                   :category_id {:type "select"}}}
                        :suppliers {:create-fields ["display_name"]}}
                stale-user-table-columns {:subcategories {:available-columns ["category_name" "name" "description" "created_at"]
                              :default-visible-columns ["name" "category_name" "description" "created_at"]
                              :filterable-columns ["name" "category_name" "description" "created_at"]
                              :sortable-columns ["name" "category_name" "created_at"]
                              :always-visible ["name"]
                              :column-metadata {:name {:label-key :common/subcategory-name}}}
                        :suppliers {:available-columns ["display_name"]}}
                user-defaults {:entities {}
                   :view-options {:categories {:display-defaults {:show-add-button? false
                                  :show-edit? false
                                  :show-delete? false}}
                          :subcategories {:display-defaults {:show-add-button? true}}}
                   :form-fields {:subcategories {:create-fields ["category_id" "name" "description"]
                             :edit-fields ["name" "description"]
                             :field-config {:name {:type "text" :label "Name"}
                                    :description {:type "textarea"}}}}
                   :table-columns {:subcategories {:available-columns ["category_name" "name" "description" "is_active" "is_system_default" "created_at"]
                               :default-visible-columns ["name" "category_name" "description" "is_active" "created_at"]
                               :filterable-columns ["name" "category_name" "description" "is_active" "created_at"]
                               :sortable-columns ["name" "category_name" "is_active" "is_system_default" "created_at"]
                               :always-visible ["name"]
                               :column-metadata {:name {:label-key :common/subcategory-name}
                                     :is_active {:label "Active"}
                                     :is_system_default {:label "System default"}}}
                           :suppliers {:available-columns ["display_name"]}}}]
            (clear-runtime-configs! db)
            (settings-io/write-user-view-options! db stale-user-view-options)
            (settings-io/write-user-form-fields! db stale-user-form-fields)
            (settings-io/write-user-table-columns! db stale-user-table-columns)
            (clojure.core/with-redefs-fn
              {#'settings-bootstrap/user-defaults (fn [config-key]
                           (get user-defaults config-key))
               #'settings-bootstrap/admin-defaults (fn [_] {})}
              (fn []
                (is (= :ok (settings-bootstrap/bootstrap-runtime-configs! db)))
                (is (false? (get-in (settings-io/read-user-view-options db)
                    [:categories :display-defaults :show-add-button?])))
                (is (false? (get-in (settings-io/read-user-view-options db)
                    [:categories :display-defaults :show-edit?])))
                (is (false? (get-in (settings-io/read-user-view-options db)
                    [:categories :display-defaults :show-delete?])))
                (is (= ["name" "description"]
                  (get-in (settings-io/read-user-form-fields db)
                  [:subcategories :edit-fields])))
                (is (= {:type "text" :label "Custom Name"}
                  (get-in (settings-io/read-user-form-fields db)
                  [:subcategories :field-config :name])))
                (is (some #{"is_active"}
                  (get-in (settings-io/read-user-table-columns db)
                  [:subcategories :available-columns])))
                (is (= {:label "System default"}
                  (get-in (settings-io/read-user-table-columns db)
                  [:subcategories :column-metadata :is_system_default])))
                (is (= ["display_name"]
                  (get-in (settings-io/read-user-table-columns db)
                  [:suppliers :available-columns]))))))))
