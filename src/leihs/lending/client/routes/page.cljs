(ns leihs.lending.client.routes.page
  (:require
   ["@@/button" :refer [Button]]
   ["@@/card" :refer [Card CardContent CardDescription CardHeader CardTitle]]
   ["react-router" :refer [useRouteLoaderData]]
   [uix.core :as uix :refer [$ defui]]))

(defui page []
  (let [data (useRouteLoaderData "root")
        user (get-in data [:currentUser :user])]
    ($ :div {:class-name "space-y-6"}
       ($ :div
          ($ :h1 {:class-name "text-3xl font-bold tracking-tight"} "Lending")
          ($ :p {:class-name "text-muted-foreground"} "Management interface"))

       ($ Card
          ($ CardHeader
             ($ CardTitle "Signed in user")
             ($ CardDescription "Loaded via the root route's loader"))
          ($ CardContent
             ($ :dl {:class-name "grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm"}
                ($ :dt {:class-name "text-muted-foreground"} "Name")
                ($ :dd (str (:firstname user) " " (:lastname user)))
                ($ :dt {:class-name "text-muted-foreground"} "Email")
                ($ :dd (:email user))
                ($ :dt {:class-name "text-muted-foreground"} "Login")
                ($ :dd (:login user)))))

       ($ :div {:class-name "flex gap-3"}
          ($ Button {:as-child true :variant "default"}
             ($ :a {:href "/lending/graphiql"} "Open GraphiQL"))
          ($ Button {:as-child true :variant "outline"}
             ($ :a {:href "/lending/"} "Back to server home"))

          ($ Button {:type :submit
                     :form "sign-out-form"}
             "Sign out"

             ($ :form {:action "/lending/sign-out"
                       :method :POST
                       :id "sign-out-form"}
                ($ :input {:type :hidden
                           :name csrf/token-field-name
                           :value csrf/token})))))))
