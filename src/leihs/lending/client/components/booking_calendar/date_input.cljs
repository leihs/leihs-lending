(ns leihs.lending.client.components.booking-calendar.date-input
  (:require
   ["@@/calendar" :refer [Calendar]]
   ["@@/input-group" :refer [InputGroup InputGroupAddon InputGroupButton
                             InputGroupInput]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["date-fns" :as date-fns]
   ["lucide-react" :refer [CalendarDays]]
   ["react-i18next" :refer [useTranslation]]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :as uix :refer [$ defui]]))

(defui DateInput
  "Date field to pick a date from a calendar. Days before `min-date` cannot be
   picked."
  [{:keys [date on-change min-date label test-id]}]
  (let [[t] (useTranslation)
        [open? set-open!] (uix/use-state false)
        min-day (some-> min-date date-fns/startOfDay)]
    ($ InputGroup {:class-name "w-40"}
       ($ InputGroupInput {:read-only true
                           :aria-label label
                           :data-test-id test-id
                           :value (if date (t "common.date.formatDate" (cj {:val date})) "")})
       ($ InputGroupAddon {:align "inline-end"}
          ($ Popover {:open open? :on-open-change set-open!}
             ($ PopoverTrigger {:as-child true}
                ($ InputGroupButton {:size "icon-xs"
                                     :aria-label (t "calendar.pick-date")
                                     :data-test-id (str test-id "-picker")}
                   ($ CalendarDays)))
             ($ PopoverContent {:class-name "w-auto p-0" :align "end"}
                ($ Calendar {:mode "single"
                             :captionLayout "dropdown"
                             :selected date
                             :defaultMonth date
                             :startMonth min-day
                             :endMonth (date-fns/addYears (js/Date.) 50)
                             :disabled (when min-day #js {:before min-day})
                             :onSelect (fn [d]
                                         (set-open! false)
                                         (when d (on-change d)))})))))))
