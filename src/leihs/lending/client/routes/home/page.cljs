(ns leihs.lending.client.routes.home.page
  (:require
   ["@@/card" :refer [Card CardContent CardFooter CardHeader CardTitle]]
   ["@@/table" :refer [Table TableBody TableCell TableHead TableHeader
                       TableRow]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :refer [Link useRouteLoaderData]]
   [leihs.lending.client.components.typo :refer [Typo]]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  (let [[t] (useTranslation)
        data (useRouteLoaderData "root")
        pools (-> data :currentUser :user :availablePools)]
    ($ :<>
       ($ Card {:class-name "mt-12 mb-6 overflow-hidden pb-0"}
          ($ CardHeader
             ($ CardTitle (t "root.title")))
          ($ CardContent

             ($ :div {:class-name "border rounded-md"}
                ($ Table
                   ($ TableHeader
                      ($ TableRow
                         ($ TableHead (t "root.pools.name"))))

                   ($ TableBody
                      (for [pool pools]
                        ($ TableRow {:key (:id pool)
                                     :data-test-id "pool-row"
                                     :class-name "even:bg-muted"}
                           ($ TableCell
                              ($ Typo {:variant "link"}
                                 ($ Link {:class-name "underline"
                                          :to (str (:id pool))}
                                    (:name pool))))))))))

          ($ CardFooter {:class-name "py-3 px-6 border-t-[1px] bg-muted/50 flex items-center justify-end"}
             ($ Typo {:variant "link"}
                ($ :a {:target "_blank"
                       :href "/lending/graphiql"}
                   (t "root.api_browser")))))
       ($ Card
          ($ CardHeader
             ($ CardTitle  "DEV / DEBUG"))
          ($ CardContent
             ($ Typo {:variant "p" :class "text-muted-foreground mb-2"} "Root loader data")
             ($ :pre {:class-name "text-xs overflow-hidden text-ellipsis"}
                (.stringify js/JSON (cj data) nil 2)))))))
