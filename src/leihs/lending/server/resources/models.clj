(ns leihs.lending.server.resources.models
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.lending.server.resources.items :as items]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :*)
      (sql/from :models)))

(defn- where-lendable-in-pool
  "Models with at least one lendable item (see items/where-lendable)."
  [sqlmap pool-id]
  (sql/where sqlmap [:exists
                     (-> items/base-sqlmap
                         (sql/where [:= :items.model_id :models.id])
                         (items/where-lendable pool-id))]))

(defn assert-lendable-in-pool! [tx pool-id model-id]
  (when-not (-> base-sqlmap
                (sql/where [:= :models.id model-id])
                (where-lendable-in-pool pool-id)
                sql-format
                (->> (jdbc-query tx))
                seq)
    (throw (ex-info "Model not available in this pool" {:status 422}))))

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
      (where-lendable-in-pool pool-id)
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
