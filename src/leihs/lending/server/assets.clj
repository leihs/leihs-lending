(ns leihs.lending.server.assets)

(def cache-bust-options
  {:cache-bust-paths [#"^/lending/assets/.*\.(js|css)$"]
   :never-expire-paths []
   :cache-enabled? true})
