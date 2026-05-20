(ns leihs.lending.server.graphql.scalars
  (:import [java.util UUID]))

(def scalars
  {:uuid-parse    #(UUID/fromString %)
   :uuid-serialize str})
