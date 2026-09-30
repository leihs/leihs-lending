(ns leihs.lending.server.resources.accessories
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn active-by-model-id
  "Active-in-pool accessory names per model, keyed by model_id."
  [tx model-ids pool-id]
  (-> (sql/select :accessories.model_id [:accessories.name :name])
      (sql/from :accessories)
      (sql/where [:in :accessories.model_id model-ids])
      (sql/where [:exists
                  (-> (sql/select 1)
                      (sql/from :accessories_inventory_pools)
                      (sql/where [:= :accessories_inventory_pools.accessory_id :accessories.id])
                      (sql/where [:= :accessories_inventory_pools.inventory_pool_id pool-id]))])
      sql-format
      (->> (jdbc-query tx))
      (->> (group-by :model_id))))
