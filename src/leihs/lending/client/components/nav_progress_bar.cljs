(ns leihs.lending.client.components.nav-progress-bar
  (:require
   ["react-router" :refer [useNavigation]]
   [uix.core :as uix :refer [$ defui]]))

(defui main []
  (let [navigation (useNavigation)
        loading? (= (.-state navigation) "loading")
        [visible? set-visible!] (uix/use-state false)
        [completing? set-completing!] (uix/use-state false)
        timer-ref (uix/use-ref nil)]

    (uix/use-effect
     (fn []
       (if loading?
         (do
           (when-let [t @timer-ref]
             (js/clearTimeout t)
             (reset! timer-ref nil))
           (set-completing! false)
           (set-visible! true)
           js/undefined)
         (when visible?
           (set-completing! true)
           (let [t (js/setTimeout
                    (fn []
                      (reset! timer-ref nil)
                      (set-visible! false)
                      (set-completing! false))
                    500)]
             (reset! timer-ref t)
             (fn [] (js/clearTimeout t))))))
     [visible? loading?])

    (when visible?
      ($ :div {:class-name (str "fixed top-16 left-0 right-0 h-[2px] z-[49] overflow-hidden pointer-events-none bg-primary/15 "
                                (if completing? "nav-bar-out" "nav-bar-in"))}))))
