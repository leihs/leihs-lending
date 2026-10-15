(ns leihs.lending.client.components.booking-calendar.grid
  (:require
   ["date-fns" :as date-fns]
   ["@@/badge" :refer [Badge]]
   ["lucide-react" :refer [CircleCheck CircleX EyeClosed Users]]
   ["react-i18next" :refer [useTranslation]]
   [clojure.string :refer [join]]
   [leihs.lending.client.lib.calendar :as cal]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :as uix :refer [$ defui]]))

(def ^:private closed-restrictions
  "Restrictions that close the pool for the day, sharing one icon."
  #{"NON_WORKDAY" "HOLIDAY"})

(def ^:private restriction-icons
  {"VISITS_CAPACITY_REACHED" Users})

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
  [{:keys [date day month-label outside? disabled? in-range? range-start?
           range-end? available? focused? single-line? on-select on-preview]}]
  (let [[t] (useTranslation)
        restrictions (:startDateRestrictions day)
        closed (filter closed-restrictions restrictions)
        closed? (seq closed)
        label (fn [restriction] (t (str "calendar.restrictions." restriction)))]
    ($ :td {:role "gridcell"
            :aria-selected in-range?
            :class-name "h-28 border border-border p-0 align-top"}
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
                              disabled? "cursor-default text-muted-foreground"
                              (and in-range? (true? available?)) "bg-lime-200 dark:bg-lime-950"
                              (and in-range? (false? available?)) "bg-red-100 dark:bg-red-950"
                              in-range? "bg-accent"
                              :else "hover:bg-accent")
                            (when-not disabled?
                              (cond
                                closed? " text-muted-foreground/40"
                                outside? " text-muted-foreground"))
                            (when (or range-start? range-end?) " ring-2 ring-inset ring-ring"))}

          ;; label of the cell: day, month, icon when date is closed
          ($ :span {:class-name "flex w-full items-center gap-1 text-xs"}
             ($ :span {:class-name "rounded-br-sm bg-muted-foreground/25 px-1.5 pt-[3px] pb-0.5 text-foreground tabular-nums"}
                (.getDate date))
             ($ :span {:class-name "text-muted-foreground/60"} month-label)
             (when-not disabled?
               ($ :span {:class-name "ml-auto flex items-center gap-0.5 pr-1 text-foreground"}
                  (when closed?
                    ($ EyeClosed {:class-name "size-4"
                                  :aria-label (join ", " (map label closed))}))
                  (for [restriction (remove closed-restrictions restrictions)]
                    (when-let [icon (restriction-icons restriction)]
                      ($ icon {:key restriction
                               :class-name "size-3.5"
                               :aria-label (label restriction)}))))))

          ;; holiday name (when given)
          ($ :span {:class-name "h-3 w-full truncate px-1 text-center text-[10px] leading-3 text-muted-foreground"
                    :title (:holidayName day)}
             (:holidayName day))

          ;; availability
          (when-not disabled?
            ($ :span {:class-name "flex w-full flex-1 flex-col items-center px-1.5 pb-1.5"}
               (if single-line?

                 ;; single-line mode: show available quantities (fulfillability) on this day
                 (let [available-quantity (:quantity day)
                       total-quantity (:totalQuantity day)]
                   ($ :<>
                      ($ :span {:class-name (str "text-3xl font-light tabular-nums "
                                                 (if (and available-quantity (neg? available-quantity))
                                                   "text-destructive"
                                                   "text-foreground")
                                                 (cond
                                                   in-range? ""
                                                   closed? " opacity-25"
                                                   :else " opacity-50"))
                                :data-test-id "calendar-day-quantity"}
                         (if (some? available-quantity) available-quantity "–"))
                      (when (some? total-quantity)
                        ($ :span {:class-name "flex flex-1 items-center"}
                           ($ Badge {:class-name "bg-gray-400 text-white tabular-nums"} total-quantity)))))

                 ;; multi-line mode: icon to indicate whether the total of all reservation lines are fulfillable on this day
                 ($ :span {:class-name "flex flex-1 items-center mb-[10%]"}
                    (if (:available day)
                      ($ CircleCheck {:class-name "size-7 stroke-1"
                                      :data-test-id "calendar-day-available"})
                      ($ CircleX {:class-name "size-7 stroke-1 text-destructive"
                                  :data-test-id "calendar-day-unavailable"}))))))))))

(defui BookingCalendar
  "Grid with 6 x 7 cells showing one month (+ days of adjacent months).
   A range is picked by clicking its start or end date, which starts a hover preview, 
   and clicking another date. Escape abandons it.
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
                    (do (set-anchor! date)
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
                                  :in-range? (cal/within? date from to)
                                  :range-start? (boolean (and from (date-fns/isSameDay date from)))
                                  :range-end? (boolean (and to (date-fns/isSameDay date to)))
                                  :available? (when day (boolean (and available? (available? day))))
                                  :focused? (= date-key focus-key)
                                  :single-line? single-line?
                                  :on-select select!
                                  :on-preview #(when anchor (set-preview! %))}))))))))))
