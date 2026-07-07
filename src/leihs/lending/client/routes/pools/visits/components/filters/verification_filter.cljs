(ns leihs.lending.client.routes.pools.visits.components.filters.verification-filter
  (:require
   ["@@/select" :refer [Select SelectContent SelectItem SelectTrigger
                        SelectValue]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   [uix.core :as uix :refer [$ defui]]))

(defui VerificationFilter [{:keys [class-name]}]
  (let [PARAM "verification"
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
             (t "visits.filters.verification-all"))
          ($ SelectItem {:data-test-id "NONE_REQUIRED"
                         :value "NONE_REQUIRED"}
             (t "visits.verification.none"))
          ($ SelectItem {:data-test-id "USER"
                         :value "USER"}
             (t "visits.verification.user"))
          ($ SelectItem {:data-test-id "USER_AND_MODEL"
                         :value "USER_AND_MODEL"}
             (t "visits.verification.user-and-model"))))))


