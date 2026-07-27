(ns leihs.lending.client.routes.pools.orders.components.table.order-row
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuItem DropdownMenuTrigger]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["@@/table" :refer [TableCell TableRow]]
   ["@@/tooltip" :refer [Tooltip TooltipTrigger TooltipContent]]
   ["lucide-react" :refer [ChevronDown CircleCheck Circle CircleX UserX]]
   ["react-i18next" :refer [useTranslation]]
   ["sonner" :refer [toast]]
   [clojure.string :refer [lower-case]]
   [leihs.lending.client.lib.date-utils :refer [format-date duration-days]]
   [uix.core :as uix :refer [$ defui]]))

(defui OrderRow [{:keys [order]}]
  (let [[t] (useTranslation)
        user (:user order)
        name (str (:firstname user) " " (:lastname user))
        suspended? (:isSuspended user)
        state (:state order)
        reject-reason (:rejectReason order)
        days (duration-days (:startDate order) (:endDate order))
        quantity (count (:reservations order))
        to-be-verified (:toBeVerified order)
        on-action-trigger #(.. toast (message (t "orders.actions.not-available")))

        [user-pop-open? set-user-pop-open!] (uix/use-state false)
        [items-pop-open? set-items-pop-open!] (uix/use-state false)
        [purpose-pop-open? set-purpose-pop-open!] (uix/use-state false)]

    ($ TableRow {:class-name "border-l-4 border-l-transparent"}

       ;; Name
       ($ TableCell
          ($ Popover {:open user-pop-open?
                      :on-open-change set-user-pop-open!}
             ($ PopoverTrigger {:data-test-id "order-user-popover-trigger"}
                ($ :div {:class-name "flex items-center gap-3"}
                   ($ :span {:class-name "font-semibold"} name)
                   (when suspended?
                     ($ UserX {:class-name "size-4 text-destructive"}))))
             ($ PopoverContent {:align "start" :class-name "w-[400px]"}
                ($ :div {:class-name "font-semibold"} name)
                ($ :div (:email user))
                ($ :div "...TODO...")
                ($ :div "...TODO...")
                (when suspended?
                  ($ :div {:class-name "text-destructive"}
                     (or (:suspendedReason user)
                         (t "orders.user.suspended")))))))

       ;; Datum (createdAt)
       ($ TableCell
          (format-date t (:createdAt order)))

       ;; Items (quantity)
       ($ TableCell {:class-name "text-center"}
          ($ Popover {:open items-pop-open?
                      :on-open-change set-items-pop-open!}
             ($ PopoverTrigger {:data-test-id "order-items-popover-trigger"}
                ($ :span {:class-name "px-3"}
                   quantity))
             ($ PopoverContent {:align "center" :class-name "w-[400px]"}
                ($ :div "...TODO..."))))

       ;; Duration
       ($ TableCell
          (if days
            (t "orders.duration.days" #js {:count days})
            "—"))

       ;; Purpose
       ($ TableCell
          (let [purpose (:purpose order)
                max-length 30]
            (cond
              (> (count purpose) max-length)
              ($ Popover {:open purpose-pop-open?
                          :on-open-change set-purpose-pop-open!}
                 ($ PopoverTrigger {:asChild true}
                    ($ :span {:class-name "cursor-pointer"}
                       (str (subs purpose 0 max-length) "…")))
                 ($ PopoverContent {:align "start" :class-name "w-[400px]"}
                    ($ :p {:class-name "whitespace-pre-wrap text-sm"}
                       purpose)))

              purpose
              ($ :span purpose)

              :else
              "—")))

       ;; Status
       ($ TableCell
          (let [status-class (cond
                               (= state "SUBMITTED") "text-blue-600"
                               (= state "APPROVED")  "text-green-600"
                               (= state "REJECTED")  "text-destructive")]
            ($ :div {:class-name (str "flex justify-center " status-class)}
               ($ Tooltip
                  ($ TooltipTrigger {:as-child true
                                     :data-test-id "order-status-tooltip-trigger"}
                     (cond
                       (= state "SUBMITTED") ($ Circle {:class-name "size-5"})
                       (= state "APPROVED")  ($ CircleCheck {:class-name "size-5"})
                       (= state "REJECTED")  ($ CircleX {:class-name "size-5"})))
                  ($ TooltipContent
                     (t (str "orders.state." (lower-case state)))
                     (when (and  (= state "REJECTED") reject-reason)
                       ($ :div {:class-name "whitespace-pre-wrap mt-2"}
                          (t "orders.table.reject-reason") ": " reject-reason)))))))

       ;; Actions / reject reason 
       ($ TableCell
          ($ :div {:class-name "min-h-9"}
             (cond
               (= state "SUBMITTED")
               ($ :div {:class-name "w-full flex items-stretch"}
                  ($ Button {:variant "outline"
                             :class-name "flex-1 rounded-r-none"
                             :onClick on-action-trigger}
                     (if to-be-verified
                       (t "orders.actions.verify-and-approve")
                       (t "orders.actions.approve")))
                  ($ DropdownMenu
                     ($ DropdownMenuTrigger {:asChild true}
                        ($ Button {:variant "outline"
                                   :size "icon"
                                   :class-name "rounded-l-none border-l-0"}
                           ($ ChevronDown)))
                     ($ DropdownMenuContent {:align "end"}
                        ($ DropdownMenuItem {:onClick on-action-trigger}
                           (t "orders.actions.edit"))
                        ($ DropdownMenuItem {:onClick on-action-trigger}
                           (t "orders.actions.reject")))))

               (= state "APPROVED")
               ($ Button {:class-name "w-full"
                          :variant "outline"
                          :onClick on-action-trigger}
                  (t "orders.actions.hand-over"))))))))
