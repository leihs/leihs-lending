(ns leihs.lending.client.lib.calendar
  (:require
   ["date-fns" :as date-fns]
   [leihs.lending.client.lib.utils :refer [cj]]))

(defn day-key
  "Date as \"yyyy-MM-dd\", the form the backend's availability dates take."
  [date]
  (date-fns/format date "yyyy-MM-dd"))

(def weeks-shown
  "Weeks every grid has, so that it keeps the same height all year. Six is
   what the longest month needs; shorter ones run into the next month."
  6)

(defn grid-range
  "First and last day shown for the month — whole ISO weeks, always six of
   them."
  [month]
  (let [start (date-fns/startOfISOWeek (date-fns/startOfMonth month))]
    {:start start
     :end (date-fns/endOfISOWeek (date-fns/addWeeks start (dec weeks-shown)))}))

(defn grid-weeks
  "The month's grid as a sequence of 7-day weeks, Monday first."
  [month]
  (let [{:keys [start end]} (grid-range month)]
    (partition 7 (vec (date-fns/eachDayOfInterval (cj {:start start :end end}))))))

(defn index-by-date
  "Availability days keyed by their date string."
  [days]
  (into {} (map (juxt :date identity)) days))

(defn earlier [a b]
  (if (date-fns/isBefore a b) a b))

(defn later [a b]
  (if (date-fns/isBefore a b) b a))

(defn within?
  "Whether the date falls into the inclusive range, days only."
  [date start end]
  (boolean
   (and start end
        (not (date-fns/isBefore date (date-fns/startOfDay start)))
        (not (date-fns/isAfter date (date-fns/startOfDay end))))))

(defn weekday-names
  "Short weekday names of the locale, Monday first."
  [locale]
  (let [fmt (js/Intl.DateTimeFormat. locale #js {:weekday "short"})
        monday (date-fns/startOfISOWeek (js/Date.))]
    (mapv #(.format fmt (date-fns/addDays monday %)) (range 7))))

(defn month-names
  "Short month names of the locale, January first."
  [locale]
  (let [fmt (js/Intl.DateTimeFormat. locale #js {:month "short"})]
    (mapv #(.format fmt (js/Date. 2000 % 1)) (range 12))))
