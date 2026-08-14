(ns leihs.lending.client.routes.pools.orders.page
  (:require
   ["@@/card" :refer [Card CardContent CardFooter CardHeader]]
   ["@@/table" :refer [Table TableBody TableHead TableHeader TableRow]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   [leihs.lending.client.components.filters.date-filter :refer [DateFilter]]
   [leihs.lending.client.components.filters.reset :refer [Reset]]
   [leihs.lending.client.components.filters.select-filter :refer [SelectFilter]]
   [leihs.lending.client.components.filters.term-filter :refer [TermFilter]]
   [leihs.lending.client.components.pagination :as pagination]
   [leihs.lending.client.routes.pools.orders.components.table.order-row :refer [OrderRow]]
   [leihs.lending.client.routes.pools.orders.components.table.skeleton-row :refer [SkeletonRow]]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  (let [[t] (useTranslation)
        {:keys [orders page per-page total-count]} (router/useLoaderData)
        pagination {:page-size per-page :page page :total-rows total-count}
        total-pages (js/Math.ceil (/ total-count per-page))
        skeleton-size (if (= page total-pages)
                        (let [mod-result (mod total-count per-page)]
                          (if (zero? mod-result)
                            per-page
                            mod-result))
                        per-page)

        navigation (router/useNavigation)
        loading? (= (.-state navigation) "loading")
        navigate (router/useNavigate)
        handle-reset (fn [] (navigate (str "?page=1&size=" per-page)))]

    ($ Card {:class-name "pb-0 gap-0"}
       ($ CardHeader {:className "flex bg-background rounded-xl z-10 sticky top-16 mb-6"
                      :style {:background "linear-gradient(to bottom, var(--background) 90%, transparent 100%)"}}
          ($ :div {:className "flex gap-2"}
             ($ TermFilter)
             ($ DateFilter {:param "startDate" :title (t "orders.filters.start-date")})
             ($ DateFilter {:param "endDate" :title (t "orders.filters.end-date")})
             ($ SelectFilter {:class-name "w-[200px]"
                              :param "states"
                              :items [{:value nil :test-id "all" :content (t "orders.filters.all-states")}
                                      {:value "SUBMITTED" :test-id "SUBMITTED"  :content (t "orders.state.submitted")}
                                      {:value "APPROVED" :test-id "APPROVED" :content (t "orders.state.approved")}
                                      {:value "REJECTED" :test-id "REJECTED" :content (t "orders.state.rejected")}]})
             ($ SelectFilter {:class-name "w-[200px]"
                              :param "toBeVerified"
                              :items [{:value nil :test-id "all" :content (t "orders.filters.verification-all")}
                                      {:value "true" :test-id "true"  :content (t "orders.filters.to-be-verified")}
                                      {:value "false" :test-id "false" :content (t "orders.filters.not-to-be-verified")}]})
             ($ Reset {:on-reset handle-reset})))

       ($ CardContent
          ($ :div {:className "border rounded-md overflow-hidden"
                   :aria-busy (when loading? "true")}
             (if (empty? orders)
               ($ :div {:class-name "p-4 text-center text-sm text-muted-foreground"}
                  (t "orders.table.empty"))
               ($ :div {:class-name "overflow-x-auto"}
                  ($ Table
                     ($ TableHeader {:class-name "rounded-t-md"}
                        ($ TableRow {:class-name "rounded-t-md hover:bg-background border-b"}
                           ($ TableHead {:className "w-[20%] text-muted-foreground"}
                              (t "orders.table.name"))
                           ($ TableHead {:className "w-[10%] text-muted-foreground"}
                              (t "orders.table.date"))
                           ($ TableHead {:className "w-[10%] text-muted-foreground text-center"}
                              (t "orders.table.quantity"))
                           ($ TableHead {:className "w-[10%] text-muted-foreground"}
                              (t "orders.table.duration"))
                           ($ TableHead {:className "w-[24%] text-muted-foreground"}
                              (t "orders.table.project-title"))
                           ($ TableHead {:className "w-[6%] text-muted-foreground text-center"}
                              (t "orders.table.status"))
                           ($ TableHead {:className "w-[20%] text-muted-foreground"}
                              "")))
                     ($ TableBody {:className "[&_tr:last-child]:border-l-4"}
                        (if loading?
                          (for [i (range skeleton-size)]
                            ($ SkeletonRow {:key i}))

                          (for [order orders]
                            ($ OrderRow {:key (:id order) :order order})))))))))

       ($ CardFooter {:class-name "sticky bottom-0 bg-background z-10 rounded-b-xl py-6"
                      :style {:background "linear-gradient(to top, var(--background) 80%, transparent 100%)"}}
          ($ pagination/main {:pagination pagination
                              :class-name "justify-start w-full"})))))
