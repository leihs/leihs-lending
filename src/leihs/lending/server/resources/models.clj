(ns leihs.lending.server.resources.models
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.resources.images.core :as images]
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
  "By `id` arg (top-level query, 404 when missing) or by the parent's
  `model-id` (nil for option lines)."
  [{{tx :tx} :request} {:keys [id]} {:keys [model-id]}]
  (when-let [model-id (or id model-id)]
    (or (-> base-sqlmap
            (sql/where [:= :models.id model-id])
            sql-format
            (->> (jdbc-query tx))
            first)
        (when id (throw (ex-info "Model not found" {:status 404}))))))

(defn get-thumbnail-url
  "Base64 data URL of the model's thumbnail -- the cover image's thumbnail when
  a cover is set, else any thumbnail attached to the model itself."
  [{{tx :tx} :request} _ {:keys [id cover-image-id]}]
  (some-> (-> (sql/select :images.content)
              (sql/from :images)
              (sql/where [:= :images.thumbnail true])
              (sql/where (if cover-image-id
                           [:or
                            [:= :images.id cover-image-id]
                            [:= :images.parent_id cover-image-id]]
                           [:= :images.target_id id]))
              (sql/limit 1)
              sql-format
              (->> (jdbc-query tx))
              first
              :content)
          images/prefix-with-data-url))

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
