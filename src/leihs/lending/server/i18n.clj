(ns leihs.lending.server.i18n
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ^:private translations
  (-> "i18n/translations.edn" io/resource slurp edn/read-string))

(defn t
  "Looks up key in resources/i18n/translations.edn for locale, falling back to
  :en-GB when the locale or the whole key is missing. params are interpolated
  into {{placeholder}} spots, mirroring the frontend's i18next syntax."
  ([key locale] (t key locale {}))
  ([key locale params]
   (let [entry (get translations key)
         template (or (get entry (keyword locale)) (get entry :en-GB))]
     (reduce-kv (fn [s k v] (str/replace s (str "{{" (name k) "}}") (str v)))
                template
                params))))
