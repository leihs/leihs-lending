(ns leihs.lending.client.routes.pools.orders.edit.components.calendar-dialog
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dialog" :refer [Dialog DialogClose DialogContent DialogDescription
                        DialogFooter DialogHeader DialogTitle]]
   ["@@/input" :refer [Input]]
   ["@@/input-group" :refer [InputGroup InputGroupAddon InputGroupInput]]
   ["@@/select" :refer [Select SelectContent SelectItem SelectTrigger
                        SelectValue]]
   ["@@/spinner" :refer [Spinner]]
   ["date-fns" :as date-fns]
   ["lucide-react" :refer [CalendarDays]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   ["sonner" :refer [toast]]
   [leihs.lending.client.components.booking-calendar.grid :refer [BookingCalendar]]
   [leihs.lending.client.components.booking-calendar.month-nav :refer [MonthNav]]
   [leihs.lending.client.lib.calendar :as cal]
   [leihs.lending.client.lib.date-utils :refer [date-from-iso]]
   [leihs.lending.client.lib.utils :refer [cj jc]]
   [leihs.lending.client.routes.pools.orders.edit.data :as data]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui DateField
  "Shows one end of the picked range; the range itself is picked in the grid."
  [{:keys [t date label test-id]}]
  ($ InputGroup {:class-name "w-36"}
     ($ InputGroupAddon ($ CalendarDays))
     ($ InputGroupInput {:read-only true
                         :aria-label label
                         :data-test-id test-id
                         :value (if date (t "common.date.formatDate" (cj {:val date})) "")})))

(defui LineSummary
  "One of the edited lines, with the quantity it asks for over the quantity
   available to it today."
  [{:keys [line]}]
  (let [quantity (:quantity line)
        available (:availableQuantity line)
        met? (>= available quantity)]
    ($ :div {:class-name "flex items-center gap-3 border-b px-3 py-2 last:border-b-0"
             :data-test-id "calendar-line"}
       ($ :span {:class-name (str "h-6 w-1 shrink-0 rounded-full "
                                  (if met? "bg-lime-500" "bg-destructive"))})
       ($ :span {:class-name "w-16 shrink-0 tabular-nums"}
          (str quantity " | " available))
       ($ :span {:class-name "font-semibold"} (get-in line [:model :name])))))

