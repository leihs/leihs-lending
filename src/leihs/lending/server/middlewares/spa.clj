(ns leihs.lending.server.middlewares.spa
  (:require
   [hiccup.page :refer [html5 include-css include-js]]))

(defn spa-handler [_request]
  {:headers {"Content-Type" "text/html"}
   :body (html5
          [:head
           [:meta {:charset "utf-8"}]
           [:title "Lending"]
           (include-css "/lending/assets/css/style.css")]
          [:body
           [:div#app]
           (include-js "/lending/assets/js/libs.js")
           (include-js "/lending/assets/js/main.js")])})

(def ^:private no-spa-uris #{"/lending/sign-in" "/lending/graphiql"})

(def ^:private no-spa-patterns [#"^/lending/assets/"
                                #"/lending/[^/]+/graphiql$"
                                #"^/lending/[^/]+/contracts/[^/]+$"])

(defn- spa-uri? [uri]
  (and (not (no-spa-uris uri))
       (not (some #(re-find % (or uri "")) no-spa-patterns))))

(defn wrap-dispatch-spa [handler]
  (fn [request]
    (if (and (= (-> request :accept :mime) :html)
             (#{:get :head} (:request-method request))
             (spa-uri? (:uri request)))
      (spa-handler request)
      (handler request))))
