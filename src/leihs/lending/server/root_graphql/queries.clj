(ns leihs.lending.server.root-graphql.queries
  (:require
   [leihs.lending.server.resources.users :as users]))

(def resolvers
  {:current-user users/get-current})
