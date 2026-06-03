(ns leihs.lending.client.main
  (:require
   ["react-router" :refer [RouterProvider]]
   ["urql" :refer [Provider]]
   [leihs.lending.client.lib.urql :refer [default-client]]
   [leihs.lending.client.routes :refer [routes]]
   [uix.core :as uix :refer [$ defui]]
   [uix.dom]))

(defui app []
  ($ uix/strict-mode
     ($ Provider {:value default-client}
        ($ RouterProvider {:router routes}))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "app")))

(defn render []
  (uix.dom/render-root ($ app) root))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn ^:dev/after-load on-after-load []
  (render))

(defn ^:export init []
  (render))
