(ns leihs.lending.client.routes.pools.orders.edit.data
  (:require
   [leihs.lending.client.lib.urql :as urql]
   [promesa.core :as p]))

(def order-query
  "query ($id: UUID!) {
     order(id: $id) {
       id
       purpose
       state
       user {
         id
         firstname
         lastname
         isSuspended
       }
       reservationLines {
         quantity
         availableQuantity
         startDate
         endDate
         reservationIds
         model {
           id
           name
           type
           isPackage
           thumbnailUrl
         }
       }
     }
   }")

(defn loader
  "Loader for /lending/:pool-id/orders/:order-id — the order with the lines the
   edit page lists."
  [pool-id order-id]
  (let [client (urql/make-pool-client pool-id)]
    (p/let [data (urql/run-query client order-query {:id order-id})]
      (if-let [order (:order data)]
        {:order order}
        (throw (js/Response. nil #js {:status 404 :statusText "Order not found"}))))))

(def model-availability-query
  "query ($modelId: UUID!, $startDate: Date!, $endDate: Date!, $userId: UUID,
          $excludeReservationIds: [UUID!]) {
     model(id: $modelId) {
       availability(startDate: $startDate, endDate: $endDate, userId: $userId,
                    excludeReservationIds: $excludeReservationIds) {
         date
         quantity
         totalQuantity
         holidayName
         startDateRestrictions
         endDateRestrictions
       }
     }
   }")

(defn use-model-availability
  "Loads the booking calendar of a single model, as it looks once the
   reservations being edited are taken out of it."
  [variables enabled?]
  (-> (urql/use-lazy-query model-availability-query variables enabled?)
      (update :data #(-> % :model :availability))))

(def reservations-availability-query
  "query ($reservationIds: [UUID!]!, $startDate: Date!, $endDate: Date!) {
     availability(reservationIds: $reservationIds, startDate: $startDate,
                  endDate: $endDate) {
       date
       available
       holidayName
       startDateRestrictions
       endDateRestrictions
     }
   }")

(defn use-reservations-availability
  "Loads the booking calendar telling whether all the given reservations can be
   served, day by day."
  [variables enabled?]
  (-> (urql/use-lazy-query reservations-availability-query variables enabled?)
      (update :data :availability)))

(defn- update-lines-mutation
  "One `createModelReservation` per entry of `model-vars` (the variable holding
   its model), followed by deleting the replaced reservations. Mutation fields
   run in order, so the order never runs out of reservations in between."
  [model-var-names model-vars]
  (str "mutation ($orderId: UUID!, $userId: UUID!, $startDate: Date!,"
       " $endDate: Date!, $ids: [UUID!]!"
       (apply str (map #(str ", $" % ": UUID!") model-var-names))
       ") {\n"
       (apply str (map-indexed
                   (fn [i model-var]
                     (str "  create" i ": createModelReservation(orderId: $orderId,"
                          " userId: $userId, modelId: $" model-var ","
                          " startDate: $startDate, endDate: $endDate) { id }\n"))
                   model-vars))
       "  deleteReservations(ids: $ids)\n}"))

(defn update-lines!
  "Replaces the reservations of the order's `lines` by ones over the new date
   range — `quantity` per line if given, else the line's own quantity. Runs as
   one request and thus one transaction."
  [pool-id order lines {:keys [start-date end-date quantity]}]
  (let [model-ids (vec (distinct (map #(get-in % [:model :id]) lines)))
        var-names (mapv #(str "modelId" %) (range (count model-ids)))
        var-of (zipmap model-ids var-names)
        model-vars (for [line lines
                         _ (range (or quantity (:quantity line)))]
                     (var-of (get-in line [:model :id])))]
    (urql/run-mutation (urql/make-pool-client pool-id)
                       (update-lines-mutation var-names model-vars)
                       (merge {:orderId (:id order)
                               :userId (get-in order [:user :id])
                               :startDate start-date
                               :endDate end-date
                               :ids (vec (mapcat :reservationIds lines))}
                              (zipmap (map keyword var-names) model-ids)))))

(def update-purpose-mutation
  "mutation ($id: UUID!, $purpose: NonEmptyString!) {
     updateOrderPurpose(id: $id, purpose: $purpose) {
       id
       purpose
     }
   }")

(defn update-purpose!
  "Replaces the purpose of a submitted order."
  [pool-id order-id purpose]
  (urql/run-mutation (urql/make-pool-client pool-id)
                     update-purpose-mutation
                     {:id order-id :purpose purpose}))
