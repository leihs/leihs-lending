(ns leihs.lending.client.routes.pools.orders.data
  (:require
   [clojure.string :refer [split]]
   [leihs.lending.client.lib.urql :as urql]
   [promesa.core :as p]))

(def list-query
  "query ($poolId: UUID!, $states: [OrderStateEnum!], $startDate: Date,
                $endDate: Date, $term: String, $toBeVerified: Boolean,
                $page: Int, $perPage: Int) {
     orders(poolId: $poolId, states: $states, startDate: $startDate,
            endDate: $endDate, term: $term, toBeVerified: $toBeVerified,
            page: $page, perPage: $perPage) {
       items {
          id
          state
          rejectReason
          toBeVerified
          purpose
          startDate
          endDate
          createdAt
          user {
           id
           firstname
           lastname
           isSuspended
         }
         reservations {
           id
         }
       }
       totalCount
     }
   }")

(def user-query
  "query ($id: UUID!) {
     user(id: $id) {
       email
       badgeId
       img256Url
       suspendedReason
     }
   }")

(defn use-user-details
  "Loads what the user popover shows on top of what the list already carries."
  [user-id enabled?]
  (-> (urql/use-lazy-query user-query {:id user-id} enabled?)
      (update :data :user)))

(def items-query
  "query ($id: UUID!) {
     order(id: $id) {
       reservations {
         id
         quantity
         startDate
         endDate
         model { name }
         option { name }
       }
     }
   }")

(defn use-items
  "Loads the reservations the items popover lists."
  [order-id enabled?]
  (-> (urql/use-lazy-query items-query {:id order-id} enabled?)
      (update :data #(-> % :order :reservations))))

(defn list-loader
  "Loader for /lending/:pool-id/orders — reads filter/pagination state from
   the URL search params, maps them to `orders` query variables, and runs the
   query against the pool-scoped endpoint. Returns the orders plus the paging
   state (including the backend-provided total) so the page can render the
   pager."
  [pool-id search-params]
  (let [get-param (fn [k] (not-empty (.get search-params k)))
        page (js/parseInt (or (get-param "page") "1"))
        per-page (js/parseInt (or (get-param "size") "50"))
        states (when-let [s (get-param "states")]
                 (clj->js (split s #",")))
        variables (cond-> {:poolId pool-id :page page :perPage per-page}
                    states (assoc :states states)
                    (get-param "startDate") (assoc :startDate (get-param "startDate"))
                    (get-param "endDate") (assoc :endDate (get-param "endDate"))
                    (get-param "term") (assoc :term (get-param "term"))
                    (get-param "toBeVerified") (assoc :toBeVerified
                                                      (= (get-param "toBeVerified") "true")))
        client (urql/make-pool-client pool-id)]
    (p/let [data (urql/run-query client list-query variables)
            orders (get-in data [:orders :items])
            total-count (get-in data [:orders :totalCount])]
      {:orders orders
       :page page
       :per-page per-page
       :total-count total-count})))

(def reject-mutation
  "mutation ($id: UUID!, $reason: NonEmptyString!) {
     rejectOrder(id: $id, reason: $reason) {
       id
       state
       rejectReason
     }
   }")

(defn reject-order!
  "Rejects a submitted order; `reason` becomes part of the rejection e-mail."
  [pool-id order-id reason]
  (urql/run-mutation (urql/make-pool-client pool-id)
                     reject-mutation
                     {:id order-id :reason reason}))

(def approve-mutation
  "mutation ($id: UUID!, $force: Boolean, $comment: String) {
     approveOrder(id: $id, force: $force, comment: $comment) {
       id
       state
     }
   }")

(defn approve-order!
  "Approves a submitted order; fails if the backend finds a blocking condition.
   `force` overrides the conditions that allow it, `comment` becomes part of
   the approval e-mail."
  ([pool-id order-id]
   (approve-order! pool-id order-id nil nil))
  ([pool-id order-id force comment]
   (urql/run-mutation (urql/make-pool-client pool-id)
                      approve-mutation
                      {:id order-id :force force :comment comment})))
