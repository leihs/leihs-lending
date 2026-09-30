(ns leihs.lending.client.routes.pools.visits.components.table.visit-row
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuItem DropdownMenuTrigger]]
   ["@@/table" :refer [TableCell TableRow]]
   ["lucide-react" :refer [ChevronDown]]
   ["react-i18next" :refer [useTranslation]]
   ["sonner" :refer [toast]]
   [leihs.lending.client.components.entities.items-popover :refer [ItemsPopover]]
   [leihs.lending.client.components.entities.user-popover :refer [UserPopover]]
   [leihs.lending.client.lib.date-utils :refer [date-from-iso format-date duration-days]]
   [leihs.lending.client.routes.pools.visits.components.table.reminders-popover :refer [RemindersPopover]]
   [uix.core :refer [$ defui]]))

(defui VisitRow [{:keys [visit use-user-details use-items]}]
  (let [[t] (useTranslation)
        user (:user visit)
        name (str (:firstname user) " " (:lastname user))
        overdue? (:isOverdue visit)
        reminder-list (:reminders visit)
        days (duration-days (:startDate visit) (:endDate visit))
        is-take-back? (= (:visitType visit) "TAKE_BACK")
        action-label (if is-take-back?
                       (t "visits.actions.take-back")
                       (t "visits.actions.hand-over"))
        on-action-trigger #(.. toast (message (t "visits.actions.not-available")))]
    ($ TableRow {:className (str "border-l-4 "
                                 (if overdue? "border-l-destructive" "border-l-transparent"))}
       ($ TableCell
          ($ UserPopover {:user user
                          :name name
                          :use-details use-user-details
                          :test-id "visit-user-popover-trigger"}))
       ($ TableCell
          (format-date t (date-from-iso (:date visit))))
       ($ TableCell {:className "text-center"}
          ($ ItemsPopover {:id (:id visit)
                           :quantity (:quantity visit)
                           :use-items use-items
                           :test-id "visit-items-popover-trigger"}))
       ($ TableCell
          (t "visits.duration.days" #js {:count days}))
       ($ TableCell
          ($ RemindersPopover {:id (:id visit)
                               :count (count reminder-list)}))
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
