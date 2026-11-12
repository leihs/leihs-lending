(ns leihs.lending.client.lib.date-utils
  (:require
   ["date-fns" :as date-fns]
   [clojure.string :as str :refer [split trim]]))

(defn date-from-iso
  "Create a JS Date object with the given date at 0:00:00 in the client's time zone
   (avoiding time-zone-driven date shifting). Returns nil for nil input."
  [iso-date-string]
  (when iso-date-string
    (let [[y m d] (map js/parseInt
                       (split iso-date-string #"-"))
          date (js/Date. y (dec m) d)]
      date)))

(defn date-time-from-iso
  "Create a JS Date object from an ISO instant (`...Z`), i.e. that moment as seen
   in the client's time zone. Returns nil for nil input."
  [iso-date-time-string]
  (when iso-date-time-string
    (js/Date. iso-date-time-string)))

(defn relative-day-label
  "Translated \"today\" or \"yesterday\" for such a day, else nil."
  [t d]
  (when d
    (cond
      (date-fns/isToday d) (t "common.date.today")
      (date-fns/isYesterday d) (t "common.date.yesterday"))))

(defn format-date
  "Format JS date object in a human readable way. Returns nil for nil input."
  [t d]
  (when d
    (let [date-string (t "common.date.formatDate" #js{:val d})]
      (if-let [label (relative-day-label t d)]
        (str date-string " (" label ")")
        date-string))))

(defn- date-pattern
  "date-fns pattern of `common.date.formatDate`, derived from a sample date."
  [t]
  (-> (t "common.date.formatDate" #js{:val (js/Date. 2033 10 22)})
      (str/replace "2033" "yyyy")
      (str/replace "11" "MM")
      (str/replace "22" "dd")))

(defn parse-date
  "Parse user input in the format of `common.date.formatDate`, also with
   1-digit day/month, 2-digit year or without separators, or an ISO date.
   Returns nil when not a valid date."
  [t s]
  (let [pattern (date-pattern t)
        compact (str/replace pattern #"[^dMy]" "")
        short-year #(str/replace % "yyyy" "yy")
        text (trim (or s ""))]
    (some (fn [p]
            (let [d (date-fns/parse text p (js/Date.))]
              (when (and (date-fns/isValid d) (<= 1000 (date-fns/getYear d) 9999))
                d)))
          [(short-year pattern) pattern (short-year compact) compact "yyyy-MM-dd"])))

(defn format-weekday-date
  "Format JS date object as weekday plus date, e.g. \"Dienstag 02.12.2025\".
   Returns nil for nil input."
  [t d]
  (when d
    (str (t "common.date.formatWeekday" #js{:val d})
         " "
         (t "common.date.formatDate" #js{:val d}))))

(defn format-date-time
  "Format JS date object with date and time in a human readable way. Returns nil for nil input."
  [t d]
  (when d
    (t "common.date.formatDateTime" #js{:val d})))

(defn duration-days
  "Inclusive day count between start and end date, or nil if unknown."
  [start-iso end-iso]
  (let [s (date-from-iso start-iso) e (date-from-iso end-iso)]
    (when (and s e) (inc (date-fns/differenceInCalendarDays e s)))))
