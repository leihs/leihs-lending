(ns leihs.lending.client.lib.errors
  (:require
   [leihs.lending.client.lib.urql :as urql]))

(def ^:private code->status
  {"UNAUTHENTICATED" 401
   "FORBIDDEN" 403})

(defn status
  "HTTP status of a thrown error, nil if it has none. Understands router error
   responses, thrown `Response`s and urql errors (the `code` the backend attached
   to the GraphQL error, or the status of the failed request)."
  [^js error]
  (let [code (urql/error-status error)
        ^js response (some-> error .-response)]
    (or (some-> error .-status)
        (if (number? code) code (code->status code))
        (when (and response (not (.-ok response)))
          (.-status response)))))

(defn error-type
  "Classifies a thrown error for display, as one of `:unauthorized`,
   `:forbidden`, `:not-found`, `:bad-request`, `:server-error`, `:network`,
   `:application-error` or `:unknown`."
  [^js error]
  (let [s (status error)]
    (cond
      (= s 401) :unauthorized
      (= s 403) :forbidden
      (= s 404) :not-found
      (and s (<= 400 s 499)) :bad-request
      (and s (>= s 500)) :server-error
      (some-> error .-networkError) :network
      (instance? js/Error error) :application-error
      :else :unknown)))
