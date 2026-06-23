(ns leihs.lending.client.provider.theme-provider
  (:require
   ["react" :as react]
   [uix.core :as uix :refer [$ defui]]))

(def initial-state
  [:theme "system"
   :set-theme (fn [_] nil)])

(def theme-provider-context
  (react/createContext (clj->js initial-state)))

(defui ThemeProvider
  [{:keys [children default-theme storage-key]
    :or {default-theme "system"
         storage-key "leihs-lending-theme"}}]
  (let [[theme set-theme!] (uix/use-state
                            (fn []
                              (or (.getItem js/localStorage storage-key)
                                  default-theme)))

        value (uix/use-memo
               (fn []
                 #js {:theme theme
                      :setTheme (fn [new-theme]
                                  (.setItem js/localStorage storage-key new-theme)
                                  (set-theme! new-theme))})
               [theme storage-key])]

    (uix/use-effect
     (fn []
       (let [root (.-documentElement js/document)]
         (-> root .-classList (.remove "light" "dark"))
         (if (= theme "system")
           (let [system-theme (if (.-matches (.matchMedia js/window "(prefers-color-scheme: dark)"))
                                "dark"
                                "light")]
             (-> root .-classList (.add system-theme)))
           (-> root .-classList (.add theme))))
       js/undefined)
     [theme])

    ($ (.-Provider theme-provider-context)
       {:value value}
       children)))

(defn use-theme []
  (let [context (react/useContext theme-provider-context)]
    (when (undefined? context)
      (throw (js/Error. "useTheme must be used within a ThemeProvider")))
    {:theme (.-theme context)
     :set-theme (.-setTheme context)}))
