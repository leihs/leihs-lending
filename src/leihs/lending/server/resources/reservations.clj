(ns leihs.lending.server.resources.reservations
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :*)
      (sql/from :reservations)))

(defn get-multiple
  [{{tx :tx} :request} _ {order-id :id}]
  (-> base-sqlmap
      (sql/where [:= :reservations.order_id order-id])
      sql-format
      (->> (jdbc-query tx))))

(defn get-for-order [tx order-id]
  (-> base-sqlmap
      (sql/where [:= :order_id order-id])
      (sql/where [:= :contract_id nil])
      sql-format
      (->> (jdbc-query tx))))

(defn get-for-order-with-model-names [tx order-id]
  (-> base-sqlmap
      (sql/select [:models.name :model_name])
      (sql/join :models [:= :reservations.model_id :models.id])
      (sql/where [:= :reservations.order_id order-id])
      sql-format
      (->> (jdbc-query tx))))
