(ns leihs.lending.server.root-graphql
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.walmartlabs.lacinia :as lacinia]
   [com.walmartlabs.lacinia.schema :as graphql-schema]
   [com.walmartlabs.lacinia.util :as graphql-util]
   [leihs.lending.server.graphql.scalars :as scalars]
   [leihs.lending.server.root-graphql.resolvers :as resolvers]
   [taoensso.timbre :refer [info]]))

(def schema* (atom nil))

(defn- load-schema []
  (or (some-> (io/resource "root-schema.edn")
              slurp
              edn/read-string
              (graphql-util/attach-resolvers resolvers/resolvers)
              (graphql-util/attach-scalar-transformers scalars/scalars)
              graphql-schema/compile)
      (throw (ex-info "Failed to load root schema" {}))))

(defn init [_options]
  (info "Initializing root GraphQL schema")
  (reset! schema* (load-schema)))

(defn handler [request]
  (let [{:keys [query variables]} (:body request)
        result (lacinia/execute @schema* query variables {:request request})]
    {:status 200 :body result}))
