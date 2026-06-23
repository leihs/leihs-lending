(ns leihs.lending.server.root-graphql.queries
  (:require
   [leihs.lending.server.resources.app-settings :as app-settings]
   [leihs.lending.server.resources.inventory-pools :as inventory-pools]
   [leihs.lending.server.resources.languages :as languages]
   [leihs.lending.server.resources.users :as users]))

(def resolvers
  {:current-user    users/get-current
   :available-pools inventory-pools/get-available-pools
   :active-languages languages/get-multiple
   :app-settings    app-settings/get-app-settings})
