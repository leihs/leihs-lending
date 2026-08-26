(ns leihs.lending.client.lib.urql
  (:require
   ["urql" :refer [createClient fetchExchange]]
   [leihs.lending.client.lib.csrf :as csrf]
   [leihs.lending.client.lib.utils :refer [jc cj]]
   [promesa.core :as p]))

(defn- fetch-options []
  (let [headers (cond-> {"Content-Type" "application/json"}
                  csrf/token
                  (assoc csrf/header-field-name csrf/token))]
    (cj {:credentials "same-origin"
         :method "POST"
         :headers headers})))

(defn- make-client
  "Builds a urql client targeting `url`. Use the pool-scoped form
   (e.g. `/lending/<pool-id>/graphql`) inside <pool-provider>; use the
   no-pool form for routes that aren't pool-scoped."
  [url]
  (createClient
   (cj {:url url
        :fetchOptions fetch-options
        :preferGetMethod false
        :exchanges [fetchExchange]})))

;; === PUBLIC ===

(defn run-query
  "Runs a urql query on the given client and resolves to a clj map of the
   response data. Throws the GraphQL/network error if one occurred so React
   Router routes it to the errorElement."
  [^js client query variables]
  (js/console.debug "urql run-query")
  (p/let [^js source (.query client query (cj (or variables {})))
          result (.toPromise source)]
    (when-let [error (.-error result)]
      (throw error))
    (jc (.-data result))))

(defn run-mutation
  "Runs a urql mutation on the given client and resolves to a clj map of the
   response data. Throws the GraphQL/network error if one occurred so React
   Router routes it to the errorElement."
  [^js client mutation variables]
  (js/console.debug "urql run-mutation")
  (p/let [^js source (.mutation client mutation (cj (or variables {})))
          result (.toPromise source)]
    (when-let [error (.-error result)]
      (throw error))
    (jc (.-data result))))

(def default-client
  "Client for routes outside any pool (sign-in flow, pool picker, profile)."
  (do (js/console.debug "urql initialize default-client")
      (make-client "/lending/graphql")))

(def ^:private pool-clients
  "Module-level cache of pool-id → urql client"
  (atom {}))

(defn make-pool-client
  "Returns the cached urql client for `pool-id`, creating it on first call.
   Subsequent calls with the same pool-id return the same JS instance, so
   loaders and React components share one client per pool."
  [pool-id]
  (or (get @pool-clients pool-id)
      (let [client (do (js/console.debug "urql make-pool-client" pool-id)
                       (make-client (str "/lending/" pool-id "/graphql")))]
        (swap! pool-clients assoc pool-id client)
        client)))
