(ns leihs.lending.client.routes.pools.orders.edit.components.add-reservation-form
  (:require
   ["@@/button" :refer [Button]]
   ["@@/calendar" :refer [Calendar]]
   ["@@/input-group" :refer [InputGroup InputGroupAddon InputGroupInput]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["date-fns" :as date-fns]
   ["lucide-react" :refer [CalendarDays List Search]]
   ["react-i18next" :refer [useTranslation]]
   ["sonner" :refer [toast]]
   [leihs.lending.client.lib.date-utils :refer [format-date]]
   [uix.core :as uix :refer [$ defui]]))

(defui DateField
  "Date picker of the — as yet unwired — form adding a reservation."
  [{:keys [value set-value! label test-id]}]
  (let [[t] (useTranslation)
        [open? set-open!] (uix/use-state false)
        select! (fn [date]
                  (set-open! false)
                  (when date (set-value! date)))]
    ($ Popover {:open open? :on-open-change set-open!}
       ($ PopoverTrigger {:asChild true}
          ($ Button {:variant "outline"
                     :class-name "min-w-44 justify-start font-normal"
                     :aria-label label
                     :data-test-id test-id}
             ($ CalendarDays {:class-name "size-4"})
             ($ :span {:class-name "truncate"} (format-date t value))))
       ($ PopoverContent {:class-name "w-[280px]"}
          ($ Calendar {:mode "single"
                       :captionLayout "dropdown"
                       :selected value
                       :defaultMonth value
                       :endMonth (date-fns/addYears (js/Date.) 50)
                       :onSelect select!})))))

(defui AddReservationForm
  "Form for adding a reservation — unwired so far."
  []
  (let [[t] (useTranslation)
        [start-date set-start-date!] (uix/use-state (js/Date.))
        [end-date set-end-date!] (uix/use-state (js/Date.))
        [term set-term!] (uix/use-state "")
        stub! #(.. toast (message (t "orders.actions.not-available")))]

    ($ :div {:class-name "flex flex-wrap items-center gap-2"}
       ($ DateField {:value start-date
                     :set-value! set-start-date!
                     :label (t "orders.edit.start-date")
                     :test-id "new-reservation-start-date"})
       ($ DateField {:value end-date
                     :set-value! set-end-date!
                     :label (t "orders.edit.end-date")
                     :test-id "new-reservation-end-date"})
       ($ InputGroup {:class-name "w-[300px]"}
          ($ InputGroupAddon ($ Search))
          ($ InputGroupInput {:value term
                              :data-test-id "new-reservation-term"
                              :placeholder (t "orders.edit.search-placeholder")
                              :on-change #(set-term! (.. % -target -value))}))
       ($ Button {:variant "outline"
                  :data-test-id "add-via-catalog"
                  :onClick stub!}
          ($ List)
          (t "orders.edit.add-via-catalog")))))
