(ns leihs.lending.server.mails
  (:require
   [leihs.core.languages :as lang]
   [leihs.core.mails :refer [get-tmpl log-mail-failure]]
   [leihs.core.settings :refer [settings]]
   [leihs.lending.server.resources.inventory-pools :as pools]
   [leihs.lending.server.resources.reservations :as res]
   [leihs.lending.server.resources.users :as users]
   [honey.sql.helpers :as sql]
   [next.jdbc :refer [execute!] :rename {execute! jdbc-execute!}]
   [honey.sql :refer [format] :rename {format sql-format}]
   [wet.core :as wet]))

(defn send-approved [tx order comment]
  (try
    (let [s (settings tx)
          user (users/get-by-id tx (:user_id order))
          lang-locale (:locale (lang/get-the-one-to-use tx (:id user)))
          pool (pools/get-by-id tx (:inventory_pool_id order))
          tmpl (get-tmpl tx "approved" (:id pool) lang-locale)]
      (cond
        (not pool) (log-mail-failure (:user_id order) (ex-info "Pool not found" {}))
        (not tmpl) (log-mail-failure (:user_id order)
                                     (ex-info (str "No 'approved' mail template for pool " (:id pool)) {}))
        :else (let [reservations (res/get-for-open-order-with-model-names tx (:id order))
                    email-body (wet/render
                                (wet/parse (:body tmpl))
                                {:params {:user user
                                          :inventory_pool pool
                                          :email_signature (:email_signature s)
                                          :reservations reservations
                                          :comment comment
                                          :purpose (:purpose order)
                                          :order_url (str (:external_base_url s)
                                                          "/manage/" (:id pool)
                                                          "/orders/" (:id order) "/edit")}
                                 :filters {}})
                    to-address (or (:email user) (:secondary_email user))
                    from-address (or (:email pool) (:smtp_default_from_address s))]
                (-> (sql/insert-into :emails)
                    (sql/values [{:user_id (:id user)
                                  :from_address from-address
                                  :to_address to-address
                                  :subject (:subject tmpl)
                                  :body email-body
                                  :template "approved"}])
                    sql-format
                    (->> (jdbc-execute! tx))))))
    (catch Exception e
      (log-mail-failure (:user_id order) e))))
