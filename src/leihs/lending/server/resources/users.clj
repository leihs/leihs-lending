(ns leihs.lending.server.resources.users
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn get-by-id [tx id]
  (-> (sql/select :users/*)
      (sql/from :users)
      (sql/where [:= :users/id id])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn get-one
  [{{tx :tx} :request} _ {:keys [user-id]}]
  (get-by-id tx user-id))

(defn get-current
  [{{tx :tx {user-id :id} :authenticated-entity} :request} _ _]
  {:id   user-id
   :user (get-by-id tx user-id)})
