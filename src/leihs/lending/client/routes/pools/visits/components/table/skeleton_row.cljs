(ns leihs.lending.client.routes.pools.visits.components.table.skeleton-row
  (:require
   ["@@/skeleton" :refer [Skeleton]]
   ["@@/table" :refer [TableCell TableRow]]
   [uix.core :as uix :refer [$ defui]]))

(defui SkeletonRow []
  ($ TableRow {:class-name "shadow-[0_-0.5px_0_var(--border)] h-[53px]"}
     ($ TableCell {:class-name "w-[26%]"}
        ($ Skeleton {:class-name "w-50 h-6"}))

     ($ TableCell {:class-name "w-[9%]"}
        ($ Skeleton {:class-name "w-25 h-6"}))

     ($ TableCell {:class-name "w-[15%]"}
        ($ :div {:class-name "flex justify-center"}
           ($ Skeleton {:class-name "w-10 h-6"})))

     ($ TableCell {:class-name "w-[10%]"}
        ($ Skeleton {:class-name "w-25 h-6"}))

     ($ TableCell {:class-name "w-[20%]"}
        ($ Skeleton {:class-name "w-50 h-6"}))

     ($ TableCell {:class-name "w-[20%]"}
        ($ :div {:class-name "flex justify-end"}
           ($ Skeleton {:class-name "w-50 h-6"})))))

