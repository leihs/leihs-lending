(ns leihs.lending.graphql.resolvers
  (:require
   [leihs.core.graphql.helpers :refer [transform-resolvers
                                       wrap-resolver-with-error
                                       wrap-resolver-with-camelCase
                                       wrap-resolver-with-kebab-case]]
   [leihs.lending.graphql.mutations :as mutations]
   [leihs.lending.graphql.queries :as queries]))

(def resolvers
  (-> queries/resolvers
      (merge mutations/resolvers)
      (transform-resolvers (comp wrap-resolver-with-error
                                 wrap-resolver-with-camelCase
                                 wrap-resolver-with-kebab-case))))
