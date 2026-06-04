(ns leihs.lending.server.ring
  (:require
   [leihs.core.anti-csrf.back :as anti-csrf]
   [leihs.core.auth.session :as session]
   [leihs.core.db :as db]
   [leihs.core.http-cache-buster2 :as cache-buster2]
   [leihs.core.ring-audits :as ring-audits]
   [leihs.core.ring-exception :as ring-exception]
   [leihs.core.routing.back :as routing]
   [leihs.core.routing.dispatch-content-type :refer [wrap-accept]]
   [leihs.lending.server.assets :as assets]
   [leihs.lending.server.authenticate :as authenticate]
   [leihs.lending.server.routes :as routes]
   [leihs.lending.server.spa :as spa]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]))

(defn init []
  (-> (routes/handler)
      ring-audits/wrap
      anti-csrf/wrap
      spa/wrap-dispatch-spa
      authenticate/wrap
      session/wrap-authenticate
      db/wrap-tx
      (wrap-json-body {:keywords? true})
      wrap-json-response
      routing/wrap-canonicalize-params-maps
      wrap-params
      wrap-cookies
      (cache-buster2/wrap-resource "public" assets/cache-bust-options)
      wrap-content-type
      wrap-accept
      ring-exception/wrap))
