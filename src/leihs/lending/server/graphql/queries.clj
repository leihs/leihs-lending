(ns leihs.lending.server.graphql.queries
  (:require
   [leihs.lending.server.resources.models :as models]
   [leihs.lending.server.resources.orders :as orders]
   [leihs.lending.server.resources.reservations :as reservations]
   [leihs.lending.server.resources.users :as users]))

(def resolvers
  {:current-user users/get-current
   :hello        (fn [_ctx _args _val] "Hello from lending!")
   :model        models/get-one
   :orders       orders/get-multiple
   :reservations reservations/get-multiple
   :user         users/get-one})
