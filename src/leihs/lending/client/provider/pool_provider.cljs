(ns leihs.lending.client.provider.pool-provider
  (:require
   ["react-router" :refer [Outlet useParams]]
   ["urql" :refer [Provider]]
   [leihs.lending.client.lib.urql :as urql]
   [uix.core :as uix :refer [$ defui]]))

(defui pool-provider []
  (let [params (useParams)
        pool-id (aget params "pool-id")
        client (uix/use-memo (fn []
                               (js/console.debug "pool-provider memo miss" pool-id)
                               (urql/make-pool-client pool-id))
                             [pool-id])]
    ($ Provider {:value client}
       ($ Outlet))))
