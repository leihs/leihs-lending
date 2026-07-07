(ns leihs.lending.server.resources.reminders
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn get-multiple
  [{{tx :tx _pool-id :pool-id} :request} _ {user-id :user-id  date :date _visit-id :id}]
  (-> (sql/select :emails.id :emails.created_at :emails.subject :emails.template)
      (sql/from :emails)
      (sql/where [:= :emails.user_id user-id])

      ;; future filter , see below (1)
      ;;(sql/join [:emails_visits :ev] [:= :ev.email_id :emails.id])
      ;; ...
      ;; (sql/where [:= :emails.source_pool_id _pool-id])
      ;; (sql/where [:= :ev.visit_id _visit-id])
      ;; (sql/where [:in :emails.template ["reminder" "deadline_soon_reminder"]])

      ;; legacy filter (shows too much emails, but consistent with Leihs Legacy)
      (sql/where [:>= :created_at date])

      (sql/order-by [:emails.created_at :desc])
      sql-format
      (->> (jdbc-query tx))))

;; (1) Filters to be implemented after in a future change, see https://github.com/leihs/leihs/issues/2217
;;     Currently we show all emails sent after the visit date, even if they are not
;;     related to the visit (same behaviour as in Legacy Lending).
