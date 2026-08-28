(ns leihs.lending.server.resources.users
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.lending.server.resources.suspensions :as suspensions]
   [next.jdbc :refer [execute!]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :*)
      (sql/from :users)))

(defn get-by-id [tx id]
  (-> base-sqlmap
      (sql/where [:= :users/id id])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn delegated-user-of?
  [tx user-id delegation-id]
  (-> (sql/select 1)
      (sql/from :delegations_users)
      (sql/where [:= :user_id user-id])
      (sql/where [:= :delegation_id delegation-id])
      sql-format
      (->> (jdbc-query tx))
      seq
      boolean))

(defn- enrich [tx pool-id user]
  (let [suspension (suspensions/active-for-user-in-pool tx (:id user) pool-id)]
    (assoc user
           :is_suspended (boolean suspension)
           :suspended_reason (:suspended_reason suspension))))

(defn get-one
  [{{tx :tx pool-id :pool-id} :request} {:keys [id]}
   {:keys [user-id delegator-user-id]}]
  (when-let [user (get-by-id tx (or id user-id delegator-user-id))]
    (enrich tx pool-id user)))

(defn get-multiple
  [{{tx :tx pool-id :pool-id} :request} {:keys [term]} _]
  (-> base-sqlmap
      (sql/where [:exists
                  (-> (sql/select 1)
                      (sql/from :access_rights)
                      (sql/where [:= :access_rights.user_id :users/id])
                      (sql/where [:= :access_rights.inventory_pool_id pool-id]))])
      (cond->
       (seq term)
        (sql/where [:or
                    [:ilike :users/firstname (str "%" term "%")]
                    [:ilike :users/lastname (str "%" term "%")]
                    [:ilike :users/login (str "%" term "%")]
                    [:ilike :users/badge_id (str "%" term "%")]]))
      (sql/limit 20)
      sql-format
      (->> (jdbc-query tx))
      (->> (mapv #(enrich tx pool-id %)))))

(defn get-current
  [{{tx :tx {user-id :id} :authenticated-entity} :request} _ _]
  (if-not user-id
    (throw (ex-info "Not authenticated" {:status 401}))
    {:id user-id
     :user-id user-id
     :user (get-by-id tx user-id)}))

(defn switch-language!
  [{{tx :tx {user-id :id} :authenticated-entity} :request} {:keys [locale]} _]
  (when-not user-id
    (throw (ex-info "Not authenticated" {:status 401})))
  (-> (sql/update :users)
      (sql/set {:language_locale locale})
      (sql/where [:= :id user-id])
      sql-format
      (->> (execute! tx)))
  {:id user-id
   :user-id user-id
   :user (get-by-id tx user-id)})
