(ns leihs.lending.client.components.entities.model-badge
  (:require
   ["@@/badge" :refer [Badge]]
   ["lucide-react" :refer [Package]]
   [uix.core :refer [$ defui]]))

(defui ModelBadge
  "Badge to indicate the type of model, copied from `inventory`"
  [{:keys [type]}]
  ($ :div {:class-name "flex gap-[2px] items-center"}
     ($ Badge {:class-name (str "w-6 h-5 justify-center shadow-none rounded-md "
                                (case type
                                  "Package" "bg-slate-500"
                                  "Model" "bg-slate-500"
                                  "Option" "bg-emerald-500"
                                  "Software" "bg-orange-500"))
               :data-test-id "type"}
        (str (case type
               "Package" "M"
               "Model" "M"
               "Option" "O"
               "Software" "S")))

     (when (= type "Package")
       ($ Package {:class-name "w-3 h-3"}))))