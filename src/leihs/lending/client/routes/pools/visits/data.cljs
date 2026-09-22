(ns leihs.lending.client.routes.pools.visits.data
  (:require
   [leihs.lending.client.lib.urql :as urql]
   [promesa.core :as p]))

(def list-query
  "query ($startDate: Date, $endDate: Date, $visitType: VisitTypeEnum,
                $term: String, $verification: VerificationEnum,
                $page: Int, $perPage: Int) {
     visits(startDate: $startDate, endDate: $endDate, visitType: $visitType,
            term: $term, verification: $verification,
            page: $page, perPage: $perPage) {
       items {
         id
         date
         startDate
         endDate
         visitType
         isOverdue
         quantity
         user {
           id
           firstname
           lastname
           isSuspended
         }
         reminders {
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
     visit(id: $id) {
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
  [visit-id enabled?]
  (-> (urql/use-lazy-query items-query {:id visit-id} enabled?)
      (update :data #(-> % :visit :reservations))))

(def reminders-query
  "query ($id: UUID!) {
     visit(id: $id) {
       reminders {
         id
         createdAt
         subject
       }
     }
   }")

(defn use-reminders
  "Loads the reminders the reminders popover lists; the list query carries only
   their ids, for the trigger's count."
  [visit-id enabled?]
  (-> (urql/use-lazy-query reminders-query {:id visit-id} enabled?)
      (update :data #(-> % :visit :reminders))))

(defn list-loader
  "Loader for /lending/:pool-id/visits — reads filter/pagination state from
   the URL search params, maps them to `visits` query variables, and runs the
   query against the pool-scoped endpoint. Returns the visits plus the paging
   state (including the backend-provided total) so the page can render the
   pager."
  [pool-id search-params]
  (let [get-param (fn [k] (not-empty (.get search-params k)))
        page (js/parseInt (or (get-param "page") "1"))
        per-page (js/parseInt (or (get-param "size") "50"))
        variables (into {:page page :perPage per-page}
                        (->> [:term
                              :startDate
                              :endDate
                              :visitType
                              :verification]
                             (map #(vector % (get-param (name %))))
                             (filter second)))
        client (urql/make-pool-client pool-id)]
    (p/let [data (urql/run-query client list-query variables)
            visits (get-in data [:visits :items])
            total-count (get-in data [:visits :totalCount])]
      {:visits visits
       :page page
       :per-page per-page
       :total-count total-count})))
