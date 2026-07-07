(ns leihs.lending.client.routes.pools.visits.components.filters.term-filter
  (:require
   ["@@/input" :refer [Input]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   [leihs.lending.client.lib.hooks :as hooks]
   [uix.core :as uix :refer [$ defui]]))

(defui TermFilter [{:keys [class-name]}]
  (let [ref (uix/use-ref nil)
        [search-params set-search-params!] (router/useSearchParams)
        [t] (useTranslation)
        [term set-term!] (uix/use-state (or (.get search-params "term") ""))
        [debounced-term reset-debounce!] (hooks/use-debounce term 300)
        prev-url-term (uix/use-ref (.get search-params "term"))]

    (uix/use-effect
     (fn []
       (let [prev @prev-url-term
             current (.get search-params "term")]
         (reset! prev-url-term current)

         ;; when term query param was in URL before (?search -> nil)
         ;; reset the debounced timer and term
         (if (and (some? prev)
                  (nil? current))

           ;; External removal of term query param: 
           ;; cancel pending debounce and reset input.
           (do (set-term! "")
               (reset-debounce! ""))

           ;; otherwise when term query param wasn't in URL and now is (nil -> ?search)
           ;; Guard ensures we only update the URL once the debounce has settled.
           (when (= debounced-term term)
             (cond
               (and (= debounced-term "") (.has search-params "term"))
               (do (.delete search-params "term")
                   (.set search-params "page" "1")
                   (set-search-params! search-params))

               (and (not= debounced-term "") (not= debounced-term current))
               (do (.set search-params "page" "1")
                   (.set search-params "term" debounced-term)
                   (set-search-params! search-params)))))))
     [debounced-term term search-params
      set-search-params! reset-debounce!])

    (uix/use-effect
     (fn []
       (let [on-key-down
             (fn [e]
               (when (and (= (.. e -code) "KeyF")
                          (.-altKey e)
                          (.-shiftKey e)
                          (not (.-ctrlKey e))
                          (not (.-metaKey e)))
                 (.preventDefault e)
                 (when ref
                   (when-let [input-element @ref]
                     (.focus input-element)))))]

         (js/window.addEventListener "keydown" on-key-down)
         (fn [] (js/window.removeEventListener "keydown" on-key-down))))
     [])

    ($ Input {:ref ref
              :placeholder (t "visits.filters.term")
              :name "term"
              :class-name (str "w-48 py-0" class-name)
              :value term
              :onChange #(set-term! (.. % -target -value))})))
