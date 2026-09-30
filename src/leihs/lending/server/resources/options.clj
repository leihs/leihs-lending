(ns leihs.lending.server.resources.options
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :*)
      (sql/from :options)))

(defn get-one
  [{{tx :tx} :request} _ {:keys [option-id]}]
  (when option-id
    (-> base-sqlmap
        (sql/where [:= :options.id option-id])
        sql-format
        (->> (jdbc-query tx))
        first)))
