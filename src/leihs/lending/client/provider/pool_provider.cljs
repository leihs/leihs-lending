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
                               (js/console.debug "recreating client")
                               (urql/make-client (str "/lending/" pool-id "/graphql")))
                             [pool-id])]
    ($ Provider {:value client}
       ($ Outlet))))
