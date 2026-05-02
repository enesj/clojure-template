(ns app.domain.frontend.expenses.authz
  "Expenses domain authorization module.
   
   Centralizes role-based access control for the Expenses domain.
   Roles (ascending privilege): viewer < member < admin < owner
   
   Usage:
     (authz/power-user? role)           ; admin or owner?
     (authz/can? role :expenses/access) ; capability check"
  (:require
    [re-frame.core :as rf]))

(def ^:private role-hierarchy
  "Role privilege levels (higher = more privileged)"
  {"viewer"     1
   "member"     2
   "admin"      3
   "owner"      4})

(def ^:private capability-requirements
  "Maps capabilities to minimum required role level or explicit role set.
   - Integer: minimum role level required
   - Set: explicit role membership required"
  {:expenses/access 1
   :expenses/expense.write 2
   :expenses/expense.delete 3
   :expenses/expense-items.manage 3
   :expenses/reference.write 2
   :expenses/unmapped.access 3
   :expenses/articles.manage 3
   :expenses/manufacturers.manage 3
   :expenses/categories.manage 3
   :expenses/expense-categories.manage 3
  :expenses/expense-contexts.manage 3
   :expenses/cities.manage 3
   :expenses/subcategories.manage 3
   :expenses/stores.manage 3
   :expenses/store-aliases.manage 3
   :expenses/supplier-aliases.manage 3
   :expenses/danger.execute 3
   :expenses/settings.write 2
   :expenses/upload 2
   :expenses/reports.export 1
   :expenses/receipts.approve 2})

(defn normalize-role
  "Normalize role to a string. Returns nil for invalid input."
  [role]
  (cond
    (keyword? role) (name role)
    (string? role)  role
    :else           nil))

(defn role-level
  "Get the privilege level for a role. Returns 0 for unknown roles."
  [role]
  (get role-hierarchy (normalize-role role) 0))

(defn power-user?
  "Returns true if role is admin or owner."
  [role]
  (>= (role-level role) 3))

(defn can-write?
  "Returns true if user can create/update (member+)."
  [role]
  (>= (role-level role) 2))

(defn can?
  "Check if a role has a specific capability.
   
   Arguments:
   - role: user's role (string or keyword)
   - capability: keyword like :expenses/access
   
   Returns true if the role satisfies the capability requirement."
  [role capability]
  (let [normalized-role (normalize-role role)
        level (role-level normalized-role)
        requirement (get capability-requirements capability)]
    (cond
      (nil? requirement) false ; unknown capability = denied
      (integer? requirement) (>= level requirement)
      (set? requirement) (contains? requirement normalized-role)
      :else false)))

(rf/reg-sub
  :expenses/user-role
  :<- [:user-role]
  (fn [role _]
    (normalize-role role)))

(rf/reg-sub
  :expenses/power-user?
  :<- [:expenses/user-role]
  (fn [role _]
    (power-user? role)))

(rf/reg-sub
  :expenses/can-write?
  :<- [:expenses/user-role]
  (fn [role _]
    (can-write? role)))

(rf/reg-sub
  :expenses/can?
  :<- [:expenses/user-role]
  (fn [role [_ capability]]
    (can? role capability)))
