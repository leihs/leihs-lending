(ns leihs.lending.client.routes.query)

(def query
  "{
      currentUser {
        id
        user {
          firstname
          lastname
          email
          login
          availablePools {
            id
            name
          }
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

