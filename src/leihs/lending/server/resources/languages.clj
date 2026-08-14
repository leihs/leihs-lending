(ns leihs.lending.server.resources.languages
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [leihs.core.languages :refer [base-sqlmap get-the-one-to-use]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn get-multiple [{{tx :tx} :request} _ _]
  (-> base-sqlmap sql-format (->> (jdbc-query tx))))

(defn one-to-use [{{tx :tx} :request} _ {user-id :id}]
  (get-the-one-to-use tx user-id))
