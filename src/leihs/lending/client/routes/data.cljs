(ns leihs.lending.client.routes.data
  (:require
   ["~/i18n.config.js" :default i18n]
   [leihs.lending.client.lib.urql :refer [default-client run-query]]
   [promesa.core :as p]))

(def query
  "{
      currentUser {
        id
        availablePools {
          id
          name
        }
        availableSubApps {
          key
          url
        }
        user {
          firstname
          lastname
          email
          login
          languageLocale
        }
        languageToUse {
          locale
        }
      }
      activeLanguages {
        name
        locale
        default
      }
      appSettings {
        logoDark
        logoLight
        externalBaseUrl
        localCurrencyString
      }
    }")

(defn loader
  []
  (js/Promise.
   (fn [resolve _reject]
     (-> (run-query default-client query nil)
         (p/then (fn [data]
                   (when-let [locale (get-in data [:currentUser :languageToUse :locale])]
                     (.changeLanguage i18n locale))
                   (resolve data)))
         (p/catch (fn [_]
                    (.assign js/window.location "/lending/sign-in")))))))
