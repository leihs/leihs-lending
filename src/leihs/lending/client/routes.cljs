(ns leihs.lending.client.routes
  (:require
   ["react-router" :as router]
   [leihs.lending.client.lib.utils :refer [cj]]
   [leihs.lending.client.loader :as loader]
   [leihs.lending.client.routes.error :rename {page error-page}]
   [leihs.lending.client.routes.layout :rename {layout root-layout}]
   [leihs.lending.client.routes.home.page :rename {page home-page}]
   [leihs.lending.client.routes.pools.layout :rename {layout pools-layout}]
   [leihs.lending.client.routes.pools.contracts.page :rename {page contracts-page}]
   [leihs.lending.client.routes.pools.daily.page :rename {page daily-page}]
   [leihs.lending.client.routes.pools.orders.page :rename {page orders-page}]
   [leihs.lending.client.routes.pools.visits.page :rename {page visits-page}]
   [leihs.lending.client.provider.pool-provider :refer [pool-provider]]
   [uix.core :as uix :refer [$]]))

(def routes
  (router/createBrowserRouter
   (cj
    [{:path "/lending/"
      :id "root"
      :element ($ root-layout)
      :errorElement ($ error-page)
      :loader loader/root-layout

      :children
      [{:index true
        :element ($ home-page)}

       {:path ":pool-id/"
        :id "pool"
        :element ($ pool-provider)
        ;;:loader loader/pool-layout

        :children
        [{:element ($ pools-layout)
          :children
          [{:index true
            :loader #(router/redirect "daily")}

           {:path "daily"
            :element ($ daily-page)}

           {:path "orders"
            :element ($ orders-page)}

           {:path "visits"
            :loader loader/visits-page
            :element ($ visits-page)}

           {:path "contracts"
            :element ($ contracts-page)}]}]}]}

     {:path "*"
      :element ($ error-page)}])))
