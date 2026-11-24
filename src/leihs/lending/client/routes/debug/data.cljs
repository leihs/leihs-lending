(ns leihs.lending.client.routes.debug.data
  (:require
   ["@urql/core" :refer [CombinedError]]))

(def error-kinds
  "Kinds of test errors with their labels, in display order."
  [["400" "400 Bad request"]
   ["401" "401 Unauthorized"]
   ["403" "403 Forbidden"]
   ["404" "404 Not found"]
   ["500" "500 Server error"]
   ["graphql-404" "GraphQL error (code 404)"]
   ["network" "Network error"]
   ["exception" "Exception"]])

(defn error
  "Test error of the given kind, as thrown by a failing request or code."
  [kind]
  (case kind
    "network" (CombinedError. #js {:networkError (js/Error. "Failed to fetch")})
    "graphql-404" (CombinedError. #js {:graphQLErrors #js [#js {:message "Not found"
                                                                :extensions #js {:code 404}}]})
    "exception" (js/Error. "Test error")
    (js/Response. nil #js {:status (js/parseInt kind)})))
