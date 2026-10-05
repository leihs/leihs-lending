(ns leihs.lending.client.components.entities.items-popover
  (:require
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["react-i18next" :refer [useTranslation]]
   [leihs.lending.client.components.entities.items-list :refer [ItemsList]]
   [uix.core :as uix :refer [$ defui]]))

(defui ItemsPopover
  "Popover listing an order's items. `use-items` is a hook `[id enabled?]`
   started by the trigger; the popover opens once the data is ready."
  [{:keys [id quantity use-items test-id]}]
  (let [[t] (useTranslation)
        [requested? set-requested!] (uix/use-state false)
        {items :data ready? :ready? error? :error?} (use-items id requested?)]
    ($ Popover {:open (and requested? ready?)
                :on-open-change set-requested!}
       ($ PopoverTrigger {:data-test-id test-id}
          ($ :span {:className "px-3"} quantity))
       ($ PopoverContent {:align "center" :className "w-[560px]"}
          ($ :div {:className "font-semibold text-base mb-4"}
             (t "components.entities.items.title"))
          (if error?
            ($ :div {:className "text-sm text-destructive"}
               (t "common.load-error"))
            ($ ItemsList {:items items}))))))
