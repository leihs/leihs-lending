(ns leihs.lending.server.resources.entitlement-groups
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :*)
      (sql/from :entitlement_groups)))

(defn get-one
  "By the parent's `entitlement-group-id`, nil for the general group."
  [{{tx :tx} :request} _ {:keys [entitlement-group-id]}]
  (when entitlement-group-id
    (-> base-sqlmap
        (sql/where [:= :entitlement_groups.id entitlement-group-id])
        sql-format
        (->> (jdbc-query tx))
        first)))
