(ns leihs.lending.server.middlewares.tx
  (:require
   [clojure.string :as str]
   [leihs.core.db :as db]
   [leihs.lending.server.graphql :as graphql]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [warn]]))

(defn- with-graphql-tx [request handler]
  (jdbc/with-transaction+options [tx (db/get-ds)]
    (letfn [(rollback-tx! [] (.rollback (:connectable tx)))]
      (try (let [resp (-> request (assoc :tx tx) handler)
                 resp-body (:body resp)
                 resp-status (:status resp)]
             (cond (:graphql-error resp)
                   (do (warn "Rolling back transaction because of graphql error " (:errors resp-body))
                       (rollback-tx!))
                   (some-> resp-status (>= 400))
                   (do (warn "Rolling back transaction because error status " resp-status)
                       (rollback-tx!)))
             resp)
           (catch Throwable th
             (warn "Rolling back transaction because of " (.getMessage th))
             (rollback-tx!)
             (throw th))))))

(defn- graphql-tx [request handler mutation?]
  (if mutation?
    (with-graphql-tx request handler)
    (-> request (assoc :tx (db/get-ds)) handler)))

(defn wrap
  "Lending owns transaction handling for its two graphql endpoints instead of
  relying on shared-clj's `db/wrap-tx`, which unconditionally checks graphql
  mutations against a single shared schema atom that only ever holds the
  pool schema — wrong for the root endpoint's own schema."
  [handler]
  (let [shared-wrapped (db/wrap-tx handler)]
    (fn [{:keys [uri] :as request}]
      (cond
        (= uri "/lending/graphql")
        (graphql-tx request handler (graphql/mutation? :root request))

        (str/ends-with? uri "/graphql")
        (graphql-tx request handler (graphql/mutation? :pool request))

        :else (shared-wrapped request)))))
