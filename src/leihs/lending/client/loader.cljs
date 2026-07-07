(ns leihs.lending.client.loader
  (:require
   [leihs.lending.client.lib.utils :refer [jc]]
   [leihs.lending.client.routes.data :rename {loader root-data-loader}]
   [leihs.lending.client.routes.pools.visits.data :rename {list-loader visits-list-loader}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; React Router loaders. Each loader builds its own urql client targeting the
;; URL it needs (pool-scoped or not), runs the query, and lets the client
;; get GC'd. Loaders and <pool-provider> don't share clients — the loader's
;; result reaches components via useRouteLoaderData, not urql's cache.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-layout
  []
  (root-data-loader))

(defn visits-page
  [route-data]
  (let [{:keys [pool-id]} (jc ^js (.-params route-data))
        url (js/URL. (.. route-data -request -url))
        search-params (.-searchParams url)]
    (visits-list-loader pool-id search-params)))
