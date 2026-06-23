(ns leihs.lending.server.resources.app-settings
  (:require
   [leihs.core.settings :refer [settings]]))

(defn get-app-settings [{{tx :tx} :request} _ _]
  (let [s (settings tx [:external_base_url
                        :logo_light :logo_dark
                        :local_currency_string :time_zone
                        :lending_terms_url :documentation_link])]
    {:external-base-url (:external_base_url s)
     :logo-light (:logo_light s)
     :logo-dark (:logo_dark s)
     :local-currency-string (:local_currency_string s)
     :time-zone (:time_zone s)
     :lending-terms-url (:lending_terms_url s)
     :documentation-link (:documentation_link s)}))
