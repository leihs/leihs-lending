(ns leihs.lending.client.routes.error
  (:require
   ["@@/button" :refer [Button]]
   ["lucide-react" :refer [TriangleAlert]]
   ["react-router" :refer [useRouteError Link]]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  (let [error (useRouteError)
        status (some-> error .-status)
        message (or (some-> error .-statusText)
                    (some-> error .-message)
                    "Something went wrong.")]

    ($ :div {:class-name "container mx-auto max-w-md py-16 text-center space-y-6"}
       ($ TriangleAlert {:class-name "mx-auto h-12 w-12 text-destructive"})
       ($ :h1 {:class-name "text-2xl font-bold"} (or status "Error"))
       ($ :p {:class-name "text-muted-foreground"} message)
       ($ Button {:as-child true :variant "outline"}
          ($ Link {:to "/lending/"} "Back to start")))))
