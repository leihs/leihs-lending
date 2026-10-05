(ns leihs.lending.client.routes.pools.orders.components.table.order-row
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuItem DropdownMenuTrigger]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["@@/table" :refer [TableCell TableRow]]
   ["@@/tooltip" :refer [Tooltip TooltipTrigger TooltipContent]]
   ["lucide-react" :refer [ChevronDown CircleCheck Circle CircleX]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   ["sonner" :refer [toast]]
   [clojure.string :refer [lower-case]]
   [leihs.lending.client.components.entities.items-popover :refer [ItemsPopover]]
   [leihs.lending.client.components.entities.user-popover :refer [UserPopover]]
   [leihs.lending.client.lib.date-utils :refer [date-time-from-iso format-date duration-days]]
   [leihs.lending.client.lib.urql :as urql]
   [leihs.lending.client.lib.utils :refer [jc]]
   [leihs.lending.client.routes.pools.orders.components.approve-failed-dialog :refer [ApproveFailedDialog]]
   [leihs.lending.client.routes.pools.orders.components.reject-dialog :refer [RejectDialog]]
   [leihs.lending.client.routes.pools.orders.data :as data]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui OrderRow [{:keys [order use-user-details use-items]}]
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        revalidator (router/useRevalidator)
        user (:user order)
        name (str (:firstname user) " " (:lastname user))
        state (:state order)
        reject-reason (:rejectReason order)
        days (duration-days (:startDate order) (:endDate order))
        quantity (count (:reservations order))
        to-be-verified (:toBeVerified order)
        on-action-trigger #(.. toast (message (t "orders.actions.not-available")))

        [purpose-pop-open? set-purpose-pop-open!] (uix/use-state false)
        [reject-open? set-reject-open!] (uix/use-state false)
        [approving? set-approving!] (uix/use-state false)
        [approve-failed-open? set-approve-failed-open!] (uix/use-state false)
        approve! (fn []
                   (set-approving! true)
                   (-> (data/approve-order! pool-id (:id order))
                       (p/then (fn [_]
                                 (.. toast (success (t "orders.approve-success")))
                                 (.revalidate revalidator)))
                       (p/catch (fn [error]
                                  (if (= 422 (urql/error-status error))
                                    (set-approve-failed-open! true)
                                    (.. toast (error (t "error.action.error"))))))
                       (p/finally (fn [_ _] (set-approving! false)))))]

    ($ TableRow {:class-name "border-l-4 border-l-transparent"}

       ;; Name
       ($ TableCell
          ($ UserPopover {:user user
                          :name name
                          :use-details use-user-details
                          :test-id "order-user-popover-trigger"}))

       ;; Datum (createdAt)
       ($ TableCell
          (format-date t (date-time-from-iso (:createdAt order))))

       ;; Items (quantity)
       ($ TableCell {:class-name "text-center"}
          ($ ItemsPopover {:id (:id order)
                           :quantity quantity
                           :use-items use-items
                           :test-id "order-items-popover-trigger"}))

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
                             :disabled approving?
                             :data-test-id "approve-order"
                             :onClick approve!}
                     (if to-be-verified
                       (t "orders.actions.verify-and-approve")
                       (t "orders.actions.approve")))
                  ($ DropdownMenu
                     ($ DropdownMenuTrigger {:asChild true}
                        ($ Button {:variant "outline"
                                   :size "icon"
                                   :data-test-id "order-actions-menu"
                                   :class-name "rounded-l-none border-l-0"}
                           ($ ChevronDown)))
                     ($ DropdownMenuContent {:align "end"}
                        ($ DropdownMenuItem {:onClick on-action-trigger}
                           (t "orders.actions.edit"))
                        ($ DropdownMenuItem {:onSelect #(set-reject-open! true)}
                           (t "orders.actions.reject"))))
                  ($ RejectDialog {:order order
                                   :open? reject-open?
                                   :set-open! set-reject-open!
                                   :use-items use-items})
                  ($ ApproveFailedDialog {:order order
                                          :open? approve-failed-open?
                                          :set-open! set-approve-failed-open!
                                          :use-items use-items}))

               (= state "APPROVED")
               ($ Button {:class-name "w-full"
                          :variant "outline"
                          :onClick on-action-trigger}
                  (t "orders.actions.hand-over"))))))))
