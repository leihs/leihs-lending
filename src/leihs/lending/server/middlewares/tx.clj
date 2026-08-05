(ns leihs.lending.server.middlewares.tx
  (:require
   [clojure.string :as str]
   [leihs.core.db :as db]
   [leihs.lending.server.graphql :as graphql]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [warn]]))

(defn- rollback-signal [resp reason]
  (ex-info "graphql tx rollback" {::response resp ::reason reason}))

(defn- with-graphql-tx [request handler]
  (try
    (jdbc/with-transaction+options [tx (db/get-ds)]
      (let [resp (-> request (assoc :tx tx) handler)
            errors (-> resp :body :errors seq)
            status (:status resp)]
        (cond
          errors
          (throw (rollback-signal resp (str "graphql error " errors)))

          (some-> status (>= 400))
          (throw (rollback-signal resp (str "error status " status)))

          :else resp)))
    (catch Throwable th
      (let [{::keys [response reason]} (ex-data th)]
        (warn "Rolling back transaction because of " (or reason (.getMessage th)))
        (or response (throw th))))))

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
