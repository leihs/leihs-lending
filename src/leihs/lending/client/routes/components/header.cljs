(ns leihs.lending.client.routes.components.header
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuGroup DropdownMenuItem
                               DropdownMenuLabel DropdownMenuPortal
                               DropdownMenuSeparator DropdownMenuSub
                               DropdownMenuSubContent DropdownMenuSubTrigger
                               DropdownMenuTrigger]]
   ["@@/input-group" :refer [InputGroup InputGroupAddon InputGroupInput]]
   ["lucide-react" :refer [ChevronsUpDown CircleUser LayoutGrid Search]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   ["sonner" :refer [toast]]
   ["~/i18n.config.js" :as i18n :refer [i18n]]
   [leihs.lending.client.lib.csrf :as csrf]
   [leihs.lending.client.lib.utils :refer [jc]]
   [leihs.lending.client.provider.theme-provider :refer [use-theme]]
   [leihs.core.core :refer [detect]]
   [uix.core :as uix :refer [$ defui]]))

(defui main [{:keys [currentUser activeLanguages appSettings]}]
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        {:keys [theme set-theme]} (use-theme)
        fetcher (router/useFetcher)
        last-fetcher-data (uix/use-ref nil)
        user (-> currentUser :user)
        available-pools (:availablePools user)
        user-name (str (:firstname user) " " (:lastname user))
        current-pool (->> available-pools (detect #(= pool-id (:id %))))
        current-lang (.. i18n -language)]

    (uix/use-effect
     (fn []
       (let [data (.-data fetcher)
             state (.-state fetcher)]
         (when (and (= state "idle")
                    (some? data)
                    (not= data @last-fetcher-data))
           (reset! last-fetcher-data data)
           (when (= (aget data "status") "error")
             (.. toast (error (t "error.action.error")
                              (clj->js {:description (t "error.action.error_detail"
                                                        #js {:httpStatus (aget data "httpStatus")})})))))))
     [fetcher t])

    ($ :header {:className "bg-background sticky z-50 top-0 flex items-center gap-4 border-b h-16"}
       ($ :nav {:className "container w-full flex flex-row justify-between text-sm items-center"}
          ($ :div {:className "flex items-center"}
             (let [logo-light (:logoLight appSettings)
                   logo-dark (:logoDark appSettings)
                   resolved-theme (if (= theme "system")
                                    (if (.-matches (.matchMedia js/window "(prefers-color-scheme: dark)"))
                                      "dark"
                                      "light")
                                    theme)
                   logo-src (if (= resolved-theme "dark")
                              (or logo-dark logo-light "/lending/assets/zhdk-logo.svg")
                              (or logo-light logo-dark "/lending/assets/zhdk-logo.svg"))
                   logo-type (cond
                               (and (= resolved-theme "light")
                                    (or logo-light logo-dark)) "Logo light"
                               (and (= resolved-theme "dark")
                                    (or logo-dark logo-light)) "Logo dark"
                               :else "Logo default")]

               ($ :img {:src logo-src
                        :className "max-h-16 py-2"
                        :alt logo-type
                        :data-test-id "app-logo"}))

             ($ :form {:action "/" :method "GET"}
                ($ InputGroup {:className "mx-12 w-fit"}
                   ($ InputGroupInput {:name "search_term"
                                       :placeholder (t "header.links.global-search" "Suche global")})
                   ($ InputGroupAddon
                      ($ Search))))
             ($ :div {:className "flex gap-6"}
                ($ :a {:href "/lending/"
                       :className "font-semibold"}
                   (t "header.links.lending" "Verleih"))
                ($ :a {:href "/inventory/"}
                   (t "header.links.inventory" "Inventar"))))

          ($ :div {:className "flex"}
             ($ DropdownMenu
                ($ DropdownMenuTrigger {:asChild "true" :className "ml-auto"}
                   ($ Button {:variant "outline"}
                      ($ :<>
                         ($ LayoutGrid {:className "h-4 w-4"})
                         ($ :span {:className "hidden lg:block"}
                            (if current-pool (:name current-pool) (t "header.app-menu.lending" "Verleih")))
                         ($ ChevronsUpDown {:className "h-4 w-4 hidden lg:block"}))))
                ($ DropdownMenuContent {:className "ml-auto" :data-test-id "app-menu"}
                   ($ DropdownMenuSeparator)
                   ($ DropdownMenuLabel {:className "text-xs font-normal"}
                      (t "header.app-menu.inventory-pools" "Geräteparks") ":")
                   ($ DropdownMenuGroup
                      (for [pool (sort-by :name available-pools)]
                        (let [url (router/generatePath "/lending/:pool-id/" #js {:pool-id (:id pool)})]
                          ($ DropdownMenuItem {:key (:id pool)
                                               :asChild true
                                               :className (when (= pool-id (:id pool)) "font-semibold")}
                             ($ :a {:href url} (:name pool))))))))

             ($ DropdownMenu
                ($ DropdownMenuTrigger {:asChild "true" :className "ml-4"}
                   ($ Button {:variant "outline"}
                      ($ :<>
                         ($ CircleUser {:className "h-4 w-4"})
                         ($ :span {:className "hidden lg:block"} user-name)
                         ($ ChevronsUpDown {:className "h-4 w-4 hidden lg:block"}))))
                ($ DropdownMenuContent {:className "ml-auto"}
                   ($ DropdownMenuGroup
                      (when-let [url (some-> (appSettings :externalBaseUrl)
                                             (str "/borrow/current-user"))]
                        ($ :<>
                           ($ DropdownMenuItem {:asChild true}
                              ($ :a {:href url} (t "header.user-menu.user-data")))
                           ($ DropdownMenuItem {:asChild true}
                              ($ :a {:href url} (t "header.user-menu.my-documents")))))
                      ($ DropdownMenuItem {:asChild true}
                         ($ :button {:type :submit
                                     :form "sign-out-form"
                                     :className "w-full"}
                            (t "header.user-menu.logout")

                            ($ :form {:action "/sign-out"
                                      :method :POST
                                      :id "sign-out-form"}
                               ($ :input {:type :hidden
                                          :name csrf/token-field-name
                                          :value csrf/token})))))

                   ($ DropdownMenuSeparator)
                   ($ DropdownMenuSub
                      ($ DropdownMenuSubTrigger
                         ($ :button {:type "button"
                                     :data-test-id "language-menu"}
                            (t "header.user-menu.language")))
                      ($ DropdownMenuPortal
                         ($ DropdownMenuSubContent
                            (for [language activeLanguages]
                              ($ DropdownMenuItem {:key (:locale language)
                                                   :asChild true}
                                 ($ :button {:type "submit"
                                             :form (str "form-" (:locale language))
                                             :data-test-id (if (= current-lang (:locale language)) "language-btn-selected" "language-btn")
                                             :class-name (str "w-full font-normal " (when (= current-lang (:locale language)) "font-semibold"))}
                                    (:name language)

                                    ($ fetcher.Form {:id (str "form-" (:locale language))
                                                     :method "PATCH"
                                                     :action "/profile"}
                                       ($ :input {:type "hidden"
                                                  :name "language"
                                                  :value (:locale language)}))))))))

                   ($ DropdownMenuSub
                      ($ DropdownMenuSubTrigger
                         ($ :button {:class-name "flex items-center gap-2"
                                     :type "button"}
                            (t "header.user-menu.theme.title")))
                      ($ DropdownMenuSubContent {:align "end"}
                         ($ DropdownMenuItem {:as-child true
                                              :onClick #(set-theme "light")}
                            ($ :button {:type "button"
                                        :class-name (str "w-full font-normal " (when (= theme "light") "font-semibold"))}
                               (t "header.user-menu.theme.light")))
                         ($ DropdownMenuItem {:as-child true
                                              :onClick #(set-theme "dark")}
                            ($ :button {:type "button"
                                        :class-name (str "w-full font-normal " (when (= theme "dark") "font-semibold"))}
                               (t "header.user-menu.theme.dark")))
                         ($ DropdownMenuItem {:as-child true
                                              :onClick #(set-theme "system")}
                            ($ :button {:type "button"
                                        :class-name (str "w-full font-normal " (when (= theme "system") "font-semibold"))}
                               (t "header.user-menu.theme.system"))))))))))))
