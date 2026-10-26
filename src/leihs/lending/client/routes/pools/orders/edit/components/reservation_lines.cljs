(ns leihs.lending.client.routes.pools.orders.edit.components.reservation-lines
  (:require
   ["@@/badge" :refer [Badge]]
   ["@@/button" :refer [Button]]
   ["@@/customized/checkbox" :refer [Checkbox]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuItem DropdownMenuTrigger]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["@@/table" :refer [Table TableBody TableCell TableHeader TableRow]]
   ["@@/tooltip" :refer [Tooltip TooltipContent TooltipTrigger]]
   ["lucide-react" :refer [CalendarArrowUp Check ChevronDown TriangleAlert X]]
   ["react-i18next" :refer [useTranslation]]
   ["sonner" :refer [toast]]
   [leihs.lending.client.components.entities.model-badge :refer [ModelBadge]]
   [leihs.lending.client.components.entities.model-image :refer [ModelImage]]
   [leihs.lending.client.lib.date-utils :refer [date-from-iso duration-days
                                                format-weekday-date]]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :refer [$ defui]]))

(defn line-key
  "Identifies a reservation line by the first of the reservations it groups."
  [line]
  (first (:reservationIds line)))

(defn group-lines
  "Date-range groups in chronological order, keeping the lines' order (by model
   name) within each group."
  [lines]
  (->> lines
       (sort-by (juxt :startDate :endDate))
       (partition-by (juxt :startDate :endDate))
       (map (fn [range-lines]
              {:start-date (:startDate (first range-lines))
               :end-date (:endDate (first range-lines))
               :lines range-lines}))))

(defui RangeRow
  "Heading of a date-range group, selecting all its lines at once.
   `on-apply` takes the range over into the add form unless `applied?`; no
   button without it."
  [{:keys [date-range selected? toggle! applied? on-apply]}]
  (let [[t] (useTranslation)
        days (duration-days (:start-date date-range) (:end-date date-range))]
    ($ TableRow {:class-name "bg-muted hover:bg-muted border-muted-foreground/30 [&>td]:py-3"}
       ($ TableCell {:class-name "w-10"}
          ($ Checkbox {:checked selected?
                       :data-test-id "select-range"
                       :aria-label (t "orders.edit.select-range")
                       :on-checked-change toggle!}))
       ($ TableCell {:col-span 3}
          ($ :div {:class-name "flex items-center gap-2"}
             ($ :span {:class-name "font-medium"}
                (let [{:keys [start-date end-date]} date-range
                      start (format-weekday-date t (date-from-iso start-date))]
                  (if (= start-date end-date)
                    start
                    (t "orders.edit.date-range"
                       (cj {:startDate start
                            :endDate (format-weekday-date t (date-from-iso end-date))})))))
             (when on-apply
               ($ Button {:variant "outline"
                          :size "icon-sm"
                          :title (t "orders.edit.apply-range")
                          :aria-label (t "orders.edit.apply-range")
                          :data-test-id "apply-range"
                          :disabled applied?
                          :onClick on-apply}
                  ($ CalendarArrowUp)))))
       ($ TableCell {:class-name "text-right text-muted-foreground"}
          (t "orders.edit.ordered"))
       ($ TableCell {:class-name "text-center text-muted-foreground"}
          (t "orders.edit.available"))
       ($ TableCell)
       ($ TableCell {:class-name "text-right"}
          ($ Badge {:class-name "bg-muted-foreground/60 text-background"}
             (t "orders.duration.days" (cj {:count days})))))))

(defui StatusIcon
  "Check when the ordered quantity is covered, else a cross."
  [{:keys [ok? class-name]}]
  (if ok?
    ($ Check {:class-name (str "size-5 text-lime-600 " class-name)})
    ($ X {:class-name (str "size-5 text-destructive " class-name)})))

(defui StatusRow
  "Row of the availability popover's list, aligned to its columns."
  [{:keys [class-name children]}]
  ($ :div {:class-name (str "col-span-3 grid grid-cols-subgrid items-center py-1 border-b " class-name)}
     children))

