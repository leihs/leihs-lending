(ns leihs.lending.client.components.pagination
  (:require
   ["@@/button" :refer [Button]]
   ["@@/dropdown-menu" :refer [DropdownMenu DropdownMenuContent
                               DropdownMenuRadioGroup DropdownMenuRadioItem
                               DropdownMenuTrigger]]
   ["@@/customized/pagination" :refer [Pagination PaginationContent PaginationEllipsis
                                       PaginationItem PaginationLink PaginationNext
                                       PaginationPrevious]]
   ["lucide-react" :refer [ChevronDown ChevronLeft ChevronRight]]
   ["react-i18next" :refer [useTranslation]]
   ["react-router" :as router :refer [Link]]
   [uix.core :as uix :refer [$ defui]]))

(defn page-range [current size total-rows]
  (let [start (* (- current 1) size)
        end (+ start size)
        last-page-number (js/Math.ceil (/ total-rows size))]

    (if (= current last-page-number)
      {:start (+ start 1)
       :end total-rows}

      {:start (+ start 1)
       :end end})))

(defui main [{:keys [pagination class-name]}]
  (let [ref-next (uix/use-ref nil)
        ref-prev (uix/use-ref nil)
        [t] (useTranslation)
        location (router/useLocation)
        [search-params set-search-params!] (router/useSearchParams)
        page-size (or (:page-size pagination) 10)
        total-rows (or (:total-rows pagination) 0)
        total-pages (if (pos? page-size)
                      (js/Math.ceil (/ total-rows page-size))
                      0)
        current-page (or (:page pagination) 0)

        page-range (if pagination
                     (page-range current-page page-size total-rows)
                     {:start 0 :end 0})

        next-page (if (not= current-page
                            total-pages)
                    (inc current-page)
                    nil)

        prev-page (if (not= current-page 1)
                    (dec current-page)
                    nil)

        gen-page-str (fn [number]
                       (.. search-params (set "page" number))
                       (.. search-params (toString)))
        gen-page-url (fn [number]
                       (str (.. location -pathname) "?" (gen-page-str number)))

        handle-size-change (fn [value]
                             (.. search-params (set "size" value))
                             (.. search-params (set "page" 1))
                             (set-search-params! search-params))]

    (uix/use-effect
     (fn []
       (let [on-key-down
             (fn [e]
               (when (and (= (.. e -code) "ArrowRight")
                          (.-altKey e)
                          (.-shiftKey e)
                          (not (.-ctrlKey e))
                          (not (.-metaKey e)))
                 (.preventDefault e)
                 (when ref-next
                   (when-let [input-element @ref-next]
                     (.. input-element (click)))))

               (when (and (= (.. e -code) "ArrowLeft")
                          (.-altKey e)
                          (.-shiftKey e)
                          (not (.-ctrlKey e))
                          (not (.-metaKey e)))
                 (.preventDefault e)
                 (when ref-prev
                   (when-let [input-element @ref-prev]
                     (.. input-element (click))))))]

         (js/window.addEventListener "keydown" on-key-down)
         (fn [] (js/window.removeEventListener "keydown" on-key-down))))
     [])

    ($ :div {:data-test-id "pagination-container"
             :class-name (str "flex " class-name)}
       ($ Pagination {:class-name "overflow-hidden justify-start w-fit mx-0 pr-6"
                      :data-test-id "pagination"}

          ;; previous link
          ($ PaginationPrevious {:as-child true
                                 :ref ref-prev
                                 :data-test-id "pagination-previous"
                                 :text (t "pagination.previous")}
             (if (> prev-page 0)
               ($ Link {:to (gen-page-url prev-page)
                        :viewTransition true})
               ($ Button {:variant "link" :disabled true})))
          ($ PaginationContent
             ;; first page when current page is greater than 2 
             (when (> current-page 2)
               ($ :<>
                  ($ PaginationItem
                     ($ PaginationLink {:as-child true
                                        :data-test-id "pagination-first-page"}
                        ($ Link {:to (gen-page-url 1)
                                 :viewTransition true}
                           "1")))
                  ($ PaginationEllipsis)))

             ;; previous link
             (when (> prev-page 0)
               ($ PaginationItem
                  ($ PaginationLink {:as-child true
                                     :data-test-id "pagination-previous-page"}
                     ($ Link {:to (gen-page-url prev-page)
                              :viewTransition true}
                        prev-page))))

             ;; current active page
             ($ PaginationItem
                ($ PaginationLink {:as-child true
                                   :data-test-id "pagination-current-page"
                                   :is-active true}
                   ($ Link {:to (gen-page-url current-page)
                            :viewTransition true}
                      current-page)))

             ;; next page when not last page
             ;; ellipsis between next page and last page, when not last page
             (when (< current-page (- total-pages 1))
               ($ :<>
                  ($ PaginationItem
                     ($ PaginationLink {:as-child true
                                        :data-test-id "pagination-next-page"}
                        ($ Link {:to (gen-page-url next-page)
                                 :viewTransition true}
                           next-page)))
                  ($ PaginationEllipsis)))

             ;; last page
             (when (and (not= current-page total-pages)
                        (> total-rows 0))
               ($ PaginationItem
                  ($ PaginationLink {:as-child true
                                     :data-test-id "pagination-last-page"}
                     ($ Link {:to (gen-page-url total-pages)
                              :viewTransition true}
                        total-pages)))))

          ;; next link 
          ($ PaginationNext {:as-child true
                             :ref ref-next
                             :data-test-id "pagination-next"
                             :text (t "pagination.next")}
             (if (and next-page (> total-rows 0))
               ($ Link {:to (gen-page-url next-page)
                        :viewTransition true})
               ($ Button {:variant "link" :disabled true}))))

       ($ :div {:class-name "items-center hidden sm:flex"}
          ($ :span {:data-test-id "pagination-range"
                    :class-name "text-muted-foreground text-sm mr-2"
                    ;; random key fixes a strange Safari repaint issue
                    :key (js/Math.random)}
             (t "pagination.range" #js {:range (str (:start page-range) "-" (:end page-range))
                                        :total total-rows})))

       ($ :div {:class-name "flex items-center ml-auto"}

          ($ :span {:class-name "mr-2 hidden md:inline"}
             (t "pagination.per-page"))

          ($ DropdownMenu
             ($ DropdownMenuTrigger {:asChild "true"}
                ($ Button {:data-test-id "pagination-size-button"
                           :variant "outline"}
                   page-size ($ ChevronDown {:class-name "ml-1 h-4 w-4"})))

             ($ DropdownMenuContent {:data-test-id "pagination-size-dropdown"
                                     :align "start"}
                ($ DropdownMenuRadioGroup {:value page-size
                                           :onValueChange handle-size-change}
                   ($ DropdownMenuRadioItem {:value 10}
                      ($ :button {:type "button"} "10"))
                   ($ DropdownMenuRadioItem {:value 20}
                      ($ :button {:type "button"} "20"))
                   ($ DropdownMenuRadioItem {:value 50}
                      ($ :button {:type "button"} "50"))
                   ($ DropdownMenuRadioItem {:value 100}
                      ($ :button {:type "button"} "100")))))))))
