(ns leihs.lending.ring
  (:require
   [leihs.core.db :as db]
   [leihs.core.ring-exception :as ring-exception]
   [leihs.lending.routes :as routes]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]))

(defn init []
  (-> (routes/handler)
      db/wrap-tx
      (wrap-json-body {:keywords? true})
      wrap-json-response
      ring-exception/wrap
      wrap-content-type))