(defui CalendarDialog
  "Changes the date range — and, for a single model, the quantity — of the
   order's selected reservation lines. The calendar shows the availability the
   change would meet, with the edited reservations taken out of it. A single
   line is shown with quantities per day, several with a verdict per day.
   `on-saved` is called once the change is saved."
  [{:keys [order lines open? set-open! on-saved]}]
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        user (:user order)
        single-line (when (= 1 (count lines)) (first lines))
        model-id (get-in single-line [:model :id])
        single-line? (some? model-id)
        reservation-ids (vec (mapcat :reservationIds lines))
        start-iso (first (sort (map :startDate lines)))
        end-iso (last (sort (map :endDate lines)))

        [start-date set-start-date!] (uix/use-state nil)
        [end-date set-end-date!] (uix/use-state nil)
        [month set-month!] (uix/use-state #(date-fns/startOfMonth (js/Date.)))
        [quantity set-quantity!] (uix/use-state 1)
        [saving? set-saving!] (uix/use-state false)

        today (date-fns/startOfDay (js/Date.))
        {grid-start :start grid-end :end} (cal/grid-range month)
        range-variables {:startDate (cal/day-key grid-start)
                         :endDate (cal/day-key grid-end)}

        model-result (data/use-model-availability
                      (merge range-variables
                             {:modelId model-id
                              :userId (:id user)
                              :excludeReservationIds reservation-ids})
                      (and open? single-line?))
        reservations-result (data/use-reservations-availability
                             (merge range-variables {:reservationIds reservation-ids})
                             (and open? (not single-line?)))
        {days :data ready? :ready? error? :error?} (if single-line?
                                                     model-result
                                                     reservations-result)

        available? (if single-line?
                     (fn [day] (>= (or (:quantity day) 0) quantity))
                     :available)

        change-range! (fn [from to]
                        (set-start-date! from)
                        (set-end-date! to))
        save! (fn []
                (set-saving! true)
                (-> (data/update-lines! pool-id order lines
                                        {:start-date (cal/day-key start-date)
                                         :end-date (cal/day-key end-date)
                                         :quantity (when single-line? quantity)})
                    (p/then (fn [_]
                              (set-open! false)
                              (on-saved)))
                    (p/catch (fn [_]
                               (.. toast (error (t "error.action.error")))))
                    (p/finally (fn [_ _] (set-saving! false)))))]

    (uix/use-effect
     (fn []
       (when open?
         (let [from (date-from-iso start-iso)]
           (set-start-date! from)
           (set-end-date! (date-from-iso end-iso))
           (set-quantity! (or (:quantity single-line) 1))
           (set-month! (date-fns/startOfMonth (or from (js/Date.)))))))
     [open? start-iso end-iso single-line])

    ($ Dialog {:open open? :on-open-change set-open!}
       ($ DialogContent {:class-name "sm:max-w-[980px]"
                         :data-test-id "calendar-dialog"}

          ($ DialogHeader
             ($ DialogTitle (t "orders.calendar.title"))
             ($ DialogDescription (str (:firstname user) " " (:lastname user))))

          ;; bottom aligned so the labelled quantity field lines up with the rest
          ($ :div {:class-name "flex items-end gap-2"}
             ($ MonthNav {:month month :set-month! set-month! :min-date today})
             ($ :div {:class-name "ml-auto flex items-end gap-2"}
                ($ DateField {:t t
                              :date start-date
                              :label (t "orders.edit.start-date")
                              :test-id "calendar-start-date"})
                ($ :span {:class-name "mb-2 text-muted-foreground"} "–")
                ($ DateField {:t t
                              :date end-date
                              :label (t "orders.edit.end-date")
                              :test-id "calendar-end-date"})
                (when single-line?
                  ($ :div {:class-name "grid gap-1"}
                     ($ :label {:class-name "text-xs text-muted-foreground"
                                :html-for "calendar-quantity"}
                        (t "orders.calendar.quantity"))
                     ($ Input {:id "calendar-quantity"
                               :type "number"
                               :min 1
                               :class-name "w-18"
                               :data-test-id "calendar-quantity"
                               :value quantity
                               :on-change #(let [n (js/parseInt (.. % -target -value))]
                                             (set-quantity! (if (js/isNaN n) 1 (max 1 n))))})))
                ;; TODO entitlement groups once the backend exposes them
                ($ Select {:value "user" :disabled true}
                   ($ SelectTrigger {:class-name "w-44"
                                     :aria-label (t "orders.calendar.borrower")}
                      ($ SelectValue))
                   ($ SelectContent
                      ($ SelectItem {:value "user"} (t "orders.calendar.borrower"))))))

          ($ :div {:class-name "relative"}
             (when-not ready?
               ($ :div {:class-name "absolute inset-0 z-10 flex items-center justify-center bg-background/60"
                        :aria-busy true}
                  ($ Spinner)))
             (if error?
               ($ :div {:class-name "p-4 text-center text-sm text-destructive"}
                  (t "common.load-error"))
               ($ BookingCalendar {:month month
                                   :set-month! set-month!
                                   :days (cal/index-by-date days)
                                   :start-date start-date
                                   :end-date end-date
                                   :on-range-change change-range!
                                   :single-line? single-line?
                                   :available? available?
                                   :min-date today})))

          ($ :div {:class-name "rounded-md border text-sm"}
             (for [line lines]
               ($ LineSummary {:key (first (:reservationIds line)) :line line})))

          ($ DialogFooter
             ($ DialogClose {:as-child true}
                ($ Button {:type "button" :variant "outline"} (t "common.cancel")))
             ($ Button {:type "button"
                        :data-test-id "calendar-save"
                        :disabled (or saving? (nil? start-date) (nil? end-date))
                        :onClick save!}
                (when saving? ($ Spinner))
                (t "orders.calendar.save")))))))
