(ns leihs.lending.server.resources.models
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :*)
      (sql/from :models)))

(defn get-one
  [{{tx :tx} :request} _ {:keys [model-id]}]
  (when model-id
    (-> base-sqlmap
        (sql/where [:= :models.id model-id])
        sql-format
        (->> (jdbc-query tx))
        first)))
