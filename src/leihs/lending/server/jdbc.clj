(ns leihs.lending.server.jdbc
  (:require [next.jdbc.date-time :as jdbc-date-time]))

(defn configure []
  ;; shared-clj's leihs.core.db only *requires* next.jdbc.date-time, which by
  ;; itself just extends SettableParameter (writes) -- it does NOT install the
  ;; ReadableColumn extension needed for date/timestamp columns to come back
  ;; as java.time values instead of raw java.sql.Date/Timestamp.
  (jdbc-date-time/read-as-local))
