(ns leihs.lending.client.routes.debug.components.error-demos
  (:require
   ["@@/card" :refer [Card CardContent CardDescription CardHeader CardTitle]]
   ["@@/customized/button" :refer [Button]]
   ["react-router" :refer [Link]]
   [leihs.lending.client.lib.urql :as urql]
   [leihs.lending.client.provider.action-error-provider :refer [use-show-error]]
   [leihs.lending.client.routes.debug.data :as debug-data]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui ^:private error-trigger []
  (throw (js/Error. "Test error thrown during render!")))

(defui ^:private render-error-demo []
  (let [[error? set-error!] (uix/use-state false)]
    ($ :<>
       (when error?
         ($ error-trigger))
       ($ Button {:variant "destructive"
                  :on-click #(set-error! true)}
          "Trigger render error"))))

(defui ^:private action-error-demos []
  (let [show-error! (use-show-error)]
    ($ :<>
       ($ Button {:variant "destructive"
                  :on-click #(p/catch (urql/run-query urql/default-client "{ doesNotExist }" nil)
                                      show-error!)}
          "Failing query")
       (for [[kind label] debug-data/error-kinds]
         ($ Button {:key kind
                    :variant "destructive"
                    :on-click #(show-error! (debug-data/error kind))}
            label)))))

(defui ^:private link-button [{:keys [to children]}]
  ($ Button {:as-child true :variant "destructive"}
     ($ Link {:to to} children)))

(defui ^:private section [{:keys [title description children]}]
  ($ :div
     ($ :h3 {:class-name "font-semibold"} title)
     ($ :p {:class-name "text-sm text-muted-foreground mb-2"} description)
     ($ :div {:class-name "flex flex-wrap gap-2"} children)))

(defui main []
  ($ Card
     ($ CardHeader
        ($ CardTitle "Error boundary tests")
        ($ CardDescription "Test the display of page errors and action errors."))
     ($ CardContent {:class-name "space-y-6"}
        ($ section {:title "1. Render error"
                    :description "Throws an error while rendering a component."}
           ($ render-error-demo))

        ($ section {:title "2. Loader errors"
                    :description "Navigates to a route whose loader throws the given error."}
           (for [[kind label] debug-data/error-kinds]
             ($ link-button {:key kind :to (str "/lending/debug/error/" kind)}
                label)))

        ($ section {:title "3. Routing errors"
                    :description "Unknown route, and a pool the user has no access to."}
           ($ link-button {:to "/lending/foo/bar/fizz/buzz"} "Unknown route (404)")
           ($ link-button {:to "/lending/00000000-0000-0000-0000-000000000000/daily"}
              "Unknown pool (404)"))

        ($ section {:title "4. Action errors"
                    :description "Shows the error of a failing action in the dialog, without leaving the page."}
           ($ action-error-demos)))))
