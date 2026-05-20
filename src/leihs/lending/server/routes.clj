(ns leihs.lending.server.routes
  (:require
   [leihs.lending.server.graphiql :as graphiql]
   [leihs.lending.server.graphql :as graphql]
   [leihs.lending.server.home :as home]
   [leihs.lending.server.sign-in :as sign-in]
   [reitit.ring :as reitit-ring]))

(def routes
  [["/lending/"
    {:get {:handler home/handler}}]
   ["/lending/sign-in"
    {:get  {:handler sign-in/get-handler}
     :post {:handler sign-in/post-handler}}]
   ["/lending/sign-out"
    {:post {:handler home/sign-out-handler}}]
   ["/lending/graphiql"
    {:get {:handler graphiql/handler}}]
   ["/lending/graphql"
    {:post {:handler graphql/handler}}]])

(defn handler []
  (reitit-ring/ring-handler
   (reitit-ring/router routes)
   (reitit-ring/create-default-handler
    {:not-found (fn [_] {:status 404 :headers {"Content-Type" "text/plain"} :body "Not Found"})
     :method-not-allowed (fn [_] {:status 405 :headers {"Content-Type" "text/plain"} :body "Method Not Allowed"})})))
