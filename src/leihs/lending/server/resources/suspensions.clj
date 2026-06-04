(ns leihs.lending.server.resources.suspensions
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn active-for-user-in-pool [tx user-id pool-id]
  (-> (sql/select :suspended_reason)
      (sql/from :suspensions)
      (sql/where [:= :suspensions/user_id user-id]
                 [:= :suspensions/inventory_pool_id pool-id]
                 [:>= :suspensions/suspended_until [:raw "CURRENT_DATE"]])
      sql-format
      (->> (jdbc-query tx))
      first))
