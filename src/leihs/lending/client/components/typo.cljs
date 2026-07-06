(ns leihs.lending.client.components.typo
  (:require
   ["@@/utils" :refer [cn]]
   ["@radix-ui/react-slot" :refer [Slot]]
   [leihs.inventory.client.lib.utils :refer [cj jc]]
   [uix.core :as uix :refer [$ defui]]))

;; Variant classes mapping
(def variant-classes
  {:h1 "text-2xl font-bold mt-12 mb-2"
   :h2 "text-xl font-bold mt-8 mb-2"
   :h3 "text-lg"
   :h4 "font-semibold leading-none tracking-tight"
   :p "leading-7"
   :bold "font-bold"
   :link "text-primary underline underline-offset-4 hover:text-primary/80"
   :caption "text-xs text-muted-foreground"
   :label "text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
   :code "relative rounded bg-muted px-[0.3rem] py-[0.2rem] font-mono text-sm"
   :description "text-sm text-muted-foreground"})

;; Element type mapping
(def variant-element-map
  {:h1 :h1
   :h2 :h2
   :h3 :h3
   :p :p
   :bold :span
   :link :span
   :caption :span
   :label :span
   :code :code
   :description :p})

;; Main Typo component
(defui main
  [{:keys [className variant asChild ref]
    :or {variant :p
         asChild false}
    :as props}]

  (let [comp (if asChild
               Slot
               (get variant-element-map (keyword variant) :p))
        variant-class (get variant-classes (keyword variant) "leading-7")
        merged-class (cn variant-class className)
        ;; Remove our custom keys from props to pass through only valid HTML attributes
        other-props (dissoc props :className :variant :asChild)]

    ($ comp
       (merge other-props
              {:class-name merged-class
               :ref ref}))))

(def Typo
  (uix/as-react
   (fn [props]
     (main (cj props)))))
