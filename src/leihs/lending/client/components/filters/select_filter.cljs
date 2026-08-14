(ns leihs.lending.client.components.filters.select-filter
  (:require
   ["@@/select" :refer [Select SelectContent SelectItem SelectTrigger
                        SelectValue]]
   ["react-router" :as router]
   [uix.core :as uix :refer [$ defui]]))

(defui SelectFilter [{:keys [class-name param items]}]
  (let [[search-params set-search-params!] (router/useSearchParams)
        value (.. search-params (get param))
        on-value-change (fn [value]
                          (if (= value nil)
                            (.delete search-params param)
                            (.set search-params param value))
                          (.set search-params "page" "1")
                          (set-search-params! search-params))]

    ($ Select {:value value
               :onValueChange on-value-change}
       ($ SelectTrigger {:name param
                         :class-name class-name}
          ($ SelectValue))
       ($ SelectContent
          (for [item items]
            ($ SelectItem {:key (:value item)
                           :value (:value item)
                           :data-test-id (:test-id item)}
               (:content item)))))))
