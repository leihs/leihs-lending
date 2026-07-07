(ns leihs.lending.client.routes.pools.visits.components.filters.visit-type-filter
  (:require
   ["@@/select" :refer [Select SelectContent SelectItem SelectTrigger
                        SelectValue]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   [uix.core :as uix :refer [$ defui]]))

(defui VisitTypeFilter [{:keys [class-name]}]
  (let [PARAM "visitType"
        [search-params set-search-params!] (router/useSearchParams)
        type (.. search-params (get "type"))
        [t] (useTranslation)

        value (.. search-params (get PARAM))
        on-value-change (fn [value]
                          (if (= value nil)
                            (.delete search-params PARAM)
                            (.set search-params PARAM value))
                          (.set search-params "page" "1")
                          (set-search-params! search-params))]

    ($ Select {:value value
               :disabled (= type "option")
               :onValueChange on-value-change}
       ($ SelectTrigger {:name PARAM
                         :class-name (str "w-[200px]" class-name)}
          ($ SelectValue))
       ($ SelectContent
          ($ SelectItem {:data-test-id "all"
                         :value nil}
             (t "visits.filters.all-visits"))
          ($ SelectItem {:data-test-id "HAND_OVER"
                         :value "HAND_OVER"}
             (t "visits.type.hand-over"))
          ($ SelectItem {:data-test-id "TAKE_BACK"
                         :value "TAKE_BACK"}
             (t "visits.type.take-back"))))))


