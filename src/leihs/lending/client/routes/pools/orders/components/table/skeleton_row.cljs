(ns leihs.lending.client.routes.pools.orders.components.table.skeleton-row
  (:require
   ["@@/skeleton" :refer [Skeleton]]
   ["@@/table" :refer [TableCell TableRow]]
   [uix.core :as uix :refer [$ defui]]))

(defui SkeletonRow []
  ($ TableRow {:class-name "shadow-[0_-0.5px_0_var(--border)] h-[53px]"}
     ($ TableCell {:class-name "w-[20%]"}
        ($ Skeleton {:class-name "w-40 h-6"}))

     ($ TableCell {:class-name "w-[10%]"}
        ($ Skeleton {:class-name "w-28 h-6"}))

     ($ TableCell {:class-name "w-[10%]"}
        ($ :div {:class-name "flex justify-center"}
           ($ Skeleton {:class-name "w-10 h-6"})))

     ($ TableCell {:class-name "w-[10%]"}
        ($ Skeleton {:class-name "w-16 h-6"}))

     ($ TableCell {:class-name "w-[24%]"}
        ($ Skeleton {:class-name "w-50 h-6"}))

     ($ TableCell {:class-name "w-[6%]"}
        ($ :div {:class-name "flex justify-center"}
           ($ Skeleton {:class-name "w-6 h-6"})))

     ($ TableCell {:class-name "w-[20%]"}
        ($ :div {:class-name "flex justify-end"}
           #_($ Skeleton {:class-name "w-[100%] h-6"})))))
