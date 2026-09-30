(ns leihs.lending.client.components.entities.items-popover
  (:require
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["@@/scroll-area" :refer [ScrollArea]]
   ["react-i18next" :refer [useTranslation]]
   [leihs.lending.client.lib.date-utils :refer [date-from-iso duration-days]]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :as uix :refer [$ defui]]))

(defn- item-name
  "Option reservations carry no model, so they fall back to the option's own
   name -- mirroring legacy's `Option#model` returning the option itself."
  [item]
  (or (get-in item [:model :name])
      (get-in item [:option :name])))

(defn- group-by-model
  "Sums quantities per model name within one date group, sorted alphabetically."
  [items]
  (->> items
       (group-by item-name)
       (map (fn [[model-name entries]]
              {:model-name model-name
               :quantity (reduce + (map #(or (:quantity %) 0) entries))}))
       (sort-by :model-name)))

(defn- group-by-date
  "Groups items primarily by reservation start/end date, then by model within
   each date group; date groups are sorted chronologically."
  [items]
  (->> items
       (group-by (juxt :startDate :endDate))
       (map (fn [[[start-date end-date] entries]]
              {:start-date start-date
               :end-date end-date
               :lines (group-by-model entries)}))
       (sort-by (juxt :start-date :end-date))))

(defui ItemsPopover
  "Items grouped primarily by reservation start/end date, then by model within
   each date group. Loading is injected: `use-items` is a hook `[id enabled?]`
   returning `{:data :ready? :error?}`. Opening the trigger starts the query; the
   popover itself only opens once the data is ready, so it never resizes under
   the cursor. A failed query shows a message rather than an empty list."
  [{:keys [id quantity use-items test-id]}]
  (let [[t] (useTranslation)
        [requested? set-requested!] (uix/use-state false)
        {items :data ready? :ready? error? :error?} (use-items id requested?)
        groups (group-by-date items)]
    ($ Popover {:open (and requested? ready?)
                :on-open-change set-requested!}
       ($ PopoverTrigger {:data-test-id test-id}
          ($ :span {:className "px-3"} quantity))
       ($ PopoverContent {:align "center" :className "w-[560px]"}
          ($ :div {:className "font-semibold text-base mb-4"}
             (t "components.entities.items-popover.title"))
          (if error?
            ($ :div {:className "text-sm text-destructive"}
               (t "common.load-error"))
            ($ ScrollArea {:class-name "max-h-100 overflow-y-auto w-full rounded-md border p-2"}
               (for [group groups]
                 ($ :div {:key (str (:start-date group) "_" (:end-date group))
                          :className "mt-2 mb-4 last:mb-0"}
                    ($ :div {:className "font-semibold text-sm mb-3"}
                       (t "components.entities.items-popover.date-range"
                          (cj {:startDate (t "common.date.formatDate" (cj {:val (date-from-iso (:start-date group))}))
                               :endDate (t "common.date.formatDate" (cj {:val (date-from-iso (:end-date group))}))
                               :count (duration-days (:start-date group) (:end-date group))})))
                    (for [line (:lines group)]
                      ($ :div {:key (:model-name line) :className "flex gap-5 text-sm mb-2"}
                         ($ :span {:className "w-6 text-right shrink-0"}
                            (:quantity line))
                         ($ :span (:model-name line))))))))))))
