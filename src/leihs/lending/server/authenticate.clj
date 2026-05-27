(ns leihs.lending.server.authenticate
  (:require
   [clojure.string :as str]
   [leihs.core.graphql.helpers :refer [error-as-graphql-object]]
   [ring.util.response :refer [redirect]]))

(def skip-paths #{"/lending/graphiql" "/lending/sign-in"})
(def graphql-path "/lending/graphql")

(defn wrap [handler]
  (fn [{:keys [uri] :as request}]
    (cond
      (or (skip-paths uri) (:authenticated-entity request)) (handler request)
      (= graphql-path uri) {:status 401
                            :body (error-as-graphql-object "UNAUTHENTICATED" "Not authenticated")}
      (not (str/starts-with? uri "/lending/")) (handler request)
      :else (redirect "/lending/sign-in"))))
