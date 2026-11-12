(ns leihs.lending.client.components.filters.date-filter
  (:require
   ["date-fns" :as date-fns]
   ["react-router" :as router]
   [leihs.lending.client.components.date-input :refer [DateInput]]
   [leihs.lending.client.lib.date-utils :refer [date-from-iso]]
   [uix.core :refer [$ defui]]))

(defui DateFilter [{:keys [class-name param title]}]
  (let [[search-params set-search-params!] (router/useSearchParams)
        value (.. search-params (get param)) ;; param: startDate|endDate
        handle-change (fn [date]
                        (if date
                          (.set search-params param (date-fns/format date "yyyy-MM-dd"))
                          (.delete search-params param))
                        (.set search-params "page" "1")
                        (set-search-params! search-params))]

    ($ DateInput {:date (date-from-iso value)
                  :on-change handle-change
                  :clearable? true
                  :label title
                  :placeholder title
                  :test-id (str param "-filter")
                  :class-name class-name})))
