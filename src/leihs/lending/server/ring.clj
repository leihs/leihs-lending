(ns leihs.lending.server.ring
  (:require
   [leihs.core.anti-csrf.back :as anti-csrf]
   [leihs.core.auth.session :as session]
   [leihs.core.db :as db]
   [leihs.core.ring-audits :as ring-audits]
   [leihs.core.ring-exception :as ring-exception]
   [leihs.core.routing.back :as routing]
   [leihs.lending.server.authenticate :as authenticate]
   [leihs.lending.server.routes :as routes]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]))

(defn init []
  (-> (routes/handler)
      ring-audits/wrap
      anti-csrf/wrap
      authenticate/wrap
      session/wrap-authenticate
      db/wrap-tx
      (wrap-json-body {:keywords? true})
      wrap-json-response
      routing/wrap-canonicalize-params-maps
      wrap-params
      wrap-cookies
      wrap-content-type
      ring-exception/wrap))
