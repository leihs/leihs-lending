(ns leihs.lending.server.graphql.mutations
  (:require
   [leihs.lending.server.resources.orders :as orders]
   [leihs.lending.server.resources.reservations :as reservations]))

(def resolvers
  {:reject-order orders/reject!
   :approve-order orders/approve!
   :update-order-purpose orders/update-purpose!
   :swap-order-user orders/swap-user!
   :create-reservation reservations/create!
   :delete-reservation reservations/delete!})
