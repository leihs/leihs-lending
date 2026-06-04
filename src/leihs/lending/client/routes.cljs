(ns leihs.lending.client.routes
  (:require
   ["react-router" :as router]
   [leihs.lending.client.lib.utils :refer [cj]]
   [leihs.lending.client.loader :as loader]
   [leihs.lending.client.routes.error :rename {page error-page}]
   [leihs.lending.client.routes.layout :rename {layout root-layout}]
   [leihs.lending.client.routes.page :rename {page home-page}]
   [leihs.lending.client.routes.pools.page :rename {page pool-page}]
   [leihs.lending.client.provider.pool-provider :refer [pool-provider]]
   [uix.core :as uix :refer [$]]))

(def routes
  (router/createBrowserRouter
   (cj
    [{:path "/"
      :loader #(router/redirect "/lending/")}

     {:path "/lending/"
      :id "root"
      :element ($ root-layout)
      ;; :errorElement ($ error-page)
      :loader loader/root-layout
      :children
      (cj
       [{:index true
         :element ($ home-page)}

        {:path ":pool-id/"
         :id "pool"
         :element ($ pool-provider)
         :loader loader/pool-layout
         :children
         (cj
          [{:index true
            :element ($ pool-page)}])}])}

     {:path "*"
      :element ($ error-page)}])))
