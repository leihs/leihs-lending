(ns leihs.lending.client.lib.urql
  (:require
   ["urql" :refer [createClient fetchExchange]]
   [leihs.lending.client.lib.csrf :as csrf]
   [leihs.lending.client.lib.utils :refer [cj]]))

(defn- fetch-options []
  (let [headers (cond-> {"Content-Type" "application/json"}
                  csrf/token
                  (assoc csrf/header-field-name csrf/token))]
    (cj {:credentials "same-origin"
         :method "POST"
         :headers headers})))

(defn make-client
  "Builds a urql client targeting `url`. Use the pool-scoped form
   (e.g. `/lending/<pool-id>/graphql`) inside <pool-provider>; use the
   no-pool form for routes that aren't pool-scoped."
  [url]
  (createClient
   (cj {:url url
        :fetchOptions fetch-options
        :preferGetMethod false
        :exchanges [fetchExchange]})))

(def default-client
  "Client for routes outside any pool (sign-in flow, pool picker, profile)."
  (make-client "/lending/graphql"))
