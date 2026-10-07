(ns leihs.lending.client.routes.pools.orders.edit.components.purpose-dialog
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dialog" :refer [Dialog DialogClose DialogContent DialogFooter
                        DialogHeader DialogTitle]]
   ["@@/form" :refer [Form FormControl FormField FormItem FormLabel
                      FormMessage]]
   ["@@/textarea" :refer [Textarea]]
   ["@hookform/resolvers/zod" :refer [zodResolver]]
   ["react" :as react]
   ["react-hook-form" :refer [useForm]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router]
   ["sonner" :refer [toast]]
   ["zod" :as z]
   [leihs.lending.client.lib.utils :refer [jc]]
   [leihs.lending.client.routes.pools.orders.edit.data :as data]
   [promesa.core :as p]
   [uix.core :as uix :refer [$ defui]]))

(defui PurposeDialog
  "Edits the purpose of an order."
  [{:keys [order open? set-open!]}]
  (let [[t] (useTranslation)
        {:keys [pool-id]} (jc (router/useParams))
        revalidator (router/useRevalidator)
        schema (uix/use-memo
                #(.object z #js {:purpose (-> (.string z)
                                              (.trim)
                                              (.min 1 (t "orders.purpose-dialog.purpose-required")))})
                [t])
        form (useForm #js {:resolver (zodResolver schema)
                           :defaultValues #js {:purpose ""}})
        purpose (or (:purpose order) "")
        save! (fn [^js values]
                (-> (data/update-purpose! pool-id (:id order) (.-purpose values))
                    (p/then (fn [_]
                              (set-open! false)
                              (.revalidate revalidator)))
                    (p/catch (fn [_]
                               (.. toast (error (t "error.action.error")))))))]

    (uix/use-effect
     (fn []
       (when open? (.reset form #js {:purpose purpose})))
     [open? purpose form])

    ($ Dialog {:open open? :on-open-change set-open!}
       ($ DialogContent {:class-name "sm:max-w-[640px]"
                         :data-test-id "order-purpose-dialog"}
          (react/createElement
           Form (js/Object.assign #js {} form)
           ($ :form {:class-name "grid gap-4"
                     :on-submit (.handleSubmit form save!)}

              ($ DialogHeader
                 ($ DialogTitle (t "orders.edit.edit-purpose")))

              ($ FormField
                 {:control (.-control form)
                  :name "purpose"
                  :render (fn [^js props]
                            (let [field (.-field props)]
                              ($ FormItem
                                 ($ FormLabel (t "orders.purpose-dialog.purpose"))
                                 ($ FormControl
                                    ($ Textarea {:class-name "min-h-40"
                                                 :data-test-id "order-purpose"
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
                            :data-test-id "order-purpose-submit"}
                    (t "common.save")))))))))
