(ns leihs.lending.sign-in
  (:require
   [hiccup2.core :as hiccup]
   [leihs.core.auth.session :as session]
   [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME]]
   [leihs.core.sign-in.password-authentication.core :refer [password-checked-user]]
   [ring.util.response :refer [redirect set-cookie]]))

(def style
  "body{font-family:sans-serif;display:flex;flex-direction:column;align-items:center;padding-top:4rem;margin:0}
   h1{margin:0 0 0.25rem}
   p{margin:0 0 1.5rem;color:#666}
   form{display:flex;flex-direction:column;gap:1rem;width:280px}
   label{display:flex;flex-direction:column;gap:0.25rem;font-size:0.875rem;font-weight:500}
   input{padding:0.5rem;font-size:1rem;border:1px solid #ccc;border-radius:4px}
   button{padding:0.6rem;font-size:1rem;background:#1a73e8;color:#fff;border:none;border-radius:4px;cursor:pointer}
   button:hover{background:#1557b0}
   .error{background:#fce8e6;color:#c5221f;padding:0.75rem;border-radius:4px;font-size:0.875rem}")

(defn page [& {:keys [error?]}]
  (str
   "<!DOCTYPE html>"
   (hiccup/html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:title "Sign in — Lending"]
      [:style style]]
     [:body
      [:h1 "Lending Sign In"]
      [:p "For development and test only"]
      (when error? [:div.error "Invalid credentials"])
      [:form {:method "POST" :action "/lending/sign-in"}
       [:label "Email / Login"
        [:input {:type "text" :name "user" :autofocus true}]]
       [:label "Password"
        [:input {:type "password" :name "password"}]]
       [:button {:type "submit"} "Sign in"]]]])))

(defn get-handler [_]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (page)})

(defn post-handler [{:keys [params] :as request}]
  (let [user (password-checked-user (:user params) (:password params))]
    (if user
      (let [user-session (session/create-user-session user "password" request)]
        (-> (redirect "/lending/")
            (set-cookie USER_SESSION_COOKIE_NAME (:token user-session) {:path "/"})))
      {:status 200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body (page :error? true)})))
