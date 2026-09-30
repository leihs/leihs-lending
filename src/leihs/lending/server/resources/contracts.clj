(ns leihs.lending.server.resources.contracts
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn contracts-to-be-verified-cte [pool-id]
  (-> (sql/select :r.contract_id)
      (sql/from [:reservations :r])
      (sql/join [:contracts :c] [:= :c.id :r.contract_id])
      (sql/join [:entitlements :e] [:= :e.model_id :r.model_id])
      (sql/join [:entitlement_groups :eg] [:= :e.entitlement_group_id :eg.id])
      (sql/join [:entitlement_groups_users :egu] [:= :eg.id :egu.entitlement_group_id])
      (sql/where [:= :c.inventory_pool_id pool-id])
      (sql/where [:= :eg.is_verification_required true])
      (sql/where [:= :egu.user_id :r.user_id])
      (sql/where [:= :eg.inventory_pool_id :r.inventory_pool_id])))

(def to-be-verified-expr
  [:exists
   (-> (sql/select 1)
       (sql/from :contracts_to_be_verified)
       (sql/where [:= :contracts_to_be_verified.contract_id :contracts.id]))])

(defn base-sqlmap [pool-id]
  (-> (sql/with [:contracts_to_be_verified (contracts-to-be-verified-cte pool-id)])
      (sql/select :contracts.id
                  :contracts.compact_id
                  :contracts.note
                  :contracts.purpose
                  [[:upper :contracts.state] :state]
                  :contracts.created_at
                  :contracts.updated_at
                  :contracts.user_id
                  [:contracts.id :contract_id]
                  [[:over [[:count :*] {}]] :total_count]
                  [to-be-verified-expr :to_be_verified])
      (sql/from :contracts)
      (sql/where [:= :contracts.inventory_pool_id pool-id])
      (sql/order-by [:contracts.created_at :desc] [:contracts.id :asc])))

(defn apply-filters [sqlmap {:keys [state term to-be-verified start-date end-date]}]
  (cond-> sqlmap
    state (sql/where [:= :contracts.state (str/lower-case (name state))])
    (seq term) (sql/where [:or
                           [:ilike :contracts.compact_id (str "%" term "%")]
                           [:ilike :contracts.purpose (str "%" term "%")]
                           [:ilike :contracts.note (str "%" term "%")]])
    (some? to-be-verified) (sql/where [:= to-be-verified-expr to-be-verified])
    start-date (sql/where [:>= :contracts.created_at start-date])
    end-date (sql/where [:<= :contracts.created_at end-date])))

(defn get-one [tx contract-id pool-id]
  (-> (sql/select :contracts.id
                  :contracts.compact_id
                  :contracts.note
                  :contracts.purpose
                  [[:upper :contracts.state] :state]
                  :contracts.created_at
                  :contracts.updated_at
                  :contracts.user_id)
      (sql/from :contracts)
      (sql/where [:= :contracts.id contract-id])
      (sql/where [:= :contracts.inventory_pool_id pool-id])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn get-multiple
  [{{tx :tx pool-id :pool-id} :request}
   {:keys [state term to-be-verified start-date end-date page per-page]}
   _]
  (let [rows (-> (base-sqlmap pool-id)
                 (apply-filters {:state state :term term :to-be-verified to-be-verified
                                 :start-date start-date :end-date end-date})
                 (sql/limit (or per-page 10))
                 (sql/offset (* (dec (or page 1)) (or per-page 10)))
                 sql-format
                 (->> (jdbc-query tx)))]
    {:items rows
     :total-count (-> rows first :total_count (or 0))}))
