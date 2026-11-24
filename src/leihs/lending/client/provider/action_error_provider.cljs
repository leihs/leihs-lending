(ns leihs.lending.client.provider.action-error-provider
  (:require
   ["@@/alert-dialog" :refer [AlertDialog AlertDialogAction AlertDialogCancel]]
   ["react" :as react]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :refer [useRevalidator]]
   [leihs.lending.client.components.error-dialog :refer [error-dialog-content]]
   [leihs.lending.client.lib.errors :as errors]
   [uix.core :as uix :refer [$ defui]]))

(def ^:private context (react/createContext nil))

(defui ^:private action-error-dialog [{:keys [error open? on-open-change]}]
  (let [[t] (useTranslation)
        revalidator (useRevalidator)
        type (some-> error errors/error-type)]
    ($ AlertDialog {:open open? :on-open-change on-open-change}
       (when error
         ($ error-dialog-content {:error error :scope "error.action"}
            (case type
              :not-found
              ($ :<>
                 ($ AlertDialogCancel (t "error.boundary.actions.close"))
                 ($ AlertDialogAction {:on-click #(.revalidate revalidator)}
                    (t "error.action.reload")))

              :unauthorized
              ($ :<>
                 ($ AlertDialogCancel (t "error.boundary.actions.close"))
                 ($ AlertDialogAction {:as-child true}
                    ($ :a {:href "/lending/sign-in"}
                       (t "error.boundary.actions.go_signin"))))

              ($ AlertDialogAction (t "error.boundary.actions.close"))))))))

(defui action-error-provider
  "Provides `use-show-error` to its children and shows the errors passed to it
   in a modal dialog."
  [{:keys [children]}]
  (let [[{:keys [error open?]} set-state!] (uix/use-state {:error nil :open? false})
        show-error! (uix/use-callback
                     (fn [error]
                       (js/console.error error)
                       (set-state! {:error error :open? true}))
                     [])]
    ($ (.-Provider context) {:value show-error!}
       children
       ($ action-error-dialog {:error error
                               :open? open?
                               :on-open-change #(set-state! (fn [s] (assoc s :open? %)))}))))

(defn use-show-error
  "Function showing a failed action's error in the error dialog."
  []
  (or (react/useContext context)
      (throw (js/Error. "use-show-error must be used within an action-error-provider"))))
