(ns leihs.lending.routes
  (:require
   [leihs.lending.graphql :as graphql]
   [reitit.ring :as reitit-ring]))

(def graphiql-html
  "<!DOCTYPE html>
<html>
<head>
  <meta charset=\"utf-8\"/>
  <title>GraphiQL &#x2014; Lending</title>
  <style>body{height:100%;margin:0;width:100%;overflow:hidden}#graphiql{height:100vh}</style>
  <link rel=\"stylesheet\" href=\"https://unpkg.com/graphiql@3/graphiql.min.css\"/>
</head>
<body>
  <div id=\"graphiql\">Loading...</div>
  <script crossorigin src=\"https://unpkg.com/react@18/umd/react.production.min.js\"></script>
  <script crossorigin src=\"https://unpkg.com/react-dom@18/umd/react-dom.production.min.js\"></script>
  <script src=\"https://unpkg.com/graphiql@3/graphiql.min.js\"></script>
  <script>
    ReactDOM.createRoot(document.getElementById('graphiql')).render(
      React.createElement(GraphiQL, {fetcher: GraphiQL.createFetcher({url: '/lending/graphql'})})
    );
  </script>
</body>
</html>")

(def routes
  [["/lending/graphiql"
    {:get {:handler (fn [_] {:status 200
                             :headers {"Content-Type" "text/html; charset=utf-8"}
                             :body graphiql-html})}}]
   ["/lending/graphql"
    {:post {:handler graphql/handler}}]])

(defn handler []
  (reitit-ring/ring-handler
   (reitit-ring/router routes)
   (reitit-ring/create-default-handler)))
