(ns leihs.lending.server.graphql.scalars
  (:require [clojure.string :as str])
  (:import [java.time LocalDate LocalDateTime]
           [java.util UUID]))

(defn- parse-non-empty-string [s]
  (if (str/blank? s)
    (throw (ex-info "must not be blank" {:status 422}))
    s))

(def scalars
  {:uuid-parse #(UUID/fromString %)
   :uuid-serialize str
   :non-empty-string-parse parse-non-empty-string
   :non-empty-string-serialize str
   :date-parse #(LocalDate/parse %)
   :date-serialize str
   :datetime-parse #(LocalDateTime/parse %)
   :datetime-serialize str})
