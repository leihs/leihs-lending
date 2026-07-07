(ns leihs.lending.client.routes.pools.visits.page
  (:require
   ["@@/card" :refer [Card CardContent CardFooter CardHeader]]
   ["@@/table" :refer [Table TableBody TableHead TableHeader TableRow]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   [leihs.lending.client.components.pagination :as pagination]
   [leihs.lending.client.routes.pools.visits.components.filters.date-filter :refer [DateFilter]]
   [leihs.lending.client.routes.pools.visits.components.filters.reset :refer [Reset]]
   [leihs.lending.client.routes.pools.visits.components.filters.term-filter :refer [TermFilter]]
   [leihs.lending.client.routes.pools.visits.components.filters.verification-filter :refer [VerificationFilter]]
   [leihs.lending.client.routes.pools.visits.components.filters.visit-type-filter :refer [VisitTypeFilter]]
   [leihs.lending.client.routes.pools.visits.components.table.skeleton-row :refer [SkeletonRow]]
   [leihs.lending.client.routes.pools.visits.components.table.visit-row :refer [VisitRow]]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  (let [[t] (useTranslation)
        {:keys [visits page per-page total-count]} (router/useLoaderData)
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
             ($ DateFilter {:param "startDate" :title (t "visits.filters.start-date")})
             ($ DateFilter {:param "endDate" :title (t "visits.filters.end-date")})
             ($ VisitTypeFilter)
             ($ VerificationFilter)
             ($ Reset {:on-reset handle-reset})))

       ($ CardContent
          ($ :div {:className "border rounded-md overflow-hidden"
                   :aria-busy (when loading? "true")}
             (if (empty? visits)
               ($ :div {:class-name "p-4 text-center text-sm text-muted-foreground"}
                  (t "visits.table.empty"))
               ($ :div {:class-name "overflow-x-auto"}
                  ($ Table
                     ($ TableHeader {:class-name "rounded-t-md"}
                        ($ TableRow {:class-name "rounded-t-md hover:bg-background border-b"}
                           ($ TableHead {:className "w-[26%] text-muted-foreground"}
                              (t "visits.table.name"))
                           ($ TableHead {:className "w-[9%] text-muted-foreground"}
                              (t "visits.table.date"))
                           ($ TableHead {:className "w-[15%] text-muted-foreground text-center"}
                              (t "visits.table.quantity"))
                           ($ TableHead {:className "w-[10%] text-muted-foreground"}
                              (t "visits.table.duration"))
                           ($ TableHead {:className "w-[20%] text-muted-foreground"}
                              (t "visits.table.notifications"))
                           ($ TableHead {:className "w-[20%] text-muted-foreground"}
                              "")))
                     ($ TableBody {:className "[&_tr:last-child]:border-l-4"}
                        (if loading?
                          (for [i (range skeleton-size)]
                            ($ SkeletonRow {:key i}))

                          (for [visit visits]
                            ($ VisitRow {:key (:id visit) :visit visit})))))))))

       ($ CardFooter {:class-name "sticky bottom-0 bg-background z-10 rounded-b-xl py-6"
                      :style {:background "linear-gradient(to top, var(--background) 80%, transparent 100%)"}}
          ($ pagination/main {:pagination pagination
                              :class-name "justify-start w-full"})))))
