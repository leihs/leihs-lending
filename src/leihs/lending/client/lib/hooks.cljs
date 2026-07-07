(ns leihs.lending.client.lib.hooks
  (:require [uix.core :as uix]))

;; NOTE: docs https://usehooks.com/usedebounce
;; Returns [debounced-value reset!] where reset! cancels any pending timer
;; and immediately sets the debounced value to the given argument.
(defn use-debounce [value delay]
  (let [[debounced-value set-debounced-value!] (uix/use-state value)
        timer-ref (uix/use-ref nil)
        clear! (uix/use-callback
                (fn [v]
                  (when-let [t @timer-ref]
                    (js/clearTimeout t)
                    (reset! timer-ref nil))
                  (set-debounced-value! v))
                [])]

    (uix/use-effect
     (fn []
       (let [handler (js/setTimeout
                      (fn [] (set-debounced-value! value))
                      delay)]
         (reset! timer-ref handler)
         (fn [] (js/clearTimeout handler))))
     [value delay clear!])

    [debounced-value clear!]))