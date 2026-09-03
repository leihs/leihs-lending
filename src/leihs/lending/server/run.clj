(ns leihs.lending.server.run
  (:refer-clojure :exclude [str keyword])
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.tools.cli :as cli :refer [parse-opts]]
   [leihs.core.core :refer [keyword str presence]]
   [leihs.core.db :as db]
   [leihs.core.http-server :as http-server]
   [leihs.core.shutdown :as shutdown]
   [leihs.core.status :as status]
   [leihs.lending.server.graphql :as graphql]
   [leihs.lending.server.ring :as ring]
   [logbug.catcher :as catcher]
   [taoensso.timbre :refer [info]]))

(defn run [options]
  (catcher/snatch
   {:return-fn (shutdown/run-return-fn options)}
   (info "Invoking run with options: " options)
   (shutdown/init options)
   (graphql/init options)
   (let [s (status/init)]
     (db/init options (:health-check-registry s)))
   (let [http-handler (ring/init)]
     (http-server/start options http-handler))))

(def cli-options
  (concat
   [["-h" "--help"]
    shutdown/pid-file-option]
   (http-server/cli-options :default-http-port 3270)
   db/cli-options))

(defn main-usage [options-summary & more]
  (->> ["leihs-lending"
        ""
        "usage: leihs-lending [<gopts>] run [<opts>] [<args>]"
        ""
        "Options:"
        options-summary
        ""
        (when more
          ["---"
           (with-out-str (pprint more))
           "---"])]
       flatten (clojure.string/join \newline)))

(defn main [gopts args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args cli-options :in-order true)
        options (merge gopts options)]
    (cond
      (:help options) (println (main-usage summary {:args args :options options}))
      :else (run options))))
