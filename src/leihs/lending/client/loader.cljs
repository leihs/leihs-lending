(ns leihs.lending.client.loader
  (:require
   [leihs.lending.client.lib.urql :as urql]
   [leihs.lending.client.lib.utils :refer [jc cj]]
   [promesa.core :as p]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; React Router loaders. Each loader builds its own urql client targeting the
;; URL it needs (pool-scoped or not), runs the query, and lets the client
;; get GC'd. Loaders and <pool-provider> don't share clients — the loader's
;; result reaches components via useRouteLoaderData, not urql's cache.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- run-query
  "Runs a urql query on the given client and resolves to a clj map of the
   response data. Throws the GraphQL/network error if one occurred so React
   Router routes it to the errorElement."
  [client query variables]
  (p/let [^js source (.query client query (cj (or variables {})))
          result (.toPromise source)]
    (when-let [error (.-error result)]
      (throw error))
    (jc (.-data result))))

(def current-user-query
  "query CurrentUser {
     currentUser {
       id
       user {
         firstname
         lastname
         email
         login
       }
     }
   }")

(defn root-layout
  "Loader for /lending/app — fetches the signed-in user."
  []
  (js/Promise.
   (fn [resolve _reject]
     (-> (run-query (urql/make-client "/lending/graphql") current-user-query nil)
         (p/then resolve)
         (p/catch (fn [_]
                    (.assign js/window.location "/lending/sign-in")))))))

(def pool-context-query
  ;; Placeholder — replace with whatever data the pool subtree actually needs.
  "query PoolContext { __typename }")

(defn pool-layout
  "Loader for /lending/app/:pool-id — builds a pool-scoped client on the
   fly and runs the pool query."
  [route-data]
  (let [params ^js (.-params route-data)
        pool-id (aget params "pool-id")
        client (urql/make-client (str "/lending/" pool-id "/graphql"))]
    (run-query client pool-context-query nil)))
