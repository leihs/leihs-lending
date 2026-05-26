(ns leihs.lending.server.authenticate
  (:require
   [clojure.string :as str]
   [ring.util.response :refer [redirect]]))

(def skip-paths #{"/lending/graphiql" "/lending/sign-in"})
(def api-paths #{"/lending/graphql"})

(defn wrap [handler]
  (fn [{:keys [uri] :as request}]
    (cond
      (or (skip-paths uri) (:authenticated-entity request)) (handler request)
      (api-paths uri) {:status 401 :headers {"Content-Type" "text/plain"} :body "Unauthorized"}
      (not (str/starts-with? uri "/lending/")) (handler request)
      :else (redirect "/lending/sign-in"))))
