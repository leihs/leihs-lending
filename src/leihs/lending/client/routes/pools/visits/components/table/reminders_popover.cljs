(ns leihs.lending.client.routes.pools.visits.components.table.reminders-popover
  (:require
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["@@/scroll-area" :refer [ScrollArea]]
   ["lucide-react" :refer [Mail]]
   ["react-i18next" :refer [useTranslation]]
   [leihs.lending.client.lib.date-utils :refer [date-time-from-iso format-date-time]]
   [leihs.lending.client.routes.pools.visits.data :as data]
   [uix.core :as uix :refer [$ defui]]))

(defui RemindersPopover
  "A visit's reminders, loaded when the popover is opened -- the trigger only
   needs their `count`, which the list query carries. Opens once the data is
   ready, so it does not resize under the cursor. A failed query shows a
   message rather than an empty list."
  [{:keys [id count]}]
  (let [[t] (useTranslation)
        [requested? set-requested!] (uix/use-state false)
        {reminders :data ready? :ready? error? :error?} (data/use-reminders id requested?)]
    (if (pos? count)
      ($ Popover {:open (and requested? ready?)
                  :on-open-change set-requested!}
         ($ PopoverTrigger {:data-test-id "visit-reminders-popover-trigger"}
            ($ :span {:className "inline-flex items-center gap-1.5"}
               ($ Mail {:className "size-4 text-muted-foreground"})
               (t "visits.reminders.some" #js {:count count})))
         ($ PopoverContent {:align "start" :class-name "w-[500px]"}
            ($ :div {:class-name "font-semibold text-base mb-4"} (t "visits.reminders.title"))
            (if error?
              ($ :div {:class-name "text-sm text-destructive"}
                 (t "common.load-error"))
              ($ ScrollArea {:class-name "max-h-72 overflow-y-auto w-full rounded-md border p-2"}
                 (for [reminder reminders]
                   ($ :div {:key (:id reminder) :className "flex gap-5 text-sm mb-2"}
                      ($ :span {:className "shrink-0 tabular-nums"}
                         (format-date-time t (date-time-from-iso (:createdAt reminder))))
                      ($ :span (:subject reminder))))))))
      ($ :span
         (t "visits.reminders.none")))))
