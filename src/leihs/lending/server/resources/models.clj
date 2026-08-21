(ns leihs.lending.server.resources.models
  (:require
   [clojure.string :as str]
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

(defn get-multiple
  [{{tx :tx pool-id :pool-id} :request} {:keys [term]} _]
  (-> base-sqlmap
      (sql/where [:exists
                  (-> (sql/select 1)
                      (sql/from :items)
                      (sql/where [:= :items.model_id :models.id])
                      (sql/where [:= :items.inventory_pool_id pool-id])
                      (sql/where [:= :items.parent_id nil])
                      (sql/where [:= :items.retired nil]))])
      (as-> sqlmap
            (reduce (fn [sqlmap token]
                      (sql/where sqlmap
                                 [:ilike
                                  [:concat_ws " " :models.manufacturer :models.product :models.version]
                                  (str "%" token "%")]))
                    sqlmap
                    (remove str/blank? (str/split (str/trim (or term "")) #"\s+"))))
      (sql/limit 20)
      sql-format
      (->> (jdbc-query tx))))
