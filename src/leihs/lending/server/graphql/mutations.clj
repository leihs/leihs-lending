(ns leihs.lending.server.graphql.mutations
  (:require
   [leihs.lending.server.resources.orders :as orders]))

(def resolvers
  {:reject-order orders/reject!
   :approve-order orders/approve!
   :update-order-purpose orders/update-purpose!
   :swap-order-user orders/swap-user!})
