(ns leihs.lending.client.components.entities.items-list
  (:require
   ["@@/scroll-area" :refer [ScrollArea]]
   ["react-i18next" :refer [useTranslation]]
   [leihs.lending.client.lib.date-utils :refer [date-from-iso duration-days]]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :refer [$ defui]]))

(defn- item-name
  [item]
  (or (get-in item [:model :name])
      (get-in item [:option :name])))

(defn- group-by-model
  "Sums quantities per model name, sorted alphabetically."
  [items]
  (->> items
       (group-by item-name)
       (map (fn [[model-name entries]]
              {:model-name model-name
               :quantity (reduce + (map #(or (:quantity %) 0) entries))}))
       (sort-by :model-name)))

(defn- group-by-date
  "Groups items by start/end date in chronological order, then by model."
  [items]
  (->> items
       (group-by (juxt :startDate :endDate))
       (map (fn [[[start-date end-date] entries]]
              {:start-date start-date
               :end-date end-date
               :lines (group-by-model entries)}))
       (sort-by (juxt :start-date :end-date))))

(defui ItemsList
  "Scrollable list of an order's reservations, grouped by start/end date and
   then by model. The caller renders the heading."
  [{:keys [items]}]
  (let [[t] (useTranslation)
        groups (group-by-date items)]
    ($ ScrollArea {:class-name "max-h-100 overflow-y-auto w-full rounded-md border p-2"}
       (for [group groups]
         ($ :div {:key (str (:start-date group) "_" (:end-date group))
                  :className "mt-2 mb-4 last:mb-0"}
            ($ :div {:className "font-semibold text-sm mb-3"}
               (t "components.entities.items.date-range"
                  (cj {:startDate (t "common.date.formatDate" (cj {:val (date-from-iso (:start-date group))}))
                       :endDate (t "common.date.formatDate" (cj {:val (date-from-iso (:end-date group))}))
                       :count (duration-days (:start-date group) (:end-date group))})))
            (for [line (:lines group)]
              ($ :div {:key (:model-name line) :className "flex gap-5 text-sm mb-2"}
                 ($ :span {:className "w-6 text-right shrink-0"}
                    (:quantity line))
                 ($ :span (:model-name line)))))))))
