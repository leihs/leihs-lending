(ns leihs.lending.client.routes.pools.orders.edit.components.model-search-field
  (:require
   ["@@/combobox" :refer [Combobox ComboboxCollection ComboboxContent
                          ComboboxEmpty ComboboxGroup ComboboxInput
                          ComboboxItem ComboboxLabel ComboboxList]]
   ["@@/input-group" :refer [InputGroupAddon]]
   ["lucide-react" :refer [Search]]
   ["react-i18next" :refer [useTranslation]]
   [clojure.string :as str]
   [leihs.lending.client.components.entities.model-badge :refer [ModelBadge]]
   [leihs.lending.client.lib.hooks :as hooks]
   [leihs.lending.client.routes.pools.orders.edit.data :as data]
   [uix.core :as uix :refer [$ defui]]))

(defui ResultItem
  [{:keys [item]}]
  (let [[t] (useTranslation)
        {:keys [kind type isPackage availableQuantity
                availableTotalQuantity borrowableQuantity]} item
        unavailable? (and (= kind :model) (< availableQuantity 1))]
    ($ ComboboxItem {:value item
                     :class-name (str "items-start justify-between gap-4 pr-2"
                                      (when unavailable? " text-destructive"))
                     :data-test-id "model-search-item"}
       ($ :span {:class-name "font-medium"} (:name item))
       ($ :span {:class-name "flex shrink-0 flex-col items-end gap-1"}
          (if (= kind :model)
            ($ :<>
               ($ :span {:class-name "tabular-nums"}
                  ($ :span {:class-name "mr-1 rounded bg-amber-200 px-1 text-xs text-amber-900"}
                     "TODO")
                  (str availableQuantity "(" availableTotalQuantity ")/"
                       borrowableQuantity))
               ($ ModelBadge {:type (if isPackage "Package" type)}))
            ($ :span {:class-name "text-xs text-muted-foreground"}
               (t "orders.edit.search.template")))))))

(defui ModelSearchField
  "Search field offering models and templates as you type. `on-select` gets
   the chosen item (`:kind` is `:model` or `:template`)."
  [{:keys [on-select]}]
  (let [[t] (useTranslation)
        [term set-term!] (uix/use-state "")
        [open? set-open!] (uix/use-state false)
        [debounced-term] (hooks/use-debounce (str/trim term) 300)
        search? (not (str/blank? debounced-term))
        {:keys [data ready?]} (data/use-model-search debounced-term search?)
        searching? (or (not= (str/trim term) debounced-term) (not ready?))
        groups (uix/use-memo
                (fn []
                  (->> data
                       (remove (comp empty? :items))
                       (map (fn [{:keys [label items]}]
                              #js {:value label :items (into-array items)}))
                       into-array))
                [data])]
    ($ Combobox {:items groups
                 :filter nil
                 :value nil
                 :open (and open? (not (str/blank? term)))
                 :onOpenChange set-open!
                 :inputValue term
                 :onInputValueChange (fn [value ^js details]
                                       (when-not (= (.-reason details) "item-press")
                                         (set-term! value)))
                 :itemToStringLabel #(:name %)
                 :onValueChange (fn [item]
                                  (when item
                                    (set-term! "")
                                    (on-select item)))
                 :autoHighlight true}
       ($ ComboboxInput {:class-name "w-[300px]"
                         :showTrigger false
                         :placeholder (t "orders.edit.search-placeholder")
                         :data-test-id "new-reservation-term"}
          ($ InputGroupAddon ($ Search)))
       ($ ComboboxContent {:class-name "w-[400px]"}
          ($ ComboboxEmpty
             (if searching?
               (t "orders.edit.search.searching")
               (t "orders.edit.search.no-results")))
          ($ ComboboxList {:data-test-id "model-search-results"}
             (fn [^js group]
               ($ ComboboxGroup {:key (.-value group) :items (.-items group)}
                  ($ ComboboxLabel
                     (t (str "orders.edit.search." (name (.-value group)))))
                  ($ ComboboxCollection
                     (fn [item]
                       ($ ResultItem {:key (:id item) :item item}))))))))))
