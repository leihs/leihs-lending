(ns leihs.lending.client.routes.pools.orders.edit.components.reservation-lines
  (:require
   ["@@/badge" :refer [Badge]]
   ["@@/button" :refer [Button]]
   ["@@/customized/checkbox" :refer [Checkbox]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuItem DropdownMenuTrigger]]
   ["@@/table" :refer [Table TableBody TableCell TableRow]]
   ["lucide-react" :refer [ChevronDown]]
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
  "Date-range groups in chronological order, with the lines sorted by model name."
  [lines]
  (->> lines
       (sort-by (juxt :startDate :endDate #(or (get-in % [:model :name]) "")))
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
       ($ TableCell {:col-span 5}
          ($ :div {:class-name "flex items-center justify-between gap-4"}
             ($ :span {:class-name "font-medium"}
                (t "orders.edit.date-range"
                   (cj {:startDate (format-weekday-date t (date-from-iso (:start-date date-range)))
                        :endDate (format-weekday-date t (date-from-iso (:end-date date-range)))})))
             ($ Badge {:class-name "bg-muted-foreground/60 text-background"}
                (t "orders.duration.days" (cj {:count days}))))))))

(defui LineRow
  [{:keys [line selected? toggle! on-edit]}]
  (let [[t] (useTranslation)
        model (:model line)
        stub! #(.. toast (message (t "orders.actions.not-available")))]
    ($ TableRow {:class-name "bg-muted/30 [&>td]:py-1"
                 :data-test-id "reservation-line"}
       ($ TableCell {:class-name "w-10"}
          ($ Checkbox {:checked selected?
                       :data-test-id "select-line"
                       :aria-label (t "orders.edit.select-line")
                       :on-checked-change toggle!}))
       ($ TableCell {:class-name "w-16 tabular-nums"
                     :data-test-id "line-quantity"}
          (str (:quantity line) " / " (:availableQuantity line)))
       ($ TableCell {:class-name "w-16"}
          ($ ModelImage {:model model}))
       ($ TableCell {:class-name "w-12"}
          ($ ModelBadge {:type (-> model :type)}))
       ($ TableCell {:class-name "w-full font-semibold whitespace-normal"}
          (:name model))
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
                   ($ DropdownMenuItem {:onSelect stub!}
                      (t "orders.edit.line-delete")))))))))

(defui ReservationLines
  "The order's reservation lines, grouped by date range.
   `selected` is the set of selected line keys, `on-edit-line` opens a line's
   calendar."
  [{:keys [lines selected toggle! toggle-many! on-edit-line]}]
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
                                  :on-edit #(on-edit-line line)}))))))))))