(defui AvailabilityStatus
  "Icon telling whether the line's quantity is available to the user's groups,
   only by taking items from other groups, or not at all. Opens a popover
   listing the numbers."
  [{:keys [model-name quantity available-quantity total-available-quantity borrowable-quantity]}]
  (let [[t] (useTranslation)
        difference (- available-quantity quantity)
        total-difference (- total-available-quantity quantity)
        [icon class-name status]
        (cond
          (<= quantity available-quantity) [Check "text-lime-600" "ok"]
          (<= quantity total-available-quantity) [Check "text-lime-600" "other-groups"]
          :else [X "text-destructive" "unavailable"])]
    ($ Popover
       ($ PopoverTrigger {:class-name (str "flex w-full justify-center cursor-pointer " class-name)
                          :data-test-id "line-availability-status"
                          :data-status status}
          ($ icon {:class-name "size-5"}))
       ($ PopoverContent {:align "end" :class-name "w-[420px] text-sm"}
          ($ :div {:class-name "font-semibold text-base"} model-name)
          (when (some? borrowable-quantity)
            ($ :div {:class-name "text-muted-foreground tabular-nums"}
               (t "orders.edit.borrowable" (cj {:quantity borrowable-quantity}))))
          ($ :dl {:class-name "grid grid-cols-[1fr_auto_auto] gap-x-6 mt-3 tabular-nums"}
             ($ StatusRow {:class-name "border-t"}
                ($ :dt (t "orders.edit.ordered"))
                ($ :dd {:class-name "text-right font-bold"} quantity))
             ($ StatusRow
                ($ :dt (t "orders.edit.available-in-period"))
                ($ :dd {:class-name "text-right"} available-quantity)
                (cond
                  (neg? available-quantity)
                  ($ Tooltip
                     ($ TooltipTrigger {:as-child true}
                        ($ TriangleAlert {:class-name "size-4 text-destructive"}))
                     ($ TooltipContent (t "orders.edit.pre-existing-overbooking")))

                  (not (neg? difference))
                  ($ StatusIcon {:ok? true})))
             (when (neg? difference)
               ($ StatusRow
                  ($ :dt (t "orders.edit.difference"))
                  ($ :dd {:class-name "text-right"} difference)
                  ($ StatusIcon {:ok? false})))
             (when (> total-available-quantity available-quantity)
               (let [unneeded-class (when (<= quantity available-quantity)
                                      " text-muted-foreground/50")]
                 ($ :<>
                    ($ StatusRow {:class-name "mt-3 border-t"}
                       ($ :dt {:class-name unneeded-class}
                          (t "orders.edit.available-in-other-groups"))
                       ($ :dd {:class-name (str "text-right" unneeded-class)}
                          (str "+" (- total-available-quantity available-quantity)))
                       (when (and (neg? difference) (not (neg? total-difference)))
                         ($ StatusIcon {:ok? true})))
                    (when (neg? total-difference)
                      ($ StatusRow
                         ($ :dt (t "orders.edit.difference"))
                         ($ :dd {:class-name "text-right"} total-difference)
                         ($ StatusIcon {:ok? false})))))))))))

