(ns leihs.lending.client.routes.layout
  (:require
   ["react-router" :refer [Outlet ScrollRestoration]]
   [uix.core :as uix :refer [$ defui]]))

(defui layout []
  ($ :<>
     ($ ScrollRestoration)
     ($ :main {:class-name "container mx-auto px-4 py-8"}
        ($ Outlet))))
