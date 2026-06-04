(ns user
  (:require [dev.build :as build]))

(defn start-frontend []
  (build/start-process ["npm" "run" "dev"]))

(defn build-frontend []
  (let [p (build/start-process ["npm" "run" "build"])]
    (.waitFor p)))