(defui LineRow
  [{:keys [line selected? toggle! on-edit on-delete]}]
  (let [[t] (useTranslation)
        {:keys [model quantity availableQuantity totalAvailableQuantity]} line
        stub! #(.. toast (message (t "orders.actions.not-available")))]
    ($ TableRow {:class-name "bg-muted/60 hover:bg-muted border-muted-foreground/20 [&>td]:py-1"
                 :data-test-id "reservation-line"}
       ($ TableCell {:class-name "w-10"}
          ($ Checkbox {:checked selected?
                       :data-test-id "select-line"
                       :aria-label (t "orders.edit.select-line")
                       :on-checked-change toggle!}))
       ($ TableCell {:class-name "w-16"}
          ($ ModelImage {:model model}))
       ($ TableCell {:class-name "w-12"}
          ($ ModelBadge {:type (-> model :type)}))
       ($ TableCell {:class-name "w-full font-semibold whitespace-normal"}
          (:name model))
       ($ TableCell {:class-name "w-24 min-w-24 text-right font-bold tabular-nums"
                     :data-test-id "line-quantity"}
          quantity)
       ($ TableCell {:class-name "w-28 min-w-28"}
          ($ :div {:class-name "grid grid-cols-2 tabular-nums"}
             ($ :span {:class-name "text-right"
                       :data-test-id "line-available-quantity"}
                availableQuantity)
             (when (> totalAvailableQuantity availableQuantity)
               ($ :span {:class-name (str "text-right pr-2"
                                          (when (<= quantity availableQuantity)
                                            " text-muted-foreground/50"))
                         :data-test-id "line-total-available-quantity"}
                  (str "+" (- totalAvailableQuantity availableQuantity))))))
       ($ TableCell {:class-name "w-10"}
          ($ AvailabilityStatus {:model-name (:name model)
                                 :quantity quantity
                                 :available-quantity availableQuantity
                                 :total-available-quantity totalAvailableQuantity
                                 :borrowable-quantity (:borrowableQuantity model)}))
       ($ TableCell {:class-name "text-right"}
          ($ :div {:class-name "flex items-stretch justify-end"}
             ($ Button {:variant "outline"
                        :class-name "rounded-r-none"
                        :data-test-id "edit-line"
                        :onClick on-edit}
                (t "orders.edit.line-actions"))
             ($ DropdownMenu
                ($ DropdownMenuTrigger {:asChild true}
                   ($ Button {:variant "outline"
                              :size "icon"
                              :aria-label (t "common.more-actions")
                              :title (t "common.more-actions")
                              :class-name "rounded-l-none border-l-0"
                              :data-test-id "line-actions-menu"}
                      ($ ChevronDown)))
                ($ DropdownMenuContent {:align "end"}
                   ($ DropdownMenuItem {:onSelect stub!}
                      (t "orders.edit.line-timeline"))
                   ($ DropdownMenuItem {:onSelect stub!}
                      (t "orders.edit.line-swap-model"))
                   ($ DropdownMenuItem {:onSelect on-delete
                                        :data-test-id "delete-line"}
                      (t "orders.edit.line-delete")))))))))

(defui ReservationLines
  "The order's reservation lines, grouped by date range.
   `selected` is the set of selected line keys, `on-edit-line` opens a line's
   calendar, `on-delete-line` deletes a line, `on-apply-range` gets a group's
   start and end date unless they equal `applied-range`, offered only when
   there are several groups."
  [{:keys [lines selected toggle! toggle-many! applied-range on-apply-range
           on-edit-line on-delete-line]}]
  (let [[t] (useTranslation)
        groups (group-lines lines)
        several? (> (count groups) 1)]
    (if (empty? lines)
      ($ :div {:class-name "p-4 text-center text-sm text-muted-foreground"}
         (t "orders.edit.empty"))
      ($ :div {:class-name "flex flex-col gap-10"}
         (for [date-range groups
               :let [line-keys (map line-key (:lines date-range))]]
           ($ :div {:key (str (:start-date date-range) "_" (:end-date date-range))}
              ($ Table
                 ($ TableHeader {:class-name "[&_tr]:border-b-[2.5px]"}
                    ($ RangeRow {:date-range date-range
                                 :selected? (every? selected line-keys)
                                 :toggle! #(toggle-many! line-keys %)
                                 :applied? (= applied-range [(:start-date date-range)
                                                             (:end-date date-range)])
                                 :on-apply (when several?
                                             #(on-apply-range (:start-date date-range)
                                                              (:end-date date-range)))}))
                 ($ TableBody {:class-name "border border-muted-foreground/20"}
                    (for [line (:lines date-range)]
                      ($ LineRow {:key (line-key line)
                                  :line line
                                  :selected? (contains? selected (line-key line))
                                  :toggle! #(toggle! (line-key line) %)
                                  :on-edit #(on-edit-line line)
                                  :on-delete #(on-delete-line line)}))))))))))
