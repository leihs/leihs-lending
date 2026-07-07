(ns leihs.lending.client.routes.pools.daily.page
  (:require
   ["@@/card" :refer [Card CardContent CardDescription CardFooter CardHeader CardTitle]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :refer [useParams]]
   ["urql" :refer [useQuery]]
   [leihs.lending.client.lib.utils :refer [jc cj]]
   [leihs.lending.client.components.typo :refer [Typo]]
   [uix.core :as uix :refer [$ defui]]))

(def demo-query
  "{
      currentUser {
        id
        user {
          firstname
          lastname
          city
          zip
          isSuspended
        }
      }
    }")

(defui page []
  (let [[t] (useTranslation)
        params (useParams)
        pool-id (aget params "pool-id")
        data (-> (useQuery #js{:query demo-query}) first .-data)]
    ($ :div {:class-name "space-y-6"}
       ($ Card {:class-name "pb-0"}
          ($ CardHeader
             ($ CardTitle "DAILY")
             ($ CardDescription "TODO"))
          ($ CardContent
             "Lorem ipsum dolor sit amet")
          ($ CardFooter {:class-name "py-3 px-6 border-t-[1px] bg-muted/50 flex items-center justify-end"}
             ($ Typo {:variant "link"}
                ($ :a {:target "_blank"
                       :href (str "/lending/" pool-id "/graphiql")}
                   (t "pool.api_browser")))))

       ($ Card
          ($ CardHeader
             ($ CardTitle "DEV / DEBUG"))
          ($ CardContent

             ($ Typo {:variant :h3 :class-name "my-2"} "Params")
             ($ :pre {:class-name "text-xs"}
                (.stringify js/JSON params nil 2))

             ($ Typo {:variant :h3 :class-name "my-2"} "GraphQL demo query")
             ($ Typo {:variant :p :class-name "text-muted-foreground"}
                "Query from component with `useQuery`:")
             ($ :pre {:class-name "text-xs"}
                (.stringify js/JSON (cj data) nil 2))
             ($ Typo {:variant :p :class-name "text-muted-foreground"}
                "The client is provided by `pool-provider` and is rebuilt whenever pool-id changes."))))))