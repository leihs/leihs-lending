(ns leihs.lending.server.graphql.mutations
  (:require
   [leihs.lending.server.resources.orders :as orders]
   [leihs.lending.server.resources.reservations :as reservations]))

(def resolvers
  {:reject-order orders/reject!
   :approve-order orders/approve!
   :update-order-purpose orders/update-purpose!
   :swap-order-user orders/swap-user!
   :create-model-reservation reservations/create-for-model!
   :create-option-reservation reservations/create-for-option!
   :create-reservation-by-inventory-code reservations/create-by-inventory-code!
   :delete-reservations reservations/delete!
   :swap-model reservations/swap-model!})
