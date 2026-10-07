(ns leihs.lending.client.routes.pools.orders.edit.components.reservation-lines
  (:require
   ["@@/badge" :refer [Badge]]
   ["@@/button" :refer [Button]]
   ["@@/customized/checkbox" :refer [Checkbox]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuItem DropdownMenuTrigger]]
   ["@@/table" :refer [Table TableBody TableCell TableRow]]
   ["lucide-react" :refer [Check ChevronDown TriangleAlert X]]
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
  "Heading of a date-range group, selecting all its lines at once."
  [{:keys [date-range selected? toggle!]}]
  (let [[t] (useTranslation)
        days (duration-days (:start-date date-range) (:end-date date-range))]
    ($ TableRow {:class-name "bg-muted hover:bg-muted border-b-3 [&>td]:py-3"}
       ($ TableCell {:class-name "w-10"}
          ($ Checkbox {:checked selected?
                       :data-test-id "select-range"
                       :aria-label (t "orders.edit.select-range")
                       :on-checked-change toggle!}))
       ($ TableCell {:col-span 3}
          ($ :div {:class-name "flex items-center gap-2"}
             ($ :span {:class-name "font-medium"}
                (t "orders.edit.date-range"
                   (cj {:startDate (format-weekday-date t (date-from-iso (:start-date date-range)))
                        :endDate (format-weekday-date t (date-from-iso (:end-date date-range)))})))
             ($ Badge {:class-name "bg-muted-foreground/60 text-background"}
                (t "orders.duration.days" (cj {:count days})))))
       ($ TableCell {:class-name "text-right text-muted-foreground"}
          (t "orders.edit.ordered"))
       ($ TableCell {:class-name "text-right text-muted-foreground"}
          (t "orders.edit.available"))
       ($ TableCell)
       ($ TableCell))))

(defui AvailabilityStatus
  "Icon telling whether the line's quantity is available to the user's groups,
   only by taking items from other groups, or not at all."
  [{:keys [quantity available-quantity total-available-quantity]}]
  (let [[icon class-name status]
        (cond
          (<= quantity available-quantity) [Check "text-lime-600" "ok"]
          (<= quantity total-available-quantity) [TriangleAlert "text-amber-500" "other-groups"]
          :else [X "text-destructive" "unavailable"])]
    ($ :span {:class-name (str "flex justify-center " class-name)
              :data-test-id "line-availability-status"
              :data-status status}
       ($ icon {:class-name "size-5"}))))

(defui LineRow
  [{:keys [line selected? toggle! on-edit on-delete]}]
  (let [[t] (useTranslation)
        {:keys [model quantity availableQuantity totalAvailableQuantity]} line
        stub! #(.. toast (message (t "orders.actions.not-available")))]
    ($ TableRow {:class-name "bg-muted/30 [&>td]:py-1"
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
       ($ TableCell
          ($ :div {:class-name "grid grid-cols-[2.5rem_2.5rem] justify-end gap-x-1 text-right tabular-nums"}
             ($ :span {:class-name (when (< availableQuantity quantity) "text-destructive")
                       :title (if (neg? availableQuantity)
                                (t "orders.edit.available-overbooked-title"
                                   (cj {:quantity (- availableQuantity)}))
                                (t "orders.edit.available-title"))
                       :data-test-id "line-available-quantity"}
                availableQuantity)
             (when (not= totalAvailableQuantity availableQuantity)
               ($ :span {:class-name (when-not (and (< availableQuantity quantity)
                                                     (<= quantity totalAvailableQuantity))
                                       "text-muted-foreground/60")
                         :title (t "orders.edit.total-available-title")
                         :data-test-id "line-total-available-quantity"}
                  (str "(" totalAvailableQuantity ")")))))
       ($ TableCell {:class-name "w-10"}
          ($ AvailabilityStatus {:quantity quantity
                                 :available-quantity availableQuantity
                                 :total-available-quantity totalAvailableQuantity}))
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
   calendar, `on-delete-line` deletes a line."
  [{:keys [lines selected toggle! toggle-many! on-edit-line on-delete-line]}]
  (let [[t] (useTranslation)]
    (if (empty? lines)
      ($ :div {:class-name "p-4 text-center text-sm text-muted-foreground"}
         (t "orders.edit.empty"))
      ($ :div {:class-name "flex flex-col gap-6"}
         (for [date-range (group-lines lines)
               :let [line-keys (map line-key (:lines date-range))]]
           ($ :div {:key (str (:start-date date-range) "_" (:end-date date-range))
                    :class-name "border rounded-md overflow-hidden"}
              ($ Table
                 ($ TableBody
                    ($ RangeRow {:date-range date-range
                                 :selected? (every? selected line-keys)
                                 :toggle! #(toggle-many! line-keys %)})
                    (for [line (:lines date-range)]
                      ($ LineRow {:key (line-key line)
                                  :line line
                                  :selected? (contains? selected (line-key line))
                                  :toggle! #(toggle! (line-key line) %)
                                  :on-edit #(on-edit-line line)
                                  :on-delete #(on-delete-line line)}))))))))))
