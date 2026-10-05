(ns leihs.lending.client.routes.pools.orders.components.reject-dialog
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dialog" :refer [Dialog DialogClose DialogContent DialogDescription
                        DialogFooter DialogHeader DialogTitle]]
   ["@@/form" :refer [Form FormControl FormField FormItem FormLabel
                      FormMessage]]
   ["@@/spinner" :refer [Spinner]]
   ["@@/textarea" :refer [Textarea]]
   ["@hookform/resolvers/zod" :refer [zodResolver]]
   ["react" :as react]
   ["react-hook-form" :refer [useForm]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   ["sonner" :refer [toast]]
   ["zod" :as z]
   [clojure.string :refer [blank?]]
   [leihs.lending.client.components.entities.items-list :refer [ItemsList]]
   [leihs.lending.client.lib.utils :refer [jc]]
   [leihs.lending.client.routes.pools.orders.data :as data]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui RejectDialog
  "Confirms rejecting a submitted order.
   `use-items` is a hook `[id enabled?]` loading the items once the dialog opens."
  [{:keys [order open? set-open! use-items]}]
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        revalidator (router/useRevalidator)
        schema (uix/use-memo
                #(.object z #js {:reason (-> (.string z)
                                             (.trim)
                                             (.min 1 (t "orders.reject-dialog.comment-required")))})
                [t])
        form (useForm #js {:resolver (zodResolver schema)
                           :defaultValues #js {:reason ""}})
        {items :data ready? :ready? error? :error?} (use-items (:id order) open?)
        user (:user order)
        name (str (:firstname user) " " (:lastname user))
        purpose (:purpose order)
        close! (fn [open?]
                 (set-open! open?)
                 (when-not open? (.reset form)))
        reject! (fn [^js values]
                  (-> (data/reject-order! pool-id (:id order) (.-reason values))
                      (p/then (fn [_]
                                (close! false)
                                (.revalidate revalidator)))
                      (p/catch (fn [_]
                                 (.. toast (error (t "error.action.error")))))))]

    ($ Dialog {:open open? :on-open-change close!}
       ($ DialogContent {:class-name "sm:max-w-[780px]"
                         :data-test-id "reject-order-dialog"}
          (react/createElement
           Form (js/Object.assign #js {} form)
           ($ :form {:class-name "grid gap-4"
                     :on-submit (.handleSubmit form reject!)}

              ($ DialogHeader
                 ($ DialogTitle (t "orders.reject-dialog.title"))
                 ($ DialogDescription name))

              ($ :div
                 ($ :div {:class-name "font-semibold text-sm mb-1"}
                    (t "orders.table.project-title"))
                 ($ :p {:class-name "text-sm whitespace-pre-wrap"}
                    (if (blank? purpose) "—" purpose)))

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

              ($ FormField
                 {:control (.-control form)
                  :name "reason"
                  :render (fn [^js props]
                            (let [field (.-field props)]
                              ($ FormItem
                                 ($ FormLabel (t "orders.reject-dialog.comment"))
                                 ($ FormControl
                                    ($ Textarea {:rows 4
                                                 :data-test-id "reject-order-reason"
                                                 :name (.-name field)
                                                 :value (.-value field)
                                                 :ref (.-ref field)
                                                 :on-blur (.-onBlur field)
                                                 :on-change (.-onChange field)}))
                                 ($ FormMessage))))})

              ($ DialogFooter
                 ($ DialogClose {:as-child true}
                    ($ Button {:type "button" :variant "outline"}
                       (t "common.cancel")))
                 ($ Button {:type "submit"
                            :disabled (.. form -formState -isSubmitting)
                            :data-test-id "reject-order-submit"}
                    (t "orders.actions.reject")))))))))
