(ns leihs.lending.server.graphql.resolvers
  (:require
   [leihs.core.graphql.helpers :refer [transform-resolvers
                                       wrap-resolver-with-error
                                       wrap-resolver-with-camelCase
                                       wrap-resolver-with-kebab-case]]
   [leihs.lending.server.graphql.mutations :as mutations]
   [leihs.lending.server.graphql.queries :as queries]))

(def resolvers
  (-> queries/resolvers
      (merge mutations/resolvers)
      (transform-resolvers (comp wrap-resolver-with-error
                                 wrap-resolver-with-camelCase
                                 wrap-resolver-with-kebab-case))))
