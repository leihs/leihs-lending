(ns leihs.lending.client.components.booking-calendar.grid
  (:require
   ["@@/tooltip" :refer [Tooltip TooltipContent TooltipTrigger]]
   ["date-fns" :as date-fns]
   ["lucide-react" :refer [CircleCheck CircleX EyeClosed TreePalm TriangleAlert]]
   ["react-i18next" :refer [useTranslation]]
   [leihs.lending.client.lib.calendar :as cal]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :as uix :refer [$ defui]]))

(def ^:private closed-restrictions
  "Restrictions that close the pool for the day."
  #{"NON_WORKDAY" "HOLIDAY"})

(def ^:private restriction-icons
  {"NON_WORKDAY" EyeClosed
   "HOLIDAY" TreePalm
   "VISITS_CAPACITY_REACHED" TriangleAlert})

(defn- stepped-date
  "Target of a keyboard navigation key, nil for keys that don't navigate."
  [date key]
  (case key
    "ArrowLeft" (date-fns/addDays date -1)
    "ArrowRight" (date-fns/addDays date 1)
    "ArrowUp" (date-fns/addDays date -7)
    "ArrowDown" (date-fns/addDays date 7)
    "Home" (date-fns/startOfISOWeek date)
    "End" (date-fns/endOfISOWeek date)
    "PageUp" (date-fns/addMonths date -1)
    "PageDown" (date-fns/addMonths date 1)
    nil))

(defui DayCell
  "One day of the grid"
  [{:keys [date day month-label outside? disabled? in-range? range-edge?
           inner-sides available? focused? single-line? on-select on-preview]}]
  (let [[t] (useTranslation)
        restrictions (:startDateRestrictions day)
        closed? (some closed-restrictions restrictions)
        availability-color (cond
                             (and in-range? closed?) "text-foreground/55"
                             in-range? "text-foreground"
                             closed? "text-foreground/25"
                             :else "text-foreground/60")
        label (fn [restriction]
                (or (when (= restriction "HOLIDAY") (:holidayName day))
                    (t (str "calendar.restrictions." restriction) (cj {:val date}))))]
    ($ :td {:role "gridcell"
            :aria-selected in-range?
            :class-name (str "h-28 border border-border p-0 align-top"
                             (when (:left inner-sides) " border-l-background")
                             (when (:right inner-sides) " border-r-background")
                             (when (:top inner-sides) " border-t-background")
                             (when (:bottom inner-sides) " border-b-background"))}
       ($ :button
          {:type "button"
           :data-day (cal/day-key date)
           :data-test-id "calendar-day"
           :aria-label (t "common.date.formatDate" (cj {:val date}))
           :tab-index (if focused? 0 -1)
           :disabled disabled?
           :on-click #(on-select date)
           :on-mouse-enter #(on-preview date)
           :class-name (str "flex size-full flex-col "
                            (cond
                              (and disabled? in-range?) "cursor-default text-muted-foreground bg-hatched bg-red-100 dark:bg-red-950"
                              disabled? "cursor-default text-muted-foreground bg-hatched"
                              (and in-range? (true? available?)) "bg-lime-200 dark:bg-lime-950"
                              (and in-range? (false? available?)) "bg-red-100 dark:bg-red-950"
                              in-range? "bg-accent"
                              :else "hover:bg-accent")
                            (when-not disabled?
                              (cond
                                closed? " text-muted-foreground/40"
                                outside? " text-muted-foreground"))
                            (when range-edge? " ring-2 ring-inset ring-ring/60"))}

          ;; label of the cell: day, month, restriction icons
          ($ :span {:class-name "flex w-full items-center gap-1 text-sm"}
             ($ :span {:class-name "w-7 shrink-0 rounded-br-sm bg-muted-foreground/25 text-center pt-[3px] pb-0.5 text-foreground"}
                (.getDate date))
             ($ :span {:class-name "text-muted-foreground/45"} month-label)
             (when-not disabled?
               ($ :span {:class-name "ml-auto flex items-center gap-0.5 pr-1 text-foreground"}
                  (for [restriction restrictions]
                    (when-let [icon (restriction-icons restriction)]
                      ($ Tooltip {:key restriction}
                         ($ TooltipTrigger {:as-child true}
                            ($ :span {:data-test-id "calendar-restriction"}
                               ($ icon {:class-name "size-4"
                                        :aria-label (label restriction)})))
                         ($ TooltipContent (label restriction))))))))

          ;; availability
          (when-not disabled?
            ($ :span {:class-name "flex w-full flex-1 flex-col items-center px-1.5 pb-1.5"}
               (if single-line?

                 ;; single-line mode: show available quantities (fulfillability) on this day
                 (let [available-quantity (:availableQuantity day)
                       total-available-quantity (:totalAvailableQuantity day)]
                   ($ :<>
                      ($ :span {:class-name (str "mt-2 text-[40px] leading-none font-light tabular-nums " availability-color)
                                :data-test-id "calendar-day-quantity"}
                         (if (some? available-quantity) available-quantity "–"))
                      (when (and (some? total-available-quantity)
                                 (not= total-available-quantity available-quantity))
                        ($ :span {:class-name "flex flex-1 items-center text-sm text-muted-foreground tabular-nums"}
                           total-available-quantity))))

                 ;; multi-line mode: icon to indicate whether the total of all reservation lines are fulfillable on this day
                 ($ :span {:class-name "flex flex-1 items-center mb-[10%]"}
                    (if (:available day)
                      ($ CircleCheck {:class-name (str "size-10 stroke-1 " availability-color)
                                      :data-test-id "calendar-day-available"})
                      ($ CircleX {:class-name (str "size-10 stroke-1 " availability-color)
                                  :data-test-id "calendar-day-unavailable"}))))))))))

(defui BookingCalendar
  "Grid with 6 x 7 cells showing one month (+ days of adjacent months).
   Clicking a date picks it as a one-day range and starts a hover preview;
   clicking another date extends the range to it. Escape ends the preview.
   Days before `min-date` cannot be picked."
  [{:keys [month set-month! days start-date end-date on-range-change
           single-line? available? min-date]}]
  (let [[t i18n] (useTranslation)
        locale (.-language i18n)
        weekdays (cal/weekday-names locale)
        months (cal/month-names locale)
        {grid-start :start grid-end :end} (cal/grid-range month)

        grid-ref (uix/use-ref nil)
        focus-pending (uix/use-ref false)
        [anchor set-anchor!] (uix/use-state nil)
        [preview set-preview!] (uix/use-state nil)
        [focused set-focused!] (uix/use-state nil)

        focus-date (let [candidate (or focused start-date (date-fns/startOfMonth month))]
                     (if (cal/within? candidate grid-start grid-end)
                       candidate
                       (date-fns/startOfMonth month)))
        focus-key (cal/day-key focus-date)

        ;; while picking, the preview replaces the committed range
        [from to] (if anchor
                    (let [other (or preview anchor)]
                      [(cal/earlier anchor other) (cal/later anchor other)])
                    [start-date end-date])

        in-range? (fn [date] (cal/within? date from to))
        inner-sides (fn [date]
                      (let [weekday (date-fns/getISODay date)
                            in-grid-range? #(and (cal/within? % grid-start grid-end)
                                                 (in-range? %))]
                        (cond-> #{}
                          (and (> weekday 1) (in-range? (date-fns/addDays date -1))) (conj :left)
                          (and (< weekday 7) (in-range? (date-fns/addDays date 1))) (conj :right)
                          (in-grid-range? (date-fns/addDays date -7)) (conj :top)
                          (in-grid-range? (date-fns/addDays date 7)) (conj :bottom))))
        disabled? (fn [date]
                    (boolean (and min-date
                                  (date-fns/isBefore date (date-fns/startOfDay min-date)))))
        abandon! (fn []
                   (set-anchor! nil)
                   (set-preview! nil))
        select! (fn [date]
                  (if anchor
                    (do (on-range-change (cal/earlier anchor date) (cal/later anchor date))
                        (abandon!))
                    (do (on-range-change date date)
                        (set-anchor! date)
                        (set-preview! date))))
        move-to! (fn [date]
                   (when-not (disabled? date)
                     (set-focused! date)
                     (reset! focus-pending true)
                     (when anchor (set-preview! date))
                     (when-not (date-fns/isSameMonth date month)
                       (set-month! (date-fns/startOfMonth date)))))
        key-down (fn [e]
                   (if (= (.-key e) "Escape")
                     (when anchor (abandon!))
                     (when-let [target (stepped-date focus-date (.-key e))]
                       (.preventDefault e)
                       (move-to! target))))]

    (uix/use-effect
     (fn []
       (when @focus-pending
         (reset! focus-pending false)
         (some-> ^js @grid-ref
                 (.querySelector (str "[data-day='" focus-key "']"))
                 (.focus))))
     [focus-key])

    ($ :div {:class-name "overflow-x-auto"}
       ($ :table {:role "grid"
                  :ref grid-ref
                  :aria-label (t "calendar.title")
                  :data-test-id "booking-calendar"
                  :class-name "w-full min-w-[640px] table-fixed border-collapse text-sm"
                  :on-key-down key-down
                  :on-mouse-leave #(when anchor (set-preview! anchor))}

          ($ :thead
             ($ :tr
                ($ :th {:class-name "w-10 pb-1 text-left text-xs font-normal text-muted-foreground"}
                   (t "calendar.week-number"))
                (for [weekday weekdays]
                  ($ :th {:key weekday
                          :role "columnheader"
                          :class-name "pb-1 text-left text-xs font-normal text-muted-foreground"}
                     weekday))))

          ($ :tbody
             (for [week (cal/grid-weeks month)]
               ($ :tr {:key (cal/day-key (first week)) :role "row"}
                  ($ :th {:role "rowheader"
                          :scope "row"
                          :class-name "border border-border text-center align-middle text-xs font-normal text-muted-foreground"}
                     (date-fns/getISOWeek (first week)))
                  (for [date week]
                    (let [date-key (cal/day-key date)
                          day (get days date-key)]
                      ($ DayCell {:key date-key
                                  :date date
                                  :day day
                                  :month-label (nth months (.getMonth date))
                                  :outside? (not (date-fns/isSameMonth date month))
                                  :disabled? (disabled? date)
                                  :in-range? (in-range? date)
                                  :inner-sides (when (in-range? date) (inner-sides date))
                                  :range-edge? (boolean (and anchor
                                                             (or (date-fns/isSameDay date from)
                                                                 (date-fns/isSameDay date to))))
                                  :available? (when day
                                                (boolean
                                                 (and available?
                                                      (available? day)
                                                      (not (and from (date-fns/isSameDay date from)
                                                                (seq (:startDateRestrictions day))))
                                                      (not (and to (date-fns/isSameDay date to)
                                                                (seq (:endDateRestrictions day)))))))
                                  :focused? (= date-key focus-key)
                                  :single-line? single-line?
                                  :on-select select!
                                  :on-preview #(when anchor (set-preview! %))}))))))))))
