(ns leihs.lending.server.root-graphql.resolvers
  (:require
   [leihs.core.graphql.helpers :refer [transform-resolvers
                                       wrap-resolver-with-error
                                       wrap-resolver-with-camelCase
                                       wrap-resolver-with-kebab-case]]
   [leihs.lending.server.root-graphql.queries :as queries]))

(def resolvers
  (-> queries/resolvers
      (transform-resolvers (comp wrap-resolver-with-error
                                 wrap-resolver-with-camelCase
                                 wrap-resolver-with-kebab-case))))
