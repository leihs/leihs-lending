(ns leihs.lending.client.components.typo
  (:require
   ["@@/utils" :refer [cn]]
   ["@radix-ui/react-slot" :refer [Slot]]
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
(defui Typo
  [{:keys [class-name variant as-child ref]
    :or {variant :p
         as-child false}
    :as props}]

  (let [comp (if as-child
               Slot
               (get variant-element-map (keyword variant) :p))
        variant-class (get variant-classes (keyword variant) "leading-7")
        merged-class (cn variant-class class-name)
        ;; Remove our custom keys from props to pass through only valid HTML attributes
        other-props (dissoc props :className :variant :as-child)]

    ($ comp
       (merge other-props
              {:class-name merged-class
               :ref ref}))))
