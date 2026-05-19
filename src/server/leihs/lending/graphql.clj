(ns leihs.lending.graphql
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.walmartlabs.lacinia :as lacinia]
   [com.walmartlabs.lacinia.schema :as graphql-schema]
   [com.walmartlabs.lacinia.util :as graphql-util]
   [leihs.core.graphql :as core-graphql]
   [taoensso.timbre :refer [info]]))

(def resolvers
  {:query/hello (fn [_ctx _args _val] "Hello from lending!")})

(defn load-schema []
  (or (some-> (io/resource "schema.edn")
              slurp
              edn/read-string
              (graphql-util/attach-resolvers resolvers)
              graphql-schema/compile)
      (throw (ex-info "Failed to load schema" {}))))

(defn init [_options]
  (info "Initializing GraphQL schema")
  (core-graphql/init-schema! (load-schema)))

(defn handler [request]
  (let [{:keys [query variables]} (:body request)
        result (lacinia/execute (core-graphql/schema) query variables {:request request})]
    {:status 200 :body result}))
