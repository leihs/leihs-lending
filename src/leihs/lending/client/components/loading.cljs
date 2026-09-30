(ns leihs.lending.client.components.loading
  (:require
   ["@@/spinner" :refer [Spinner]]
   [uix.core :refer [$ defui]]))

(defui loading-fallback []
  ($ :div {:class "flex items-center justify-center w-screen h-screen"
           :aria-busy true}
     ($ Spinner)))
