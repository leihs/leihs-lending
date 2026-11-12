(ns leihs.lending.client.components.date-input
  (:require
   ["@@/calendar" :refer [Calendar]]
   ["@@/input-group" :refer [InputGroup InputGroupAddon InputGroupButton
                             InputGroupInput]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["date-fns" :as date-fns]
   ["lucide-react" :refer [CalendarDays]]
   ["react-i18next" :refer [useTranslation]]
   [clojure.string :refer [blank?]]
   [leihs.lending.client.lib.date-utils :refer [parse-date relative-day-label]]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :as uix :refer [$ defui]]))

(defui DateInput
  "Date field to type, paste or pick a date from a calendar. Typed input is
   applied on Enter or blur; invalid input is flagged on Enter and reverted on
   blur or Escape. Days before `min-date` are rejected. With `clearable?`,
   emptying the field or unselecting the day calls `on-change` with nil."
  [{:keys [date on-change min-date clearable? label placeholder test-id class-name]}]
  (let [[t] (useTranslation)
        format-text #(if % (t "common.date.formatDate" (cj {:val %})) "")
        date-text (format-text date)
        [text set-text!] (uix/use-state date-text)
        [focused? set-focused!] (uix/use-state false)
        [invalid? set-invalid!] (uix/use-state false)
        [open? set-open!] (uix/use-state false)
        min-day (some-> min-date date-fns/startOfDay)
        allowed? #(not (and min-day (date-fns/isBefore % min-day)))
        shown-day-label (when-not focused? (relative-day-label t date))
        revert! (fn []
                  (set-text! date-text)
                  (set-invalid! false))
        change! (fn [d]
                  (set-text! (format-text d))
                  (set-invalid! false)
                  (when-not (if (and d date) (date-fns/isSameDay d date) (= d date))
                    (on-change d)))
        apply-text! (fn []
                      (let [d (parse-date t text)]
                        (cond
                          (and (blank? text) clearable?) (do (change! nil) true)
                          (and d (allowed? d)) (do (change! d) true)
                          :else false)))]

    (uix/use-effect #(set-text! date-text) [date-text])

    ($ InputGroup {:class-name (or class-name "w-42")}
       ;; input only as wide as its text, so the day label sits right after the date
       ($ InputGroupInput {:class-name (when shown-day-label "w-auto flex-initial field-sizing-content")
                           :aria-label label
                           :aria-invalid invalid?
                           :placeholder placeholder
                           :data-test-id test-id
                           :value text
                           :on-change (fn [e]
                                        (set-text! (.. e -target -value))
                                        (set-invalid! false))
                           :on-focus #(set-focused! true)
                           :on-blur (fn [_]
                                      (set-focused! false)
                                      (when-not (apply-text!) (revert!)))
                           :on-key-down (fn [e]
                                          (case (.-key e)
                                            "Enter" (do (.preventDefault e)
                                                        (when-not (apply-text!)
                                                          (set-invalid! true)))
                                            "Escape" (revert!)
                                            nil))})
       (when shown-day-label
         ($ InputGroupAddon {:align "inline-end"
                             :class-name "flex-1 justify-start pr-0 pl-0 font-normal"
                             :data-test-id (str test-id "-relative")}
            shown-day-label))
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
                             :data-test-id (str test-id "-calendar")
                             :selected date
                             :defaultMonth date
                             :startMonth min-day
                             :endMonth (date-fns/addYears (js/Date.) 50)
                             :disabled (when min-day #js {:before min-day})
                             :onSelect (fn [d]
                                         (set-open! false)
                                         (cond
                                           (and d (allowed? d)) (change! d)
                                           (and (nil? d) clearable?) (change! nil)))})))))))
