(ns leihs.lending.client.routes.pools.contracts.page
  (:require
   ["@@/card" :refer [Card CardContent CardHeader CardTitle CardDescription]]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  ($ :div {:class-name "space-y-6"}
     ($ Card
        ($ CardHeader
           ($ CardTitle "CONTRACTS")
           ($ CardDescription "TODO"))
        ($ CardContent "Lorem ipsum dolor sit amet"))))