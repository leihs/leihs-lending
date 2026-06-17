(ns leihs.lending.server.graphql.queries
  (:require
   [leihs.lending.server.resources.models :as models]
   [leihs.lending.server.resources.orders :as orders]
   [leihs.lending.server.resources.reminders :as reminders]
   [leihs.lending.server.resources.reservations :as reservations]
   [leihs.lending.server.resources.users :as users]
   [leihs.lending.server.resources.visits :as visits]))

(def resolvers
  {:current-user users/get-current
   :model models/get-one
   :order orders/get-one
   :orders orders/get-multiple
   :reminders reminders/get-multiple
   :reservations reservations/get-multiple
   :user users/get-one
   :visits visits/get-multiple})
