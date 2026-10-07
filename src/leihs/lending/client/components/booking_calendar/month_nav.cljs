(ns leihs.lending.client.components.booking-calendar.month-nav
  (:require
   ["@@/button" :refer [Button]]
   ["@@/select" :refer [Select SelectContent SelectItem SelectTrigger
                        SelectValue]]
   ["date-fns" :as date-fns]
   ["lucide-react" :refer [ChevronLeft ChevronRight]]
   ["react-i18next" :refer [useTranslation]]
   [leihs.lending.client.lib.calendar :as cal]
   [uix.core :refer [$ defui]]))

(def ^:private years-ahead 5)

(defui MonthNav
  "Steps through the months and jumps to a month and year. Does not go back
   beyond the month of `min-date`."
  [{:keys [month set-month! min-date]}]
  (let [[t i18n] (useTranslation)
        months (cal/month-names (.-language i18n))
        year (.getFullYear month)
        first-year (min year (if min-date (.getFullYear min-date) year))
        go! (fn [date] (set-month! (date-fns/startOfMonth date)))
        at-min? (boolean (and min-date
                              (not (date-fns/isAfter month
                                                     (date-fns/startOfMonth min-date)))))]

    ($ :div {:class-name "flex items-center gap-1"}
       ($ Button {:variant "ghost"
                  :size "icon"
                  :disabled at-min?
                  :aria-label (t "calendar.previous-month")
                  :data-test-id "calendar-previous-month"
                  :onClick #(go! (date-fns/addMonths month -1))}
          ($ ChevronLeft))

       ($ Select {:value (str (.getMonth month))
                  :onValueChange #(go! (date-fns/setMonth month (js/parseInt %)))}
          ($ SelectTrigger {:class-name "w-24"
                            :aria-label (t "calendar.month")
                            :data-test-id "calendar-month"}
             ($ SelectValue))
          ($ SelectContent
             (map-indexed (fn [index name]
                            ($ SelectItem {:key index :value (str index)} name))
                          months)))

       ($ Select {:value (str year)
                  :onValueChange #(go! (date-fns/setYear month (js/parseInt %)))}
          ($ SelectTrigger {:class-name "w-24"
                            :aria-label (t "calendar.year")
                            :data-test-id "calendar-year"}
             ($ SelectValue))
          ($ SelectContent
             (for [option (range first-year (+ (max year first-year) years-ahead 1))]
               ($ SelectItem {:key option :value (str option)} option))))

       ($ Button {:variant "ghost"
                  :size "icon"
                  :aria-label (t "calendar.next-month")
                  :data-test-id "calendar-next-month"
                  :onClick #(go! (date-fns/addMonths month 1))}
          ($ ChevronRight)))))
