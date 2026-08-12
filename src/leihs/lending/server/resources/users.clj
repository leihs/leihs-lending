(ns leihs.lending.server.resources.users
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.lending.server.resources.suspensions :as suspensions]
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

(defn get-one
  [{{tx :tx pool-id :pool-id} :request} {:keys [id]} {:keys [user-id]}]
  (let [uid (or user-id id)
        user (get-by-id tx uid)
        suspension (suspensions/active-for-user-in-pool tx uid pool-id)
        delegator (when-let [did (:delegator_user_id user)]
                    (get-by-id tx did))]
    (-> user
        (assoc :is_suspended (boolean suspension)
               :suspended_reason (:suspended_reason suspension)
               :delegator_user delegator))))

(defn get-current
  [{{tx :tx {user-id :id} :authenticated-entity} :request} _ _]
  (if-not user-id
    (throw (ex-info "Not authenticated" {:status 401}))
    {:id user-id
     :user (get-by-id tx user-id)}))
