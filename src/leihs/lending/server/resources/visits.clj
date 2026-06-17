(ns leihs.lending.server.resources.visits
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn- base-sqlmap [pool-id]
  (-> (sql/select :v.id
                  :v.date
                  :v.reservation_ids
                  [[:upper :v.type] :visit_type]
                  :v.quantity
                  :v.with_user_to_verify
                  :v.with_user_and_model_to_verify
                  :v.user_id
                  [[:< :v.date [:raw "CURRENT_DATE"]] :is_overdue]
                  [{:select [[[:string_agg [:distinct :o.purpose] [:inline "\n"]]]]
                    :from [[:reservations :r]]
                    :join [[:orders :o] [:= :o.id :r.order_id]]
                    :where [:= :r.id [:any :v.reservation_ids]]}
                   :comment]
                  [{:select [[[:string_agg [:distinct :co.title] [:inline "\n"]]]]
                    :from [[:reservations :r]]
                    :join [[:orders :o] [:= :o.id :r.order_id]
                           [:customer_orders :co] [:= :co.id :o.customer_order_id]]
                    :where [:= :r.id [:any :v.reservation_ids]]}
                   :project_title]
                  [{:select [[[:min :r.start_date]]]
                    :from [[:reservations :r]]
                    :where [:= :r.id [:any :v.reservation_ids]]} :start_date]
                  [{:select [[[:max :r.end_date]]]
                    :from [[:reservations :r]]
                    :where [:= :r.id [:any :v.reservation_ids]]} :end_date])
      (sql/from [:visits :v])
      (sql/join [:users :u] [:= :u.id :v.user_id])
      (sql/left-join [:suspensions :s]
                     [:and
                      [:= :s.user_id :v.user_id]
                      [:= :s.inventory_pool_id :v.inventory_pool_id]
                      [:>= :s.suspended_until [:raw "CURRENT_DATE"]]])
      (sql/where [:= :v.inventory_pool_id pool-id])
      (sql/order-by [:v.date :asc] [:v.id :asc])))

(defn- apply-filters [sqlmap {:keys [date start-date end-date visit-type term verification]}]
  (cond-> sqlmap
    date (sql/where [:= :v.date date])
    start-date (sql/where [:>= :v.date start-date])
    end-date (sql/where [:<= :v.date end-date])
    visit-type (sql/where [:= :v.type (str/lower-case (name visit-type))])
    (seq term) (sql/where [:or
                           [:ilike :u.firstname (str "%" term "%")]
                           [:ilike :u.lastname (str "%" term "%")]])
    (= verification :NONE_REQUIRED)
    (sql/where [:and [:= :v.with_user_to_verify false]
                [:= :v.with_user_and_model_to_verify false]])
    (= verification :USER)
    (sql/where [:and [:= :v.with_user_to_verify true]
                [:= :v.with_user_and_model_to_verify false]])
    (= verification :USER_AND_MODEL)
    (sql/where [:= :v.with_user_and_model_to_verify true])))

(defn get-multiple
  [{{tx :tx pool-id :pool-id} :request}
   {:keys [date start-date end-date visit-type term verification page per-page]}
   _]
  (-> (base-sqlmap pool-id)
      (apply-filters {:date date :start-date start-date :end-date end-date
                      :visit-type visit-type :term term :verification verification})
      (sql/limit (or per-page 10))
      (sql/offset (* (dec (or page 1)) (or per-page 10)))
      sql-format
      (->> (jdbc-query tx))))
