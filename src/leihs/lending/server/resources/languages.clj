(ns leihs.lending.server.resources.languages
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [leihs.core.languages :refer [base-sqlmap]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn get-multiple [{{tx :tx} :request} _ _]
  (-> base-sqlmap sql-format (->> (jdbc-query tx))))
