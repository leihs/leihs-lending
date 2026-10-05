(ns leihs.lending.server.resources.items
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :items.*)
      (sql/from :items)))

(defn where-lendable
  "Items the pool is responsible for, not retired and not inside a package --
  what legacy's add/swap model search offers."
  [sqlmap pool-id]
  (-> sqlmap
      (sql/where [:= :items.inventory_pool_id pool-id])
      (sql/where [:= :items.parent_id nil])
      (sql/where [:= :items.retired nil])))

(defn children-by-parent-id
  "Item children (package contents) per parent item, keyed by parent_id."
  [tx item-ids]
  (-> (sql/select [:items.parent_id :parent_id]
                  [:items.inventory_code :inventory_code]
                  [:models.name :model_name])
      (sql/from :items)
      (sql/join :models [:= :models.id :items.model_id])
      (sql/where [:in :items.parent_id item-ids])
      sql-format
      (->> (jdbc-query tx))
      (->> (group-by :parent_id))))

(defn get-by-inventory-code
  "Case-insensitive (codes are unique that way), across pools. Includes the
  responsible pool's name."
  [tx code]
  (-> base-sqlmap
      (sql/select [:inventory_pools.name :inventory_pool_name])
      (sql/join :inventory_pools [:= :inventory_pools.id :items.inventory_pool_id])
      (sql/where [:= [:lower :items.inventory_code] [:lower code]])
      sql-format
      (->> (jdbc-query tx))
      first))
