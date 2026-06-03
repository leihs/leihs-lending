(ns leihs.lending.client.routes.pools.page
  (:require
   ["@@/card" :refer [Card CardContent CardDescription CardHeader CardTitle]]
   ["react-router" :refer [useParams]]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  (let [params (useParams)
        pool-id (aget params "pool-id")]
    ($ :div {:class-name "space-y-6"}
       ($ :div
          ($ :h1 {:class-name "text-3xl font-bold tracking-tight"} "Pool")
          ($ :p {:class-name "text-muted-foreground font-mono text-sm"} pool-id))

       ($ Card
          ($ CardHeader
             ($ CardTitle "Pool-scoped urql client")
             ($ CardDescription
                (str "All GraphQL queries inside this route hit "
                     "/lending/" pool-id "/graphql")))
          ($ CardContent
             ($ :p {:class-name "text-sm text-muted-foreground"}
                "Add a useQuery here — the client is provided by pool-provider "
                "and is rebuilt whenever pool-id changes."))))))
