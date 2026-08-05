(ns leihs.lending.server.graphql
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.walmartlabs.lacinia :as lacinia]
   [com.walmartlabs.lacinia.parser :as lacinia-parser]
   [com.walmartlabs.lacinia.schema :as graphql-schema]
   [com.walmartlabs.lacinia.tracing :as tracing]
   [com.walmartlabs.lacinia.util :as graphql-util]
   [leihs.lending.server.graphql.resolvers :as pool-resolvers]
   [leihs.lending.server.graphql.scalars :as scalars]
   [leihs.lending.server.root-graphql.resolvers :as root-resolvers]
   [taoensso.timbre :refer [info]]))

(def schemas* (atom {}))

(def enable-timing* (atom false))

(defn- attach-overall-timing [result]
  (let [resolver-timings (get-in result [:extensions :tracing :execution :resolvers])
        total-ms (Math/round (/ (apply + (map :duration resolver-timings)) 1e6))]
    (assoc-in result [:extensions :overall-timing :elapsed-in-ms] total-ms)))

(defn- load-schema [resource-file resolvers]
  (or (some-> (io/resource resource-file)
              slurp
              edn/read-string
              (graphql-util/attach-resolvers resolvers)
              (graphql-util/attach-scalar-transformers scalars/scalars)
              graphql-schema/compile)
      (throw (ex-info (str "Failed to load schema " resource-file) {}))))

(defn init [_options]
  (info "Initializing GraphQL schemas")
  (reset! schemas* {:pool (load-schema "schema.edn" pool-resolvers/resolvers)
                    :root (load-schema "root-schema.edn" root-resolvers/resolvers)}))

(defn mutation? [endpoint request]
  (boolean
   (when-let [query-str (-> request :body :query)]
     (try
       (-> (lacinia-parser/parse-query (get @schemas* endpoint) query-str)
           lacinia-parser/operations
           :type
           (= :mutation))
       (catch Throwable _ false)))))

(defn handler [endpoint request]
  (let [{:keys [query variables]} (:body request)
        schema (get @schemas* endpoint)
        pool-timing? (and (= endpoint :pool) @enable-timing*)
        result (-> (lacinia/execute schema
                                    query
                                    variables
                                    (cond-> {:request request}
                                      pool-timing? tracing/enable-tracing))
                   (cond-> pool-timing? attach-overall-timing))]
    {:status 200 :body result}))
