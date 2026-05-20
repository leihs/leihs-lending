(ns leihs.lending.routes
  (:require
   [leihs.lending.graphiql :as graphiql]
   [leihs.lending.graphql :as graphql]
   [leihs.lending.sign-in :as sign-in]
   [reitit.ring :as reitit-ring]))

(def routes
  [["/lending/sign-in"
    {:get  {:handler sign-in/get-handler}
     :post {:handler sign-in/post-handler}}]
   ["/lending/graphiql"
    {:get {:handler graphiql/handler}}]
   ["/lending/graphql"
    {:post {:handler graphql/handler}}]])

(defn handler []
  (reitit-ring/ring-handler
   (reitit-ring/router routes)
   (reitit-ring/create-default-handler)))
