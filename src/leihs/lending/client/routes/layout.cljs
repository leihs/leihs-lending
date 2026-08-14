(ns leihs.lending.client.routes.layout
  (:require
   ["@@/sonner" :refer [Toaster]]
   ["@@/tooltip" :refer [TooltipProvider]]
   ["react-router" :refer [Outlet ScrollRestoration useLoaderData]]
   ["~/i18n.config.js"]
   [leihs.lending.client.routes.components.header :as header]
   [leihs.lending.client.provider.theme-provider :refer [ThemeProvider]]
   [uix.core :as uix :refer [$ defui]]))

(defui layout []
  (let [data (useLoaderData)]
    ($ ThemeProvider {:default-theme "system"}
       ($ TooltipProvider
          ($ :<>
             ($ ScrollRestoration)
             ($ header/main data)
             ($ :main {:class-name "md:container"}
                ($ Outlet)
                ($ Toaster {:position "top-center"
                            :closeButton true
                            :richColors true})))))))

