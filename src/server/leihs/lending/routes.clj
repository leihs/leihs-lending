(ns leihs.lending.routes
  (:require
   [leihs.lending.graphiql :as graphiql]
   [leihs.lending.graphql :as graphql]
   [reitit.ring :as reitit-ring]))

(def routes
  [["/lending/graphiql"
    {:get {:handler graphiql/handler}}]
   ["/lending/graphql"
    {:post {:handler graphql/handler}}]])

(defn handler []
  (reitit-ring/ring-handler
   (reitit-ring/router routes)
   (reitit-ring/create-default-handler)))
