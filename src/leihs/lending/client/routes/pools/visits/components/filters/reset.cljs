(ns leihs.lending.client.routes.pools.visits.components.filters.reset
  (:require
   ["@@/button" :refer [Button]]
   ["@@/tooltip" :refer [Tooltip TooltipTrigger TooltipContent]]
   ["lucide-react" :refer [ListRestart]]
   [uix.core :as uix :refer [$ defui]]
   [uix.dom]))

(defui Reset [{:keys [class-name on-reset]}]
  (let [ref (uix/use-ref nil)]

    (uix/use-effect
     (fn []
       (let [on-key-down
             (fn [e]
               (when (and (= (.. e -code) "KeyR")
                          (.-altKey e)
                          (.-shiftKey e)
                          (not (.-ctrlKey e))
                          (not (.-metaKey e)))
                 (.preventDefault e)
                 (when ref
                   (when-let [input-element @ref]
                     (.. input-element (click))))))]

         (js/window.addEventListener "keydown" on-key-down)
         (fn [] (js/window.removeEventListener "keydown" on-key-down))))
     [])

    ($ Tooltip
       ($ TooltipTrigger {:as-child true}
          ($ Button {:ref ref
                     :size "icon"
                     :variant "outline"
                     :className (str " " class-name)
                     :data-test-id "reset-filters-button"
                     :on-click on-reset}
             ($ ListRestart)))
       ($ TooltipContent
          "Reset filters (Alt+Shift+R)"))))
