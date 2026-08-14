(ns leihs.lending.server.root-graphql.queries
  (:require
   [leihs.lending.server.resources.app-settings :as app-settings]
   [leihs.lending.server.resources.inventory-pools :as inventory-pools]
   [leihs.lending.server.resources.languages :as languages]
   [leihs.lending.server.resources.sub-apps :as sub-apps]
   [leihs.lending.server.resources.users :as users]))

(def resolvers
  {:current-user       users/get-current
   :available-pools    inventory-pools/get-available-pools
   :available-sub-apps sub-apps/get-available-sub-apps
   :active-languages   languages/get-multiple
   :language-to-use    languages/one-to-use
   :app-settings       app-settings/get-app-settings})
