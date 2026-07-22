(ns leihs.lending.server.resources.reminders
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn get-multiple
  [{{tx :tx pool-id :pool-id} :request} _ {user-id :user-id visit-id :id}]
  (-> (sql/select :emails.id :emails.created_at :emails.subject :emails.template)
      (sql/from :emails)
      (sql/join [:emails_visits :ev] [:= :ev.email_id :emails.id])
      (sql/where [:= :emails.user_id user-id])
      (sql/where [:= :emails.source_pool_id pool-id])
      (sql/where [:= :ev.visit_id visit-id])
      (sql/where [:in :emails.template ["reminder" "deadline_soon_reminder"]])
      (sql/order-by [:emails.created_at :desc])
      sql-format
      (->> (jdbc-query tx))))
