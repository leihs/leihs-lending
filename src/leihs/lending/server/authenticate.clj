(ns leihs.lending.server.authenticate
  (:require
   [clojure.string :as str]
   [leihs.core.graphql.helpers :refer [error-as-graphql-object]]
   [ring.util.response :refer [redirect]]))

(def skip-paths #{"/lending/sign-in"})

(defn- graphql-uri? [uri]
  (or (= uri "/lending/graphql")
      (str/ends-with? uri "/graphql")))

(defn- graphiql-uri? [uri]
  (or (= uri "/lending/graphiql")
      (str/ends-with? uri "/graphiql")))

(defn wrap [handler]
  (fn [{:keys [uri] :as request}]
    (cond
      (or (skip-paths uri) (graphiql-uri? uri) (:authenticated-entity request)) (handler request)
      (graphql-uri? uri) {:status 401
                          :body (error-as-graphql-object "UNAUTHENTICATED" "Not authenticated")}
      (not (str/starts-with? uri "/lending/")) (handler request)
      :else (redirect "/lending/sign-in"))))
