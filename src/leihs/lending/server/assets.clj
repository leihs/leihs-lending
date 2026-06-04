(ns leihs.lending.server.assets)

(def cache-bust-options
  {:cache-bust-paths ["/lending/assets/css/style.css"
                      "/lending/assets/js/main.js"
                      "/lending/assets/js/libs.js"]
   :never-expire-paths [#".+_[0-9a-f]{40}\..+"]
   :cache-enabled? true})
