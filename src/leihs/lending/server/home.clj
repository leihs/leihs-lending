(ns leihs.lending.server.home
  (:require
   [hiccup2.core :as hiccup]
   [leihs.core.anti-csrf.back :as anti-csrf]
   [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME ANTI_CSRF_TOKEN_FORM_PARAM_NAME]]
   [next.jdbc.sql :refer [delete!] :rename {delete! jdbc-delete!}]
   [ring.util.response :refer [redirect set-cookie]]))

(def style
  "body{font-family:sans-serif;max-width:600px;margin:3rem auto;padding:0 1rem}
   h1{margin:0 0 0.25rem}
   .subtitle{color:#666;margin:0 0 2rem}
   .user-card{background:#f8f9fa;border:1px solid #e0e0e0;border-radius:8px;padding:1.5rem;margin-bottom:2rem}
   .user-card dt{font-size:0.8rem;color:#666;margin-top:0.75rem}
   .user-card dd{margin:0.1rem 0 0;font-weight:500}
   .actions{display:flex;gap:1rem;align-items:center}
   a.button{padding:0.5rem 1rem;background:#1a73e8;color:#fff;text-decoration:none;border-radius:4px;font-size:0.9rem}
   a.button:hover{background:#1557b0}
   form button{padding:0.5rem 1rem;background:#fff;color:#c5221f;border:1px solid #c5221f;border-radius:4px;font-size:0.9rem;cursor:pointer}
   form button:hover{background:#fce8e6}")

(defn handler [{:keys [authenticated-entity] :as request}]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str
          "<!DOCTYPE html>"
          (hiccup/html
           [:html
            [:head
             [:meta {:charset "utf-8"}]
             [:title "Lending"]
             [:style style]]
            [:body
             [:h1 "Lending"]
             [:p.subtitle "Management interface"]
             [:div.user-card
              [:dl
               [:dt "Name"]
               [:dd (str (:firstname authenticated-entity) " " (:lastname authenticated-entity))]
               [:dt "Email"]
               [:dd (:email authenticated-entity)]
               [:dt "Login"]
               [:dd (:login authenticated-entity)]]]
             [:div.actions
              [:a.button {:href "/lending/graphiql"} "Open GraphiQL"]
              [:form {:method "POST" :action "/lending/sign-out"}
               [:input {:type "hidden" :name ANTI_CSRF_TOKEN_FORM_PARAM_NAME :value (anti-csrf/anti-csrf-token request)}]
               [:button {:type "submit"} "Sign out"]]]]]))})

(defn sign-out-handler [{:keys [tx authenticated-entity]}]
  (when-let [session-id (:user_session_id authenticated-entity)]
    (jdbc-delete! tx :user_sessions ["id = ?" session-id]))
  (-> (redirect "/")
      (set-cookie USER_SESSION_COOKIE_NAME "" {:path "/" :max-age 0})))
