(ns leihs.lending.client.components.entities.model-image
  (:require
   ["@@/dialog" :refer [Dialog DialogContent DialogHeader DialogTitle DialogTrigger]]
   ["@@/spinner" :refer [Spinner]]
   ["lucide-react" :refer [ImageIcon]]
   ["react-i18next" :refer [useTranslation]]
   [uix.core :as uix :refer [$ defui]]))

(def ^:private thumbnail-class
  "min-w-12 h-12 object-contain rounded border p-1 bg-white")

(defui ^:private ImageDialog
  "Thumbnail opening a dialog with the full-size image.
   `use-image` is a hook `[model-id enabled?]` returning `{:data :ready? :error?}`"
  [{:keys [model use-image]}]
  (let [[t] (useTranslation)
        [requested? set-requested!] (uix/use-state false)
        {url :data ready? :ready? error? :error?} (use-image (:id model) requested?)]
    ($ Dialog {:on-open-change #(when % (set-requested! true))}
       ($ DialogTrigger {:asChild true}
          ($ :button {:type "button"
                      :class-name "cursor-zoom-in"
                      :data-test-id "model-image"}
             ($ :img {:src (:thumbnailUrl model)
                      :alt (:name model)
                      :loading "lazy"
                      :class-name thumbnail-class})))
       ($ DialogContent {:data-test-id "model-image-dialog"}
          ($ DialogHeader {:class-name "mr-8"}
             ($ DialogTitle (:name model)))
          ($ :div {:class-name "flex w-full max-h-[60vh] aspect-square items-center justify-center"}
             (cond
               (not ready?) ($ Spinner)
               (or error? (not url)) ($ :div {:class-name "text-error"} (t "common.load-error"))
               :else ($ :img {:src url
                              :alt (:name model)
                              :class-name "size-full object-contain"})))))))

(defui ModelImage
  "Thumbnail of a model, with a placeholder when it has no image. Given
   `use-image` (see ImageDialog), clicking it shows the full-size image."
  [{:keys [model use-image]}]
  (if-let [url (:thumbnailUrl model)]
    (if use-image
      ($ ImageDialog {:model model :use-image use-image})
      ($ :img {:src url
               :alt (:name model)
               :loading "lazy"
               :class-name thumbnail-class}))
    ($ :div {:class-name "flex min-w-12 h-12 justify-center items-center"}
       ($ ImageIcon {:class-name "size-10 stroke-1 text-foreground"}))))
