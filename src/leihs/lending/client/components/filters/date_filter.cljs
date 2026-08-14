(ns leihs.lending.client.components.filters.date-filter
  (:require
   ["@@/button" :refer [Button]]
   ["@@/calendar" :refer [Calendar]]
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["date-fns" :as date-fns]
   ["lucide-react" :refer [CalendarDays ChevronsUpDown]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   [leihs.lending.client.lib.date-utils :refer [format-date]]
   [uix.core :as uix :refer [$ defui]]))

(defui DateFilter [{:keys [class-name param title]}]
  (let [[search-params set-search-params!] (router/useSearchParams)
        [open set-open!] (uix/use-state false)
        [t] (useTranslation)
        value (.. search-params (get param)) ;; param: startDate|endDate
        handle-select (fn [date]
                        (set-open! false)
                        (let [formatted-date (if date
                                               (date-fns/format date "yyyy-MM-dd")
                                               nil)]
                          (if (or (= date nil) (= formatted-date value))
                            (.delete search-params param)
                            (.set search-params param formatted-date))

                          (.set search-params "page" "1")
                          (set-search-params! search-params)))]

    ($ Popover {:open open
                :on-open-change set-open!}
       ($ PopoverTrigger {:asChild true}
          ($ Button {:variant "outline"
                     :class-name (str "min-w-48 max-w-48 " class-name)
                     :data-test-id (str param "-filter-button")}

             ($ CalendarDays {:class-name "h-4 w-4"})
             (if value
               ($ :span {:class-name "truncate w-full text-left"}
                  (format-date t value))
               title)
             ($ ChevronsUpDown {:class-name "ml-auto h-4 w-4 shrink-0 opacity-50"})))

       ($ PopoverContent {:class-name "w-[280px]"}
          ($ Calendar {:mode "single"
                       :captionLayout "dropdown"
                       :data-test-id (str param "-calendar")
                       :selected (js/Date. value)
                       :onSelect handle-select})))))
