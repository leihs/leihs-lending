(ns leihs.lending.graphiql
  (:require
   [hiccup2.core :as hiccup]
   [hiccup.util :refer [raw-string]]))

(def html
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
      [:script (raw-string "ReactDOM.createRoot(document.getElementById('graphiql')).render(
        React.createElement(GraphiQL, {fetcher: GraphiQL.createFetcher({url: '/lending/graphql'})})
      );")]]])))

(defn handler [_]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body html})
