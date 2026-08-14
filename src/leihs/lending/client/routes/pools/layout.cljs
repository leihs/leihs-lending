(ns leihs.lending.client.routes.pools.layout
  (:require
   ["@@/breadcrumb" :refer [Breadcrumb BreadcrumbItem BreadcrumbLink
                            BreadcrumbList BreadcrumbPage BreadcrumbSeparator]]
   ["@@/tabs" :refer [Tabs TabsContent TabsList TabsTrigger]]
   ["lucide-react" :refer [Home]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :refer [Link Outlet useLocation useParams
                           useRouteLoaderData generatePath]]
   [clojure.string :refer [split]]
   [leihs.core.core :refer [detect]]
   [uix.core :as uix :refer [$ defui]]))

(defui layout []
  (let [[t] (useTranslation)
        location (useLocation)
        params (useParams)
        pool-id (-> params (aget "pool-id"))
        root-data (useRouteLoaderData "root")
        available-pools (-> root-data :currentUser :availablePools)
        current-pool (detect #(= pool-id (:id %)) available-pools)
        pool-name (:name current-pool)
        base (str "/lending/" pool-id "/")
        route-name (-> (.-pathname location) (split #"/") last)]
    ($ :section {:class-name "mb-8"}
       ($ Breadcrumb {:className "my-8"}
          ($ BreadcrumbList
             ($ BreadcrumbItem
                ($ BreadcrumbLink {:asChild true}
                   ($ Link {:className "flex items-center gap-1.5"
                            :to "/lending/"
                            :viewTransition true}
                      ($ Home {:className "size-4"})
                      (t "pools.breadcrumb.lending"))))

             ($ BreadcrumbSeparator)

             ($ BreadcrumbItem
                ($ BreadcrumbLink {:asChild true}
                   ($ Link {:to (generatePath "/lending/:pool-id" #js{:pool-id pool-id})
                            :viewTransition true}
                      pool-name)))

             ($ BreadcrumbSeparator)

             ($ BreadcrumbItem
                ($ BreadcrumbPage (t (str "pools.tabs." route-name))))))

       ($ Tabs {:value route-name}
          ($ :div {:className "flex w-full mb-2"}
             ($ TabsList
                ($ TabsTrigger {:value "daily" :asChild true}
                   ($ Link {:to (str base "daily") :viewTransition true}
                      (t "pools.tabs.daily")))
                ($ TabsTrigger {:value "orders" :asChild true}
                   ($ Link {:to (str base "orders") :viewTransition true}
                      (t "pools.tabs.orders")))
                ($ TabsTrigger {:value "visits" :asChild true}
                   ($ Link {:to (str base "visits") :viewTransition true}
                      (t "pools.tabs.visits")))
                ($ TabsTrigger {:value "contracts" :asChild true}
                   ($ Link {:to (str base "contracts") :viewTransition true}
                      (t "pools.tabs.contracts")))))

          ($ TabsContent {:forceMount true
                          :tab-index -1}
             ($ Outlet))))))
