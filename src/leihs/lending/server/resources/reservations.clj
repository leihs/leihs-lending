(ns leihs.lending.server.resources.reservations
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn get-multiple
  [{{tx :tx} :request} _ {order-id :id}]
  (-> (sql/select :reservations.id :reservations.model_id)
      (sql/from :reservations)
      (sql/where [:= :reservations.order_id order-id])
      sql-format
      (->> (jdbc-query tx))))
