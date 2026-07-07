(ns leihs.lending.client.lib.date-utils
  (:require
   ["date-fns" :as date-fns]))

(defn format-date [t s]
  (when-let [d (date-fns/parseISO s)]
    (let [date-string (t "common.date.formatDate" #js{:val d})]
      (cond
        (date-fns/isToday d) (str date-string " (" (t "common.date.today") ")")
        (date-fns/isYesterday d) (str date-string " (" (t "common.date.yesterday") ")")
        :else date-string))))

(defn duration-days
  "Inclusive day count between start and end date, or nil if unknown."
  [start end]
  (let [s (date-fns/parseISO start) e (date-fns/parseISO end)]
    (when (and s e) (inc (date-fns/differenceInCalendarDays e s)))))