(ns leihs.lending.server.graphql.queries
  (:require
   [leihs.lending.server.resources.users :as users]))

(def resolvers
  {:query/hello   (fn [_ctx _args _val] "Hello from lending!")
   :current-user  users/get-current})
