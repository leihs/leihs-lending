(ns leihs.lending.client.routes.debug.page
  (:require
   ["@@/card" :refer [Card CardContent CardHeader CardTitle]]
   ["react-router" :refer [useRouteLoaderData]]
   [leihs.lending.client.lib.utils :refer [cj]]
   [leihs.lending.client.routes.debug.components.error-demos :as error-demos]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  (let [data (useRouteLoaderData "root")]
    ($ :div {:class-name "mt-12 mb-6 space-y-6"}
       ($ error-demos/main)
       ($ Card
          ($ CardHeader
             ($ CardTitle "Root loader data"))
          ($ CardContent
             ($ :pre {:class-name "text-xs overflow-hidden text-ellipsis"}
                (.stringify js/JSON (cj data) nil 2)))))))
