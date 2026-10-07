(ns leihs.lending.client.components.entities.model-image
  (:require
   ["lucide-react" :refer [ImageOff]]
   [uix.core :refer [$ defui]]))

(defui ModelImage
  "Thumbnail of a model, with a placeholder when it has no image."
  [{:keys [model]}]
  (if-let [url (:thumbnailUrl model)]
    ($ :img {:src url
             :alt (:name model)
             :loading "lazy"
             :class-name "min-w-12 h-12 object-contain rounded border p-1 bg-white"})
    ($ :div {:class-name "flex min-w-12 h-12 justify-center items-center rounded border p-2"}
       ($ ImageOff {:class-name "size-6 text-border"}))))
