(ns leihs.lending.server.graphql.scalars
  (:require [clojure.string :as str])
  (:import [java.time Instant LocalDate LocalDateTime ZoneOffset]
           [java.util UUID]))

(defn- parse-non-empty-string [s]
  (if (str/blank? s)
    (throw (ex-info "must not be blank" {:status 422}))
    s))

(defn- parse-datetime
  "Counterpart of `serialize-datetime`: takes an ISO instant (`...Z`) and returns
   the UTC wall clock the timestamp columns store."
  [s]
  (LocalDateTime/ofInstant (Instant/parse s) ZoneOffset/UTC))

(defn- serialize-datetime
  "`timestamp without time zone` columns hold UTC (what Rails writes) and
   next.jdbc reads them back as LocalDateTime, so the zone has to be re-attached
   here -- an offset-less ISO string would be read as local time by clients."
  [^LocalDateTime date-time]
  (str (.toInstant date-time ZoneOffset/UTC)))

(def scalars
  {:uuid-parse #(UUID/fromString %)
   :uuid-serialize str
   :non-empty-string-parse parse-non-empty-string
   :non-empty-string-serialize str
   :date-parse #(LocalDate/parse %)
   :date-serialize str
   :datetime-parse parse-datetime
   :datetime-serialize serialize-datetime})
