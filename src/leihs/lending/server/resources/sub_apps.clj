(ns leihs.lending.server.resources.sub-apps
  (:require
   [leihs.core.remote-navbar.shared :refer [sub-apps]]))

(defn get-available-sub-apps [{{tx :tx :keys [authenticated-entity]} :request} _ _]
  (when-let [apps (sub-apps tx authenticated-entity)]
    (cond-> []
      (:borrow apps)       (conj {:key "borrow"  :url "/borrow/"})
      (:admin apps)        (conj {:key "admin"   :url "/admin/"})
      (:procure apps)      (conj {:key "procure" :url "/procure/"})
      (seq (:manage apps)) (conj {:key "lending" :url "/lending/"}
                                 {:key "inventory" :url "/inventory/"}))))
