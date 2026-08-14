(ns leihs.lending.client.routes.pools.orders.data
  (:require
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
           firstname
           lastname
           email
           isSuspended
           suspendedReason
         }
         reservations {
           id
         }
       }
       totalCount
     }
   }")

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
                 (clj->js (clojure.string/split s #",")))
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
