(ns leihs.lending.server.root-graphql.mutations
  (:require
   [leihs.lending.server.resources.users :as users]))

(def resolvers
  {:switch-language users/switch-language!})
