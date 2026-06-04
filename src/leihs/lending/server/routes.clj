(ns leihs.lending.server.routes
  (:require
   [leihs.lending.server.graphiql :as graphiql]
   [leihs.lending.server.graphql :as graphql]
   [leihs.lending.server.home :as home]
   [leihs.lending.server.sign-in :as sign-in]
   [reitit.coercion.malli :as malli-coercion]
   [reitit.ring :as reitit-ring]
   [reitit.ring.coercion :as coercion]))

(defn wrap-pool-id [handler]
  (fn [request]
    (let [pool-id (get-in request [:parameters :path :pool-id])]
      (-> request
          (assoc :pool-id pool-id)
          handler))))

(def routes
  [["/lending/"
    {:get {:handler home/handler}}]
   ["/lending/sign-in"
    {:get {:handler sign-in/get-handler}
     :post {:handler sign-in/post-handler}}]
   ["/lending/sign-out"
    {:post {:handler home/sign-out-handler}}]
   ["/lending/:pool-id"
    {:parameters {:path {:pool-id :uuid}}
     :middleware [wrap-pool-id]}
    ["/graphiql" {:get {:handler graphiql/handler}}]
    ["/graphql" {:post {:handler graphql/handler}}]]])

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
