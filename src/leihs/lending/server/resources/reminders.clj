(ns leihs.lending.server.resources.reminders
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn get-multiple
  [{{tx :tx pool-id :pool-id} :request} _ {user-id :user-id}]
  (-> (sql/select :id :created_at :subject :template)
      (sql/from :emails)
      (sql/where [:= :user_id user-id])
      (sql/where [:= :inventory_pool_id pool-id])
      (sql/where [:in :template ["reminder" "deadline_soon_reminder"]])
      (sql/order-by [:created_at :desc])
      sql-format
      (->> (jdbc-query tx))))
