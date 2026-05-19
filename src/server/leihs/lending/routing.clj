(ns leihs.lending.routing
  (:require
   [leihs.core.db :as db]
   [leihs.lending.routes :as routes]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]))

(defn wrap-catch [handler]
  (fn [req]
    (try (handler req)
         (catch clojure.lang.ExceptionInfo e
           {:status (or (-> e ex-data :status) 500)
            :body   (ex-message e)})
         (catch Exception _
           {:status 500 :body "Internal Server Error"}))))

(defn init []
  (-> (routes/handler)
      db/wrap-tx
      (wrap-json-body {:keywords? true})
      wrap-json-response
      wrap-catch
      wrap-content-type))
