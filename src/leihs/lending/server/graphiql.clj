(ns leihs.lending.server.graphiql
  (:require
   [hiccup2.core :as hiccup]
   [hiccup.util :refer [raw-string]]
   [leihs.core.constants :refer [ANTI_CSRF_TOKEN_COOKIE_NAME ANTI_CSRF_TOKEN_HEADER_NAME]]))

(defn- html [graphql-url csrf-token]
  (str
   "<!DOCTYPE html>"
   (hiccup/html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:title "GraphiQL — Lending"]
      [:style "body{height:100%;margin:0;width:100%;overflow:hidden}#graphiql{height:100vh}"]
      [:link {:rel "stylesheet" :href "https://unpkg.com/graphiql@3/graphiql.min.css"}]]
     [:body
      [:div#graphiql "Loading..."]
      [:script {:crossorigin true :src "https://unpkg.com/react@18/umd/react.production.min.js"}]
      [:script {:crossorigin true :src "https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"}]
      [:script {:src "https://unpkg.com/graphiql@3/graphiql.min.js"}]
      [:script (raw-string (str "ReactDOM.createRoot(document.getElementById('graphiql')).render(
        React.createElement(GraphiQL, {fetcher: GraphiQL.createFetcher({
          url: '" graphql-url "',
          headers: {'" ANTI_CSRF_TOKEN_HEADER_NAME "': '" csrf-token "'}
        })})
      );"))]]])))

(defn handler [{pool-id :pool-id cookies :cookies}]
  (let [csrf-token (get-in cookies [ANTI_CSRF_TOKEN_COOKIE_NAME :value])]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (html (str "/lending/" pool-id "/graphql") csrf-token)}))

(defn root-handler [{cookies :cookies}]
  (let [csrf-token (get-in cookies [ANTI_CSRF_TOKEN_COOKIE_NAME :value])]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (html "/lending/graphql" csrf-token)}))
