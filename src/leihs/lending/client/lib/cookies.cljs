(ns leihs.lending.client.lib.cookies)

(defn get-cookie [name]
  (let [re (js/RegExp. (str "(?:^|; )" name "=([^;]*)"))
        match (.match (.-cookie js/document) re)]
    (when match
      (js/decodeURIComponent (aget match 1)))))
