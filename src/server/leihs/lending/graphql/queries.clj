(ns leihs.lending.graphql.queries
  (:require
   [leihs.lending.resources.users :as users]))

(def resolvers
  {:query/hello   (fn [_ctx _args _val] "Hello from lending!")
   :current-user  users/get-current})
