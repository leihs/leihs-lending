(ns leihs.lending.server.resources.orders
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc :refer [execute!]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn base-sqlmap [pool-id]
  (-> (sql/select :orders.id
                  :orders.user_id
                  :orders.purpose
                  [[:upper :orders.state] :state]
                  :orders.reject_reason
                  :orders.created_at
                  :orders.updated_at
                  [[:raw (str "FIRST_VALUE(orders.created_at) "
                              "OVER (PARTITION BY orders.user_id "
                              "ORDER BY orders.created_at DESC)")]
                   :newest_created_at_per_user]
                  [(-> (sql/select [[:min :reservations.start_date] :v])
                       (sql/from :reservations)
                       (sql/where [:= :reservations.order_id :orders.id]))
                   :start_date]
                  [(-> (sql/select [[:max :reservations.end_date] :v])
                       (sql/from :reservations)
                       (sql/where [:= :reservations.order_id :orders.id]))
                   :end_date])
      (sql/from :orders)
      (sql/where [:= :orders.inventory_pool_id pool-id])
      (sql/order-by [:newest_created_at_per_user :desc]
                    [:orders.user_id :asc]
                    [:orders.created_at :desc])))

(defn verifiable-orders-cte [pool-id]
  (-> (sql/select :r.order_id)
      (sql/from [:reservations :r])
      (sql/join [:orders :o] [:= :o.id :r.order_id])
      (sql/join [:entitlements :e] [:= :e.model_id :r.model_id])
      (sql/join [:entitlement_groups :eg] [:= :e.entitlement_group_id :eg.id])
      (sql/join [:entitlement_groups_users :egu] [:= :eg.id :egu.entitlement_group_id])
      (sql/where [:= :o.inventory_pool_id pool-id])
      (sql/where [:= :eg.is_verification_required true])
      (sql/where [:= :egu.user_id :r.user_id])
      (sql/where [:= :eg.inventory_pool_id :r.inventory_pool_id])))

(defn apply-filters [sqlmap {:keys [states start-date end-date to-be-verified]}]
  (cond-> sqlmap
    (seq states) (sql/where [:in :orders.state (map (comp str/lower-case name) states)])
    start-date (sql/where [:>= :orders.created_at start-date])
    end-date (sql/where [:<= :orders.created_at end-date])
    (true? to-be-verified)
    (sql/where [:in :orders.id
                (-> (sql/select :order_id) (sql/from :verifiable_orders))])
    (false? to-be-verified)
    (sql/where [:not-in
                :orders.id
                (-> (sql/select :order_id) (sql/from :verifiable_orders))])))

(defn get-multiple
  [{{tx :tx} :request}
   {:keys [pool-id states start-date end-date to-be-verified page per-page]}
   _]
  (-> (cond-> (base-sqlmap pool-id)
        (some? to-be-verified)
        (sql/with [:verifiable_orders (verifiable-orders-cte pool-id)]))
      (apply-filters {:states states
                      :start-date start-date
                      :end-date end-date
                      :to-be-verified to-be-verified})
      (sql/limit (or per-page 10))
      (sql/offset (* (dec (or page 1)) (or per-page 10)))
      sql-format
      (->> (jdbc-query tx))))

(defn get-by-id [tx id]
  (-> (sql/select :orders.id
                  :orders.user_id
                  :orders.purpose
                  [[:upper :orders.state] :state]
                  :orders.reject_reason
                  :orders.created_at
                  :orders.updated_at)
      (sql/from :orders)
      (sql/where [:= :orders.id id])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn get-one
  [{{tx :tx} :request} {:keys [id]} _]
  (get-by-id tx id))

(defn reject!
  [{{tx :tx} :request} {:keys [id reason]} _]
  (-> (sql/update :orders)
      (sql/set {:state "rejected" :reject_reason reason :updated_at [:now]})
      (sql/where [:= :orders.id id])
      sql-format
      (->> (execute! tx)))
  (-> (sql/update :reservations)
      (sql/set {:status "rejected"})
      (sql/where [:= :reservations.order_id id])
      (sql/where [:= :reservations.contract_id nil])
      sql-format
      (->> (execute! tx)))
  (get-by-id tx id))

