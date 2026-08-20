(ns leihs.lending.server.resources.orders
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [java-time :as t]
   [leihs.core.availability.changes :refer [local-date]]
   [leihs.core.availability.core :as av]
   [leihs.core.availability.pool :as pool]
   [leihs.core.db :as db]
   [leihs.core.mails :refer [log-mail-failure]]
   [leihs.lending.server.mails :as mails]
   [leihs.lending.server.resources.reservations :as res]
   [next.jdbc :refer [execute!] :as jdbc]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn base-sqlmap [pool-id]
  (-> (sql/select :orders.id
                  :orders.user_id
                  :orders.purpose
                  [[:upper :orders.state] :state]
                  :orders.reject_reason
                  :orders.created_at
                  :orders.updated_at
                  [[:over [[:first_value :orders.created_at]
                           {:partition-by [:orders.user_id]
                            :order-by [[:orders.created_at :desc]]}]]
                   :newest_created_at_per_user]
                  [(-> (sql/select [[:min :reservations.start_date] :v])
                       (sql/from :reservations)
                       (sql/where [:= :reservations.order_id :orders.id]))
                   :start_date]
                  [(-> (sql/select [[:max :reservations.end_date] :v])
                       (sql/from :reservations)
                       (sql/where [:= :reservations.order_id :orders.id]))
                   :end_date]
                  [[:over [[:count :*] {}]] :total_count]
                  [[:exists
                    (-> (sql/select 1)
                        (sql/from [:reservations :r])
                        (sql/join [:entitlements :e] [:= :e.model_id :r.model_id])
                        (sql/join [:entitlement_groups :eg] [:= :e.entitlement_group_id :eg.id])
                        (sql/join [:entitlement_groups_users :egu] [:= :eg.id :egu.entitlement_group_id])
                        (sql/where [:= :r.order_id :orders.id])
                        (sql/where [:= :eg.is_verification_required true])
                        (sql/where [:= :egu.user_id :r.user_id])
                        (sql/where [:= :eg.inventory_pool_id :r.inventory_pool_id]))]
                   :to_be_verified])
      (sql/from :orders)
      (sql/join [:users :u] [:= :u.id :orders.user_id])
      (sql/where [:= :orders.inventory_pool_id pool-id])
      (sql/where [:!= :orders.state "canceled"])
      (sql/where [:exists
                  (-> (sql/select 1)
                      (sql/from :reservations)
                      (sql/where [:= :reservations.order_id :orders.id])
                      (sql/where [:= :reservations.contract_id nil]))])
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

(defn apply-filters [sqlmap {:keys [states start-date end-date term to-be-verified]}]
  (cond-> sqlmap
    (seq states) (sql/where [:in :orders.state (map (comp str/lower-case name) states)])
    start-date (sql/where [:>= :orders.created_at start-date])
    end-date (sql/where [:<= :orders.created_at end-date])
    (seq term) (sql/where [:or
                           [:ilike :u.firstname (str "%" term "%")]
                           [:ilike :u.lastname (str "%" term "%")]
                           [:ilike :u.login (str "%" term "%")]
                           [:ilike :u.badge_id (str "%" term "%")]
                           [:ilike :orders.purpose (str "%" term "%")]])
    (true? to-be-verified)
    (sql/where [:in :orders.id
                (-> (sql/select :order_id) (sql/from :verifiable_orders))])
    (false? to-be-verified)
    (sql/where [:not-in
                :orders.id
                (-> (sql/select :order_id) (sql/from :verifiable_orders))])))

(defn get-multiple
  [{{tx :tx} :request}
   {:keys [pool-id states start-date end-date term to-be-verified page per-page]}
   _]
  (let [rows (-> (cond-> (base-sqlmap pool-id)
                   (some? to-be-verified)
                   (sql/with [:verifiable_orders (verifiable-orders-cte pool-id)]))
                 (apply-filters {:states states
                                 :start-date start-date
                                 :end-date end-date
                                 :term term
                                 :to-be-verified to-be-verified})
                 (sql/limit (or per-page 10))
                 (sql/offset (* (dec (or page 1)) (or per-page 10)))
                 sql-format
                 (->> (jdbc-query tx)))]
    {:items rows
     :total-count (-> rows first :total_count (or 0))}))

(defn get-by-id [tx id]
  (-> (sql/select :orders.id
                  :orders.user_id
                  :orders.inventory_pool_id
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

(defn- assert-submitted! [tx id]
  (when (not= "SUBMITTED" (:state (get-by-id tx id)))
    (throw (ex-info "Order is not in submitted state" {:status 422}))))

(defn- any-suspended? [tx id]
  (-> (sql/select
       [[:exists
         (-> (sql/select 1)
             (sql/from :suspensions)
             (sql/join :orders [:= :orders.inventory_pool_id :suspensions.inventory_pool_id])
             (sql/where [:= :orders.id id])
             (sql/where [:>= :suspensions.suspended_until [:raw "CURRENT_DATE"]])
             (sql/where [:or
                         [:= :suspensions.user_id :orders.user_id]
                         [:in :suspensions.user_id
                          (-> (sql/select :reservations.delegated_user_id)
                              (sql/from :reservations)
                              (sql/where [:= :reservations.order_id id])
                              (sql/where [:!= :reservations.delegated_user_id nil]))]]))]
        :suspended])
      sql-format
      (->> (jdbc-query tx))
      first
      :suspended))

(defn reject!
  [{{tx :tx} :request} {:keys [id reason]} _]
  (assert-submitted! tx id)
  (-> (sql/update :orders)
      (sql/set {:state "rejected" :reject_reason reason})
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

(defn- all-reservations-expired? [tx id]
  (let [today (local-date)
        reservations (res/get-for-open-order tx id)]
    (and (seq reservations)
         (every? #(not (t/before? today (local-date (:end_date %))))
                 reservations))))

(defn- any-pool-closed? [tx id]
  (some (fn [r]
          (let [pool-id (:inventory_pool_id r)
                workdays (pool/get-workdays tx pool-id)
                holidays (pool/get-holidays tx pool-id)
                pool-data (merge workdays {:holidays holidays})]
            (or (pool/close-time? (:start_date r) pool-data)
                (pool/close-time? (:end_date r) pool-data))))
        (res/get-for-open-order tx id)))

(defn- any-unavailable? [tx id]
  (let [order (get-by-id tx id)
        user-id (:user_id order)
        today (local-date)]
    (some (fn [r]
            (and (t/before? today (local-date (:end_date r)))
                 (< (av/maximum-available-in-pool-and-period-summed-for-groups
                     tx (:model_id r) user-id
                     (:start_date r) (:end_date r)
                     (:inventory_pool_id r)
                     [(:id r)])
                    (:quantity r))))
          (res/get-for-open-order tx id))))

(defn update-purpose!
  [{{tx :tx} :request} {:keys [id purpose]} _]
  (assert-submitted! tx id)
  (-> (sql/update :orders)
      (sql/set {:purpose purpose})
      (sql/where [:= :orders.id id])
      sql-format
      (->> (execute! tx)))
  (get-by-id tx id))

(defn- assert-valid-new-user! [tx pool-id user-id]
  (let [row (-> (sql/select
                 [[:exists
                   (-> (sql/select 1)
                       (sql/from :users)
                       (sql/where [:= :users.id user-id])
                       (sql/where [:= :users.account_enabled true]))]
                  :enabled]
                 [[:exists
                   (-> (sql/select 1)
                       (sql/from :access_rights)
                       (sql/where [:= :access_rights.user_id user-id])
                       (sql/where [:= :access_rights.inventory_pool_id pool-id]))]
                  :has_access])
                sql-format
                (->> (jdbc-query tx))
                first)]
    (when-not (:enabled row)
      (throw (ex-info "User is deactivated" {:status 422})))
    (when-not (:has_access row)
      (throw (ex-info "User does not have access to this pool" {:status 422})))))

(defn- get-customer-order-id [tx order-id]
  (-> (sql/select :customer_order_id)
      (sql/from :orders)
      (sql/where [:= :id order-id])
      sql-format
      (->> (jdbc-query tx))
      first
      :customer_order_id))

(defn- customer-order-order-count [tx customer-order-id]
  (-> (sql/select [[:count :*] :count])
      (sql/from :orders)
      (sql/where [:= :customer_order_id customer-order-id])
      sql-format
      (->> (jdbc-query tx))
      first
      :count))

(defn- fork-customer-order! [tx user-id purpose]
  (-> (sql/insert-into :customer_orders)
      (sql/values [{:user_id user-id :purpose purpose :title purpose}])
      (sql/returning :id)
      sql-format
      (->> (jdbc-query tx))
      first
      :id))

(defn swap-user!
  [{{tx :tx pool-id :pool-id} :request} {:keys [id user-id delegated-user-id]} _]
  (assert-submitted! tx id)
  (assert-valid-new-user! tx pool-id user-id)
  (let [order (get-by-id tx id)
        customer-order-id (get-customer-order-id tx id)]
    (-> (sql/update :reservations)
        (sql/set {:user_id user-id :delegated_user_id delegated-user-id})
        (sql/where [:= :reservations.order_id id])
        (sql/where [:= :reservations.contract_id nil])
        sql-format
        (->> (execute! tx)))
    (if (= 1 (customer-order-order-count tx customer-order-id))
      (do
        (-> (sql/update :orders)
            (sql/set {:user_id user-id})
            (sql/where [:= :orders.id id])
            sql-format
            (->> (execute! tx)))
        (-> (sql/update :customer_orders)
            (sql/set {:user_id user-id})
            (sql/where [:= :customer_orders.id customer-order-id])
            sql-format
            (->> (execute! tx))))
      (let [new-customer-order-id (fork-customer-order! tx user-id (:purpose order))]
        (-> (sql/update :orders)
            (sql/set {:user_id user-id :customer_order_id new-customer-order-id})
            (sql/where [:= :orders.id id])
            sql-format
            (->> (execute! tx))))))
  (get-by-id tx id))

(defn approve!
  [{{tx :tx} :request} {:keys [id force comment]} _]
  (assert-submitted! tx id)
  (when (all-reservations-expired? tx id)
    (throw (ex-info "All reservations have already ended" {:status 422})))
  (when (and (not force) (any-suspended? tx id))
    (throw (ex-info "A suspended user is associated with this order" {:status 422})))
  (when (and (not force) (any-pool-closed? tx id))
    (throw (ex-info "Pool is closed on reservation start or end date" {:status 422})))
  (when (and (not force) (any-unavailable? tx id))
    (throw (ex-info "Some reservations are not available" {:status 422})))
  (-> (sql/update :orders)
      (sql/set {:state "approved"})
      (sql/where [:= :orders.id id])
      sql-format
      (->> (execute! tx)))
  (-> (sql/update :reservations)
      (sql/set {:status "approved"})
      (sql/where [:= :reservations.order_id id])
      (sql/where [:= :reservations.contract_id nil])
      sql-format
      (->> (execute! tx)))
  (let [order (get-by-id tx id)]
    (try
      (jdbc/with-transaction+options [mail-tx (db/get-ds)]
        (mails/send-approved mail-tx order comment))
      (catch Exception e
        (log-mail-failure (:user_id order) e)))
    order))

