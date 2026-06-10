(ns leihs.lending.server.middlewares.pool-id)

(defn wrap-pool-id [handler]
  (fn [request]
    (let [pool-id (get-in request [:parameters :path :pool-id])]
      (-> request
          (assoc :pool-id pool-id)
          handler))))
