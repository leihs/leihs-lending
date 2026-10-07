(ns leihs.lending.client.routes.pools.orders.edit.page
  (:require
   ["@@/alert" :refer [Alert AlertDescription]]
   ["@@/badge" :refer [Badge]]
   ["@@/button" :refer [Button]]
   ["@@/card" :refer [Card CardContent CardHeader]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuItem DropdownMenuTrigger]]
   ["lucide-react" :refer [ArrowRightLeft ChevronDown SquarePen TriangleAlert UserX]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   ["sonner" :refer [toast]]
   [clojure.string :refer [blank?]]
   [leihs.lending.client.lib.urql :as urql]
   [leihs.lending.client.lib.utils :refer [jc]]
   [leihs.lending.client.routes.pools.orders.components.approve-dialog :refer [ApproveDialog]]
   [leihs.lending.client.routes.pools.orders.components.reject-dialog :refer [RejectDialog]]
   [leihs.lending.client.routes.pools.orders.components.styles :refer [approve-class]]
   [leihs.lending.client.routes.pools.orders.data :as data]
   [leihs.lending.client.routes.pools.orders.edit.components.add-reservation-form :refer [AddReservationForm]]
   [leihs.lending.client.routes.pools.orders.edit.components.calendar-dialog :refer [CalendarDialog]]
   [leihs.lending.client.routes.pools.orders.edit.components.purpose-dialog :refer [PurposeDialog]]
   [leihs.lending.client.routes.pools.orders.edit.components.reservation-lines :refer [ReservationLines line-key]]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        navigate (router/useNavigate)
        {:keys [order]} (router/useLoaderData)
        user (:user order)
        name (str (:firstname user) " " (:lastname user))
        purpose (:purpose order)
        submitted? (= (:state order) "SUBMITTED")
        stub! #(.. toast (message (t "orders.actions.not-available")))
        back! #(navigate (str "/lending/" pool-id "/orders"))

        [purpose-open? set-purpose-open!] (uix/use-state false)
        [reject-open? set-reject-open!] (uix/use-state false)
        [approve-open? set-approve-open!] (uix/use-state false)
        [approve-failed? set-approve-failed!] (uix/use-state false)
        [approving? set-approving!] (uix/use-state false)
        approve! (fn []
                   (set-approving! true)
                   (-> (data/approve-order! pool-id (:id order))
                       (p/then (fn [_]
                                 (.. toast (success (t "orders.approve-success")))
                                 (back!)))
                       (p/catch (fn [error]
                                  (if (= 422 (urql/error-status error))
                                    (do (set-approve-failed! true)
                                        (set-approve-open! true))
                                    (.. toast (error (t "error.action.error"))))))
                       (p/finally (fn [_ _] (set-approving! false)))))
        approve-with-comment! (fn []
                                (set-approve-failed! false)
                                (set-approve-open! true))

        lines (:reservationLines order)
        [selected set-selected!] (uix/use-state #{})
        toggle! (fn [line-key on?]
                  (set-selected! #((if on? conj disj) % line-key)))
        toggle-many! (fn [line-keys on?]
                       (set-selected! #(apply (if on? conj disj) % line-keys)))
        [calendar-lines set-calendar-lines!] (uix/use-state nil)
        open-calendar! (fn [lines] (set-calendar-lines! (vec lines)))
        revalidator (router/useRevalidator)
        saved! (fn []
                 ;; saved lines come back with new reservation ids
                 (set-selected! #{})
                 (.revalidate revalidator))
        selected-lines (filterv #(contains? selected (line-key %)) lines)]

    ($ Card
       ($ CardHeader

          (when-not submitted?
            ($ Alert {:variant "destructive"}
               ($ TriangleAlert)
               ($ AlertDescription
                  "Order is not in SUBMITTED state, this feature will not work. To be implemented")))

          ($ :div {:class-name "flex flex-wrap justify-between gap-4"}
             ($ :div
                ($ :div {:class-name "flex items-center gap-3"}
                   ($ :h1 {:class-name "text-2xl"}
                      (t "orders.edit.title")
                      " "
                      ($ :span {:class-name "font-bold"} name))
                   (when (:isSuspended user)
                     ($ UserX {:class-name "size-5 text-destructive"}))
                   ($ Button {:variant "outline"
                              :size "icon-sm"
                              :aria-label (t "orders.edit.swap-user")
                              :data-test-id "swap-order-user"
                              :onClick stub!}
                      ($ ArrowRightLeft)))
                ($ :div {:class-name "flex items-start gap-3 mt-2"}
                   ($ :span {:class-name (str "mt-1 whitespace-pre-wrap"
                                              (when (blank? purpose) " text-muted-foreground"))}
                      (if (blank? purpose)
                        (t "orders.edit.purpose-placeholder")
                        purpose))
                   ($ Button {:variant "outline"
                              :size "icon-sm"
                              :aria-label (t "orders.edit.edit-purpose")
                              :data-test-id "edit-order-purpose"
                              :onClick #(set-purpose-open! true)}
                      ($ SquarePen))))

             ($ :div {:class-name "flex items-stretch gap-2"}
                ($ Button {:variant "destructive"
                           :data-test-id "reject-order"
                           :onClick #(set-reject-open! true)}
                   (t "orders.edit.reject"))
                ($ :div {:class-name "flex items-stretch"}
                   ($ Button {:class-name (str approve-class " rounded-r-none")
                              :disabled approving?
                              :data-test-id "approve-order"
                              :onClick approve!}
                      (t "orders.edit.approve"))
                   ($ DropdownMenu
                      ($ DropdownMenuTrigger {:asChild true}
                         ($ Button {:size "icon"
                                    :class-name (str approve-class " rounded-l-none border-l border-l-lime-600")
                                    :data-test-id "approve-order-menu"}
                            ($ ChevronDown)))
                      ($ DropdownMenuContent {:align "end"}
                         ($ DropdownMenuItem {:onSelect approve-with-comment!
                                              :data-test-id "approve-order-with-comment"}
                            (t "orders.edit.approve-with-comment")))))))

          ($ :hr {:class-name "border-border mt-3"}))

       ($ CardContent
          ($ :div {:class-name "grid gap-8"}
             ($ :div {:class-name "flex flex-wrap items-center justify-between gap-2"}

                ($ AddReservationForm)

                ($ :div {:class-name "flex items-stretch"}
                   ($ Button {:class-name "rounded-r-none"
                              :disabled (empty? selected)
                              :data-test-id "edit-selection"
                              :onClick #(open-calendar! selected-lines)}
                      (t "orders.edit.edit-selection")
                      ($ Badge {:variant "secondary"} (count selected)))
                   ($ DropdownMenu
                      ($ DropdownMenuTrigger {:asChild true}
                         ($ Button {:size "icon"
                                    :class-name "rounded-l-none border-l border-l-background/25"
                                    :disabled (empty? selected)
                                    :data-test-id "selection-actions-menu"}
                            ($ ChevronDown)))
                      ($ DropdownMenuContent {:align "end"}
                         ($ DropdownMenuItem {:onSelect stub!}
                            (t "orders.edit.print-selection"))
                         ($ DropdownMenuItem {:onSelect stub!}
                            (t "orders.edit.delete-selection"))))))

             ($ ReservationLines {:lines lines
                                  :selected selected
                                  :toggle! toggle!
                                  :toggle-many! toggle-many!
                                  :on-edit-line #(open-calendar! [%])})))

       ($ PurposeDialog {:order order
                         :open? purpose-open?
                         :set-open! set-purpose-open!})
       ($ RejectDialog {:order order
                        :open? reject-open?
                        :set-open! set-reject-open!
                        :use-items data/use-items
                        :on-success back!})
       ($ ApproveDialog {:order order
                         :open? approve-open?
                         :set-open! set-approve-open!
                         :use-items data/use-items
                         :failed? approve-failed?
                         :on-success back!})
       ($ CalendarDialog {:order order
                          :lines (or calendar-lines [])
                          :open? (boolean (seq calendar-lines))
                          :set-open! #(when-not % (set-calendar-lines! nil))
                          :on-saved saved!}))))
