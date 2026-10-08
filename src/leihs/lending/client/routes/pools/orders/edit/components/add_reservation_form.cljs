(ns leihs.lending.client.routes.pools.orders.edit.components.add-reservation-form
  (:require
   ["@@/button" :refer [Button]]
   ["@@/calendar" :refer [Calendar]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["date-fns" :as date-fns]
   ["lucide-react" :refer [CalendarDays List]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   ["sonner" :refer [toast]]
   [leihs.lending.client.lib.calendar :as cal]
   [leihs.lending.client.lib.date-utils :refer [format-date]]
   [leihs.lending.client.lib.utils :refer [cj jc]]
   [leihs.lending.client.routes.pools.orders.edit.components.model-search-field :refer [ModelSearchField]]
   [leihs.lending.client.routes.pools.orders.edit.data :as data]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui DateField
  "Date picker of the form adding a reservation."
  [{:keys [value set-value! label test-id]}]
  (let [[t] (useTranslation)
        [open? set-open!] (uix/use-state false)
        select! (fn [date]
                  (set-open! false)
                  (when date (set-value! date)))]
    ($ Popover {:open open? :on-open-change set-open!}
       ($ PopoverTrigger {:asChild true}
          ($ Button {:variant "outline"
                     :class-name "min-w-44 justify-start font-normal"
                     :aria-label label
                     :data-test-id test-id}
             ($ CalendarDays {:class-name "size-4"})
             ($ :span {:class-name "truncate"} (format-date t value))))
       ($ PopoverContent {:class-name "w-[280px]"}
          ($ Calendar {:mode "single"
                       :captionLayout "dropdown"
                       :selected value
                       :defaultMonth value
                       :endMonth (date-fns/addYears (js/Date.) 50)
                       :onSelect select!})))))

(defui AddReservationForm
  "Form adding the model picked in the search field to the order, over the
   chosen date range. `on-added` runs after a successful add."
  [{:keys [order on-added]}]
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        [start-date set-start-date!] (uix/use-state (js/Date.))
        [end-date set-end-date!] (uix/use-state (js/Date.))
        stub! #(.. toast (message (t "orders.actions.not-available")))
        add! (fn [{:keys [kind id] :as item}]
               (if (= kind :model)
                 (-> (data/create-model-reservation! pool-id order id
                                                     {:start-date (cal/day-key start-date)
                                                      :end-date (cal/day-key end-date)})
                     (p/then (fn [_]
                               (.. toast (success (t "orders.edit.added"
                                                     (cj {:name (:name item)}))))
                               (on-added)))
                     (p/catch (fn [_]
                                (.. toast (error (t "error.action.error"))))))
                 (stub!)))]

    ($ :div {:class-name "flex flex-wrap items-center gap-2"}
       ($ DateField {:value start-date
                     :set-value! set-start-date!
                     :label (t "orders.edit.start-date")
                     :test-id "new-reservation-start-date"})
       ($ DateField {:value end-date
                     :set-value! set-end-date!
                     :label (t "orders.edit.end-date")
                     :test-id "new-reservation-end-date"})
       ($ ModelSearchField {:on-select add!})
       ($ Button {:variant "outline"
                  :data-test-id "add-via-catalog"
                  :onClick stub!}
          ($ List)
          (t "orders.edit.add-via-catalog")))))
