(ns leihs.lending.server.resources.availability
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [java-time :as jt]
   [leihs.core.availability.changes :as ch]
   [leihs.core.availability.pool :as pool]
   [leihs.core.availability.queries :as q]
   [leihs.lending.server.resources.entitlement-groups :as entitlement-groups]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn- group->row
  [[group-id {:keys [in-quantity running-reservations]}]]
  {:entitlement_group_id (when-not (= group-id :general) group-id)
   :in_quantity in-quantity
   :reservation_ids running-reservations})

(defn get-multiple
  "Raw availability changes of the model in the pool, sorted by date, starting
  today. Each change holds until the next one. General group comes first, the
  others follow sorted by id."
  [{{tx :tx pool-id :pool-id} :request} _ {model-id :id}]
  (->> (ch/main tx model-id pool-id)
       (sort-by key)
       (map (fn [[date groups]]
              {:date date
               :groups (->> groups
                            (sort-by (fn [[group-id _]]
                                       (when-not (= group-id :general) (str group-id))))
                            (map group->row))}))))

(defn- assert-group-in-pool! [tx pool-id group-id]
  (when-not (-> entitlement-groups/base-sqlmap
                (sql/where [:= :entitlement_groups.id group-id])
                (sql/where [:= :entitlement_groups.inventory_pool_id pool-id])
                sql-format
                (->> (jdbc-query tx))
                seq)
    (throw (ex-info "Entitlement group not found" {:status 404}))))

(defn- get-group-ids
  "General group plus the given group or the user's groups."
  [tx pool-id user-id entitlement-group-id]
  (when (= (some? user-id) (some? entitlement-group-id))
    (throw (ex-info "Exactly one of userId or entitlementGroupId is required"
                    {:status 422})))
  (if entitlement-group-id
    (do (assert-group-in-pool! tx pool-id entitlement-group-id)
        [:general entitlement-group-id])
    (cons :general (q/get-user-group-ids tx user-id))))

(defn- summed-quantity
  "Minimum over the changes of the summed in-quantities of the groups (all
  groups when `group-ids` is nil). Not floored, negative = overbooked."
  [changes group-ids]
  (->> (vals changes)
       (map (fn [allocs]
              (->> (cond-> allocs group-ids (select-keys group-ids))
                   vals
                   (map :in-quantity)
                   (apply +))))
       (apply min)))

(defn- intervals
  "Splits [start, end] at the change dates into intervals of constant
  availability."
  [changes start end]
  (let [dates (->> (keys changes)
                   (filter #(and (jt/after? % start) (not (jt/after? % end))))
                   sort)]
    (map (fn [from to-next]
           [from (if to-next (jt/minus to-next (jt/days 1)) end)])
         (cons start dates)
         (concat dates [nil]))))

(defn- get-quantities
  "Per-date quantity (given groups) and total quantity (all groups). Dates
  before today get 0."
  [changes group-ids start end]
  (let [today (ch/local-date)
        start* (if (jt/before? start today) today start)
        past (when (jt/before? start today)
               (->> (ch/explode-date-range start (jt/min end (jt/minus today (jt/days 1))))
                    (map #(hash-map :date % :quantity 0 :total_quantity 0))))
        upcoming (when-not (jt/before? end start*)
                   (mapcat (fn [[from to]]
                             (let [inner (ch/between changes from to)
                                   quantity (summed-quantity inner group-ids)
                                   total (summed-quantity inner nil)]
                               (->> (ch/explode-date-range from to)
                                    (map #(hash-map :date %
                                                    :quantity quantity
                                                    :total_quantity total)))))
                           (intervals changes start* end)))]
    (concat past upcoming)))

(defn- get-pool-calendar-data
  "Workdays (incl. max_visits) and upcoming holidays of the pool."
  [tx pool-id]
  (-> (sql/select :*)
      (sql/from :workdays)
      (sql/where [:= :inventory_pool_id pool-id])
      sql-format
      (->> (jdbc-query tx))
      first
      (assoc :holidays (pool/get-holidays tx pool-id))))

(defn- get-visits-counts
  "Date -> number of visits (see `visits` view) in the pool."
  [tx pool-id start end]
  (-> (sql/select :date [[:count :*] :visits_count])
      (sql/from :visits)
      (sql/where [:= :inventory_pool_id pool-id]
                 [:between :date start end])
      (sql/group-by :date)
      sql-format
      (->> (jdbc-query tx)
           (map (juxt :date :visits_count))
           (into {}))))

(defn- visits-capacity-reached? [date visits-count pool-data]
  (let [index (-> date .getDayOfWeek .getValue (mod 7) str keyword)]
    (when-let [max-visits (some-> pool-data :max_visits index str parse-long)]
      (>= visits-count max-visits))))

(defn- restrictions
  "Informational only, lending managers may override them."
  [date visits-count pool-data]
  (cond-> []
    (not (pool/working-day? date pool-data)) (conj :NON_WORKDAY)
    (pool/get-holiday date pool-data) (conj :HOLIDAY)
    (visits-capacity-reached? date visits-count pool-data) (conj :VISITS_CAPACITY_REACHED)))

(defn get-calendar
  "Booking calendar of the model in the pool. Quantity for the general group
  plus either the given entitlement group or the user's groups."
  [{{tx :tx pool-id :pool-id} :request}
   {:keys [start-date end-date user-id entitlement-group-id exclude-reservation-ids]}
   {model-id :id}]
  (when (jt/before? end-date start-date)
    (throw (ex-info "endDate must not be before startDate" {:status 422})))
  (let [group-ids (get-group-ids tx pool-id user-id entitlement-group-id)
        changes (ch/main tx model-id pool-id exclude-reservation-ids)
        pool-data (get-pool-calendar-data tx pool-id)
        visits-counts (get-visits-counts tx pool-id start-date end-date)]
    (map (fn [{:keys [date] :as day}]
           (let [rs (restrictions date (get visits-counts date 0) pool-data)]
             (assoc day
                    :start_date_restrictions rs
                    :end_date_restrictions rs)))
         (get-quantities changes group-ids start-date end-date))))
