(ns leihs.lending.server.middlewares.authorize
  (:require [leihs.core.graphql.helpers :refer [error-as-graphql-object]]))

(def AUTHORIZED-ROLES #{"lending_manager" "inventory_manager"})

(defn- authorized? [{:keys [pool-id authenticated-entity]}]
  (->> (:access-rights authenticated-entity)
       (some #(and (= (:inventory_pool_id %) pool-id)
                   (AUTHORIZED-ROLES (:role %))))))

(defn- deny [request]
  (let [fmt (get-in request [:reitit.core/match :data :authorize/format])]
    (if (= fmt :graphql)
      {:status 403 :body (error-as-graphql-object "FORBIDDEN" "Not authorized")}
      {:status 403 :headers {"Content-Type" "text/plain"} :body "Forbidden"})))

(defn wrap [handler]
  (fn [request]
    (if (authorized? request)
      (handler request)
      (deny request))))
