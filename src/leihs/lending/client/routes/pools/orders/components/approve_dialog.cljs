(ns leihs.lending.client.routes.pools.orders.components.approve-dialog
  (:require
   ["@@/alert" :refer [Alert AlertDescription]]
   ["@@/button" :refer [Button]]
   ["@@/dialog" :refer [Dialog DialogClose DialogContent DialogDescription
                        DialogFooter DialogHeader DialogTitle]]
   ["@@/label" :refer [Label]]
   ["@@/spinner" :refer [Spinner]]
   ["@@/textarea" :refer [Textarea]]
   ["lucide-react" :refer [TriangleAlert]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   ["sonner" :refer [toast]]
   [clojure.string :refer [trim]]
   [leihs.lending.client.components.entities.items-list :refer [ItemsList]]
   [leihs.lending.client.lib.urql :as urql]
   [leihs.lending.client.lib.utils :refer [jc]]
   [leihs.lending.client.routes.pools.orders.components.styles :refer [approve-class]]
   [leihs.lending.client.routes.pools.orders.data :as data]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui ApproveDialog
  [{:keys [order open? set-open! use-items failed? on-edit on-success]}]
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        revalidator (router/useRevalidator)
        {items :data ready? :ready? error? :error?} (use-items (:id order) open?)
        user (:user order)
        name (str (:firstname user) " " (:lastname user))
        [comment set-comment!] (uix/use-state "")
        [approving? set-approving!] (uix/use-state false)
        [refused? set-refused!] (uix/use-state false)
        force? (boolean (or failed? refused?))
        close! (fn [open?]
                 (set-open! open?)
                 (when-not open?
                   (set-comment! "")
                   (set-refused! false)))
        approve! (fn []
                   (set-approving! true)
                   (-> (data/approve-order! pool-id (:id order) force?
                                            (not-empty (trim comment)))
                       (p/then (fn [_]
                                 (close! false)
                                 (.. toast (success (t "orders.approve-success")))
                                 (if on-success
                                   (on-success)
                                   (.revalidate revalidator))))
                       (p/catch (fn [error]
                                  (if (and (not force?)
                                           (= 422 (urql/error-status error)))
                                    (set-refused! true)
                                    (.. toast (error (t "error.action.error"))))))
                       (p/finally (fn [_ _] (set-approving! false)))))]

    ($ Dialog {:open open? :on-open-change close!}
       ($ DialogContent {:class-name "sm:max-w-[780px]"
                         :data-test-id "approve-order-dialog"}

          ($ DialogHeader
             ($ DialogTitle (if force?
                              (t "orders.approve-failed-dialog.title")
                              (t "orders.approve-dialog.title")))
             ($ DialogDescription name))

          ($ :div {:class-name "grid gap-4"}

             (when force?
               ($ Alert {:variant "destructive"}
                  ($ TriangleAlert)
                  ($ AlertDescription (t "orders.approve-error"))))

             ($ :div
                ($ :div {:class-name "font-semibold text-sm mb-2"}
                   (t "components.entities.items.title"))
                (cond
                  error?
                  ($ :div {:class-name "text-sm text-destructive"}
                     (t "common.load-error"))

                  (not ready?)
                  ($ :div {:class-name "flex justify-center rounded-md border p-4"
                           :aria-busy true}
                     ($ Spinner))

                  :else
                  ($ ItemsList {:items items})))

             ($ :div {:class-name "grid gap-2"}
                ($ Label {:html-for "approve-comment"}
                   (t "orders.approve-failed-dialog.comment"))
                ($ Textarea {:id "approve-comment"
                             :class-name "min-h-24"
                             :data-test-id "approve-order-comment"
                             :value comment
                             :on-change #(set-comment! (.. % -target -value))})))

          ($ DialogFooter
             ($ DialogClose {:as-child true}
                ($ Button {:type "button" :variant "outline"}
                   (t "common.cancel")))
             (when (and force? on-edit)
               ($ Button {:type "button"
                          :variant "outline"
                          :data-test-id "approve-order-edit"
                          :onClick on-edit}
                  (t "orders.approve-failed-dialog.edit-order")))
             ($ Button {:type "button"
                        :class-name approve-class
                        :disabled approving?
                        :data-test-id "approve-order-submit"
                        :onClick approve!}
                (if force?
                  (t "orders.approve-failed-dialog.force-approve")
                  (t "orders.approve-dialog.approve"))))))))
