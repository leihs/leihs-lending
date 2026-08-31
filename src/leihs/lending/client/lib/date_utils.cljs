(ns leihs.lending.client.lib.date-utils
  (:require
   ["date-fns" :as date-fns]
   [clojure.string :refer [split]]))

(defn date-from-iso
  "Create a JS Date object with the given date at 0:00:00 in the client's time zone
   (avoiding time-zone-driven date shifting). Returns nil for nil input."
  [iso-date-string]
  (when iso-date-string
    (let [[y m d] (map js/parseInt
                       (split iso-date-string #"-"))
          date (js/Date. y (dec m) d)]
      date)))

(defn format-date
  "Format JS date object in a human readable way. Returns nil for nil input."
  [t d]
  (when d
    (let [date-string (t "common.date.formatDate" #js{:val d})]
      (cond
        (date-fns/isToday d) (str date-string " (" (t "common.date.today") ")")
        (date-fns/isYesterday d) (str date-string " (" (t "common.date.yesterday") ")")
        :else date-string))))

(defn duration-days
  "Inclusive day count between start and end date, or nil if unknown."
  [start-iso end-iso]
  (let [s (date-from-iso start-iso) e (date-from-iso end-iso)]
    (when (and s e) (inc (date-fns/differenceInCalendarDays e s)))))
