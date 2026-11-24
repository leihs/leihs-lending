(ns leihs.lending.client.routes.error
  (:require
   ["@@/alert-dialog" :refer [AlertDialog AlertDialogAction AlertDialogCancel
                              AlertDialogTrigger]]
   ["@@/customized/button" :refer [Button]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :refer [Link useRevalidator useRouteError]]
   [leihs.lending.client.components.error-dialog :refer [error-dialog-content]]
   [leihs.lending.client.lib.errors :as errors]
   [uix.core :as uix :refer [$ defui]]))

(def ^:private retryable? #{:network :server-error :application-error :unknown})

(defui page
  "Error boundary of a route: shows the thrown error in a modal dialog, which can
   be reopened after closing it."
  []
  (let [[t] (useTranslation)
        error (useRouteError)
        revalidator (useRevalidator)
        type (errors/error-type error)
        [open? set-open!] (uix/use-state true)]

    (uix/use-effect #(set-open! true) [error])

    ($ AlertDialog {:open open? :on-open-change set-open!}

       ($ AlertDialogTrigger {:as-child true}
          ($ Button {:variant "destructive"
                     :class-name "fixed right-4 bottom-4 z-10"}
             (t "error.boundary.open")))

       ($ error-dialog-content {:error error :scope "error.boundary"}
          (when (retryable? type)
            ($ AlertDialogCancel {:on-click #(.revalidate revalidator)}
               (t "error.boundary.actions.retry")))

          ($ AlertDialogCancel
             (t "error.boundary.actions.close"))

          (if (= type :unauthorized)
            ($ AlertDialogAction {:as-child true}
               ($ :a {:href "/lending/sign-in"}
                  (t "error.boundary.actions.go_signin")))
            ($ AlertDialogAction {:as-child true}
               ($ Link {:to "/lending/"}
                  (t "error.boundary.actions.go_home"))))))))
