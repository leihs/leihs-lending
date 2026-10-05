(ns leihs.lending.client.routes.pools.orders.components.approve-failed-dialog
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
   [leihs.lending.client.lib.utils :refer [jc]]
   [leihs.lending.client.routes.pools.orders.data :as data]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui ApproveFailedDialog
  "Shown when approving an order was refused by the backend; offers approving
   it anyway. `use-items` is a hook `[id enabled?]` loading the items once the
   dialog opens."
  [{:keys [order open? set-open! use-items]}]
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        revalidator (router/useRevalidator)
        {items :data ready? :ready? error? :error?} (use-items (:id order) open?)
        user (:user order)
        name (str (:firstname user) " " (:lastname user))
        [comment set-comment!] (uix/use-state "")
        [approving? set-approving!] (uix/use-state false)
        close! (fn [open?]
                 (set-open! open?)
                 (when-not open? (set-comment! "")))
        approve! (fn []
                   (set-approving! true)
                   (-> (data/approve-order! pool-id (:id order) true
                                            (not-empty (trim comment)))
                       (p/then (fn [_]
                                 (close! false)
                                 (.. toast (success (t "orders.approve-success")))
                                 (.revalidate revalidator)))
                       (p/catch (fn [_]
                                  (.. toast (error (t "error.action.error")))))
                       (p/finally (fn [_ _] (set-approving! false)))))]

    ($ Dialog {:open open? :on-open-change close!}
       ($ DialogContent {:class-name "sm:max-w-[780px]"
                         :data-test-id "approve-failed-dialog"}

          ($ DialogHeader
             ($ DialogTitle (t "orders.approve-failed-dialog.title"))
             ($ DialogDescription name))

          ($ :div {:class-name "grid gap-4"}

             ($ Alert {:variant "destructive"}
                ($ TriangleAlert)
                ($ AlertDescription (t "orders.approve-error")))

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
                             :rows 4
                             :data-test-id "approve-order-comment"
                             :value comment
                             :on-change #(set-comment! (.. % -target -value))})))

          ($ DialogFooter
             ($ DialogClose {:as-child true}
                ($ Button {:type "button" :variant "outline"}
                   (t "common.cancel")))
             ($ Button {:type "button"
                        :variant "outline"
                        :data-test-id "approve-order-edit"
                        :onClick #(.. toast (message (t "orders.actions.not-available")))}
                (t "orders.approve-failed-dialog.edit-order"))
             ($ Button {:type "button"
                        :disabled approving?
                        :data-test-id "approve-order-force"
                        :onClick approve!}
                (t "orders.approve-failed-dialog.force-approve")))))))
