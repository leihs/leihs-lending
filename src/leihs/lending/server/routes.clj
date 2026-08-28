(ns leihs.lending.server.routes
  (:require
   [leihs.lending.server.graphiql :as graphiql]
   [leihs.lending.server.graphql :as graphql]
   [leihs.lending.server.home :as home]
   [leihs.lending.server.html.contracts :as html-contracts]
   [leihs.lending.server.middlewares.authorize :as authorize]
   [leihs.lending.server.middlewares.pool-id :as pool-id]
   [leihs.lending.server.sign-in :as sign-in]
   [reitit.coercion.malli :as malli-coercion]
   [reitit.ring :as reitit-ring]
   [reitit.ring.coercion :as coercion]))

(def routes
  [["/lending/sign-in"
    {:get {:handler sign-in/get-handler}
     :post {:handler sign-in/post-handler}}]
   ["/lending/sign-out"
    {:post {:handler home/sign-out-handler}}]
   ["/lending/graphql"
    {:post {:handler #(graphql/handler :root %)}}]
   ["/lending/graphiql"
    {:get {:handler graphiql/root-handler}}]
   ["/lending/:pool-id"
    {:parameters {:path {:pool-id :uuid}}
     :middleware [pool-id/wrap-pool-id]}
    ["/graphiql" {:middleware [authorize/wrap]
                  :get {:handler graphiql/handler}}]
    ["/graphql" {:middleware [authorize/wrap]
                 :authorize/format :graphql
                 :post {:handler #(graphql/handler :pool %)}}]
    ["/contracts/:contract-id"
     {:parameters {:path {:contract-id :uuid}}
      :get {:handler html-contracts/show}}]]])

(defn handler []
  (reitit-ring/ring-handler
   (reitit-ring/router routes
                       {:data {:coercion malli-coercion/coercion
                               :middleware [coercion/coerce-request-middleware]}})
   (reitit-ring/create-default-handler
    {:not-found (fn [_] {:status 404
                         :headers {"Content-Type" "text/plain"}
                         :body "Not Found"})
     :method-not-allowed (fn [_] {:status 405
                                  :headers {"Content-Type" "text/plain"}
                                  :body "Method Not Allowed"})})))
