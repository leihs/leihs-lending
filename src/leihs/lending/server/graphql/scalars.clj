(ns leihs.lending.server.graphql.scalars
  (:require [clojure.string :as str])
  (:import [java.util UUID]))

(defn- parse-non-empty-string [s]
  (if (str/blank? s)
    (throw (ex-info "must not be blank" {}))
    s))

(def scalars
  {:uuid-parse #(UUID/fromString %)
   :uuid-serialize str
   :non-empty-string-parse parse-non-empty-string
   :non-empty-string-serialize str})
