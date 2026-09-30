(ns leihs.lending.server.resources.items
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

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
