(ns leihs.lending.client.routes.pools.visits.components.table.visit-row
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuItem DropdownMenuTrigger]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["@@/table" :refer [TableCell TableRow]]
   ["lucide-react" :refer [ChevronDown Mail UserX]]
   ["react-i18next" :refer [useTranslation]]
   ["sonner" :refer [toast]]
   [leihs.lending.client.lib.date-utils :refer [date-from-iso format-date duration-days]]
   [uix.core :as uix :refer [$ defui]]))

(defui VisitRow [{:keys [visit]}]
  (let [[t] (useTranslation)
        user (:user visit)
        name (str (:firstname user) " " (:lastname user))
        overdue? (:isOverdue visit)
        suspended? (:isSuspended user)
        reminders (count (:reminders visit))
        days (duration-days (:startDate visit) (:endDate visit))
        is-take-back? (= (:visitType visit) "TAKE_BACK")
        action-label (if is-take-back?
                       (t "visits.actions.take-back")
                       (t "visits.actions.hand-over"))
        on-action-trigger #(.. toast (message (t "visits.actions.not-available")))

        [user-pop-open? set-user-pop-open!] (uix/use-state false)
        [items-pop-open? set-items-pop-open!] (uix/use-state false)
        [reminders-pop-open? set-reminders-pop-open!] (uix/use-state false)]
    ($ TableRow {:className (str "border-l-4 "
                                 (if overdue? "border-l-destructive" "border-l-transparent"))}
       ($ TableCell
          ($ Popover {:open user-pop-open?
                      :on-open-change set-user-pop-open!}
             ($ PopoverTrigger {:data-test-id "visit-user-popover-trigger"}
                ($ :div {:className "flex items-center gap-3"}
                   ($ :span {:className "font-semibold"} name)
                   (when suspended?
                     ($ UserX {:className "size-4 text-destructive"}))))

             ($ PopoverContent {:align "start" :class-name "w-[400px]"}
                ($ :div {:class-name "font-semibold"} name)
                ($ :div (:email user))
                ($ :div "...TODO...")
                ($ :div "...TODO...")
                (when suspended?
                  ($ :div {:class-name "text-destructive"}
                     (or (:suspendedReason user)
                         (t "visits.user.suspended")))))))
       ($ TableCell
          (format-date t (date-from-iso (:date visit))))
       ($ TableCell {:className "text-center"}
          ($ Popover {:open items-pop-open?
                      :on-open-change set-items-pop-open!}
             ($ PopoverTrigger {:data-test-id "visit-items-popover-trigger"}
                ($ :span {:className "px-3"}
                   (:quantity visit)))
             ($ PopoverContent {:align "center" :class-name "w-[400px]"}
                ($ :div "...TODO..."))))
       ($ TableCell
          (t "visits.duration.days" #js {:count days}))
       ($ TableCell
          (if (pos? reminders)
            ($ Popover {:open reminders-pop-open?
                        :on-open-change set-reminders-pop-open!}
               ($ PopoverTrigger {:data-test-id "visit-reminders-popover-trigger"}
                  ($ :span {:className "inline-flex items-center gap-1.5"}
                     ($ Mail {:className "size-4 text-muted-foreground"})
                     (t "visits.reminders.some" #js {:count reminders})))
               ($ PopoverContent {:align "start" :class-name "w-[400px]"}
                  ($ :div "...TODO...")))
            ($ :span
               (t "visits.reminders.none"))))
       ($ TableCell
          ($ :div {:className "flex justify-end"}
             ($ :div {:className "inline-flex"}
                ($ Button {:variant "outline"
                           :className "rounded-r-none"
                           :onClick on-action-trigger}
                   action-label)
                ($ DropdownMenu
                   ($ DropdownMenuTrigger {:asChild true}
                      ($ Button {:variant "outline"
                                 :size "icon"
                                 :className "rounded-l-none border-l-0"}
                         ($ ChevronDown)))
                   ($ DropdownMenuContent {:align "end"}
                      (if is-take-back?
                        ($ DropdownMenuItem {:onClick on-action-trigger}
                           (t "visits.actions.send-reminder"))
                        ($ DropdownMenuItem {:onClick on-action-trigger}
                           (t "visits.actions.delete")))))))))))
