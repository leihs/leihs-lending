(ns leihs.lending.server.resources.inventory-pools
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.user.permissions :refer [managed-inventory-pools]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :*)
      (sql/from :inventory_pools)))

(defn get-by-id [tx id]
  (-> base-sqlmap
      (sql/where [:= :id id])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn get-available-pools [{{tx :tx :keys [authenticated-entity]} :request} _ _]
  (managed-inventory-pools tx authenticated-entity))
