(ns leihs.lending.server.resources.reservations
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.availability.core :as av]
   [next.jdbc :refer [execute!]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :*)
      (sql/from :reservations)))

(defn get-multiple
  [{{tx :tx} :request} _ {order-id :id contract-id :contract-id reservation-ids :reservation-ids}]
  (-> base-sqlmap
      (cond->
       (and order-id (not reservation-ids) (not contract-id))
        (sql/where [:= :reservations.order_id order-id])
        contract-id
        (sql/where [:= :reservations.contract_id contract-id])
        reservation-ids
        (sql/where [:in :reservations.id reservation-ids]))
      sql-format
      (->> (jdbc-query tx))))

(defn get-with-details-for-contract
  "Contract-show lines: one row per reservation, with item/model display fields
  and a price computed per legacy Reservation#price_or_max_price -- item.price
  when handed over, else the pool's costliest borrowable item for that model.
  OptionLine rows (model_id/item_id nil, option_id set) fall back to the
  option's own inventory_code/name/price, mirroring legacy's Option#model
  returning itself."
  [tx contract-id pool-id]
  (-> (sql/select
       :reservations.id
       :reservations.quantity
       :reservations.start_date
       :reservations.end_date
       :reservations.returned_date
       [:items.id :item_id]
       [[:coalesce :items.inventory_code :options.inventory_code] :item_inventory_code]
       [:items.serial_number :item_serial_number]
       [:models.id :model_id]
       [[:coalesce :models.name :options.name] :model_name]
       [:models.type :model_type]
       [:models.is_package :model_is_package]
       [:ru.firstname :returned_to_user_firstname]
       [:ru.lastname :returned_to_user_lastname]
       [[:case
         [:!= :reservations.item_id nil] [:* :items.price :reservations.quantity]
         [:!= :reservations.option_id nil] [:* :options.price :reservations.quantity]
         :else [:* (-> (sql/select [[:max :fallback_items.price] :v])
                       (sql/from [:items :fallback_items])
                       (sql/where [:= :fallback_items.model_id :reservations.model_id])
                       (sql/where [:= :fallback_items.inventory_pool_id pool-id])
                       (sql/where [:= :fallback_items.is_borrowable true]))
                :reservations.quantity]]
        :price])
      (sql/from :reservations)
      (sql/left-join :items [:= :items.id :reservations.item_id])
      (sql/left-join :models [:= :models.id :reservations.model_id])
      (sql/left-join :options [:= :options.id :reservations.option_id])
      (sql/left-join [:users :ru] [:= :ru.id :reservations.returned_to_user_id])
      (sql/where [:= :reservations.contract_id contract-id])
      (sql/order-by [[:coalesce :models.name :options.name] :asc] [:items.inventory_code :asc])
      sql-format
      (->> (jdbc-query tx))))

(defn contract-delegated-user-id [tx contract-id]
  (-> (sql/select :delegated_user_id)
      (sql/from :reservations)
      (sql/where [:= :contract_id contract-id])
      (sql/where [:!= :delegated_user_id nil])
      (sql/limit 1)
      sql-format
      (->> (jdbc-query tx))
      first
      :delegated_user_id))

(defn contract-handed-over-by-user-id [tx contract-id]
  (-> (sql/select :handed_over_by_user_id)
      (sql/from :reservations)
      (sql/where [:= :contract_id contract-id])
      (sql/where [:!= :handed_over_by_user_id nil])
      (sql/limit 1)
      sql-format
      (->> (jdbc-query tx))
      first
      :handed_over_by_user_id))

(defn- open-order-sqlmap
  "Excludes already-contracted (handed-over) reservations."
  [order-id]
  (-> base-sqlmap
      (sql/where [:= :order_id order-id])
      (sql/where [:= :contract_id nil])))

(defn get-for-open-order [tx order-id]
  (-> (open-order-sqlmap order-id)
      sql-format
      (->> (jdbc-query tx))))

(defn get-for-open-order-with-model-names [tx order-id]
  (-> (open-order-sqlmap order-id)
      (sql/select [:models.name :model_name])
      (sql/join :models [:= :reservations.model_id :models.id])
      sql-format
      (->> (jdbc-query tx))))

(defn get-lines
  "Groups an open order's reservations into model+pool+date-range lines,
  summing desired quantity and computing available quantity per line.
  Drops OptionLine rows (model_id nil) -- not quantity/entitlement-limited,
  and today never present on an open order, but kept explicit since
  hand-over/contract work may attach option lines before contract_id is set."
  [{{tx :tx} :request} _ {order-id :id}]
  (->> (get-for-open-order tx order-id)
       (remove #(nil? (:model_id %)))
       (group-by (juxt :model_id :inventory_pool_id :start_date :end_date))
       (map (fn [[_ rs]]
              (let [{:keys [model_id inventory_pool_id user_id start_date end_date]} (first rs)
                    ids (mapv :id rs)]
                {:model_id model_id
                 :quantity (->> rs (map :quantity) (apply +))
                 :available_quantity (av/maximum-available-in-pool-and-period-summed-for-groups
                                      tx model_id user_id start_date end_date inventory_pool_id ids)
                 :start_date start_date
                 :end_date end_date
                 :reservation_ids ids})))))

(defn- assert-order-submitted! [tx order-id]
  (when order-id
    (let [state (-> (sql/select [[:upper :state] :state])
                    (sql/from :orders)
                    (sql/where [:= :id order-id])
                    sql-format
                    (->> (jdbc-query tx))
                    first
                    :state)]
      (when (not= "SUBMITTED" state)
        (throw (ex-info "Order is not in submitted state" {:status 422}))))))

(defn- assert-not-removing-all! [tx order-id excluded-id]
  (when order-id
    (let [remaining (->> (get-for-open-order tx order-id)
                         (remove #(= excluded-id (:id %)))
                         count)]
      (when (zero? remaining)
        (throw (ex-info "Cannot remove the last reservation — reject the order instead"
                        {:status 422}))))))

(defn create!
  [{{tx :tx pool-id :pool-id} :request}
   {:keys [order-id user-id model-id start-date end-date]} _]
  (assert-order-submitted! tx order-id)
  (-> (sql/insert-into :reservations)
      (sql/values [{:inventory_pool_id pool-id
                    :user_id user-id
                    :order_id order-id
                    :model_id model-id
                    :quantity 1
                    :start_date start-date
                    :end_date end-date
                    :status (if order-id "submitted" "approved")
                    :created_at [:now]
                    :updated_at [:now]}])
      (sql/returning :*)
      sql-format
      (->> (jdbc-query tx))
      first))

(defn delete!
  [{{tx :tx} :request} {:keys [id order-id]} _]
  (assert-order-submitted! tx order-id)
  (assert-not-removing-all! tx order-id id)
  (-> (sql/delete-from :reservations)
      (sql/where [:= :id id])
      sql-format
      (->> (execute! tx)))
  id)
