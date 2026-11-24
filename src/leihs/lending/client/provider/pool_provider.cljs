(ns leihs.lending.client.provider.pool-provider
  (:require
   ["react-router" :refer [Outlet useParams useRouteLoaderData]]
   ["urql" :refer [Provider]]
   [leihs.lending.client.lib.urql :as urql]
   [uix.core :as uix :refer [$ defui]]))

(defui pool-provider
  "Provides the urql client of the pool in the URL. Throws a 404 to the error
   boundary when the pool isn't among the user's available pools."
  []
  (let [params (useParams)
        pool-id (aget params "pool-id")
        available-pools (-> (useRouteLoaderData "root") :currentUser :availablePools)
        client (uix/use-memo (fn []
                               (urql/make-pool-client pool-id))
                             [pool-id])]
    (when-not (some #(= pool-id (:id %)) available-pools)
      (throw (js/Response. nil #js {:status 404})))
    ($ Provider {:value client}
       ($ Outlet))))
