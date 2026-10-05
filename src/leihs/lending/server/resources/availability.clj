(ns leihs.lending.server.resources.availability
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [java-time :as jt]
   [leihs.core.availability.changes :as ch]
   [leihs.core.availability.core :as av]
   [leihs.core.availability.pool :as pool]
   [leihs.core.availability.queries :as q]
   [leihs.lending.server.resources.entitlement-groups :as entitlement-groups]
   [leihs.lending.server.resources.reservations :as res]
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

(defn- get-pool-calendar-data
  "Workdays (incl. max_visits) and upcoming holidays of the pool."
  [tx pool-id]
  (assoc (pool/get-workdays tx pool-id)
         :holidays (pool/get-holidays tx pool-id)))

(defn- get-visits-counts
  "Date -> number of visits (see `visits` view) in the pool, as in legacy.
  The view splits hand-overs by status, so a user with submitted and approved
  reservations on the same day counts twice (borrow counts once)."
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

(defn- restrictions
  "Informational only, lending managers may override them."
  [date visits-count pool-data]
  (cond-> []
    (not (pool/working-day? date pool-data)) (conj :NON_WORKDAY)
    (pool/get-holiday date pool-data) (conj :HOLIDAY)
    (pool/visits-capacity-reached? date visits-count pool-data) (conj :VISITS_CAPACITY_REACHED)))

(defn- with-restrictions
  "Adds the same start and end date restrictions to each day."
  [tx pool-id start end days]
  (let [pool-data (get-pool-calendar-data tx pool-id)
        visits-counts (get-visits-counts tx pool-id start end)]
    (map (fn [{:keys [date] :as day}]
           (let [rs (restrictions date (get visits-counts date 0) pool-data)]
             (assoc day
                    :start_date_restrictions rs
                    :end_date_restrictions rs)))
         days)))

(defn- model-calendar
  [tx pool-id model-id {:keys [start-date end-date user-id entitlement-group-id
                               exclude-reservation-ids]}]
  (av/booking-calendar (ch/main tx model-id pool-id exclude-reservation-ids)
                       (get-group-ids tx pool-id user-id entitlement-group-id)
                       start-date
                       end-date))

(defn- get-single-user-id [reservations]
  (let [user-ids (distinct (map :user_id reservations))]
    (when (> (count user-ids) 1)
      (throw (ex-info "Reservations must belong to a single user" {:status 422})))
    (first user-ids)))

(defn- reservations-calendar
  "A day is available when each model of the reservations has at least their
  summed quantity, with the reservations themselves excluded. Option lines
  are ignored."
  [tx pool-id {:keys [reservation-ids start-date end-date]}]
  (let [rs (res/get-in-pool tx pool-id reservation-ids)
        group-ids (cons :general (q/get-user-group-ids tx (get-single-user-id rs)))
        availabilities (->> rs
                            (filter :model_id)
                            (group-by :model_id)
                            (map (fn [[model-id model-rs]]
                                   (let [required (apply + (map :quantity model-rs))]
                                     (->> (av/booking-calendar
                                           (ch/main tx model-id pool-id reservation-ids)
                                           group-ids
                                           start-date
                                           end-date)
                                          (map #(>= (:quantity %) required)))))))]
    (apply map
           (fn [date & oks]
             {:date date :available (every? true? oks)})
           (ch/explode-date-range start-date end-date)
           availabilities)))

(defn get-calendar
  "Booking calendar in the pool. For a model (parent): quantity for the
  general group plus either the given entitlement group or the user's groups.
  For `reservation-ids`: availability of all of them per day."
  [{{tx :tx pool-id :pool-id} :request}
   {:keys [start-date end-date] :as args}
   {model-id :id}]
  (when (jt/before? end-date start-date)
    (throw (ex-info "endDate must not be before startDate" {:status 422})))
  (->> (if model-id
         (model-calendar tx pool-id model-id args)
         (reservations-calendar tx pool-id args))
       (with-restrictions tx pool-id start-date end-date)))
