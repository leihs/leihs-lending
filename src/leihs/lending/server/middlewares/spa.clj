(ns leihs.lending.server.middlewares.spa
  (:require
   [hiccup.page :refer [html5 include-css include-js]]
   [leihs.core.http-cache-buster2 :as cache-buster]))

(defn spa-handler [_request]
  {:headers {"Content-Type" "text/html"}
   :body (html5
          [:head
           [:meta {:charset "utf-8"}]
           [:title "Lending"]
           (include-css (cache-buster/cache-busted-path "/lending/assets/css/style.css"))]
          [:body
           [:div#app]
           (include-js (cache-buster/cache-busted-path "/lending/assets/js/libs.js"))
           (include-js (cache-buster/cache-busted-path "/lending/assets/js/main.js"))])})

(def ^:private no-spa-uris #{"/lending/sign-in" "/lending/graphiql"})

(def ^:private no-spa-patterns [#"^/lending/assets/"
                                #"/lending/[^/]+/graphiql$"])

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
