(ns leihs.lending.authenticate
  (:require [clojure.string :as str]))

(def skip-paths #{"/lending/graphiql" "/lending/sign-in"})

(defn wrap [handler]
  (fn [{:keys [uri] :as request}]
    (if (or (skip-paths uri)
            (:authenticated-entity request))
      (handler request)
      {:status 401
       :headers {"Content-Type" "text/plain"}
       :body "Unauthorized"})))
