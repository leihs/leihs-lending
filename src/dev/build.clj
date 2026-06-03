(ns dev.build
  (:import
   [java.lang ProcessBuilder Runtime Thread]))

(defn start-process [cmd]
  (let [current-dir (System/getProperty "user.dir")
        builder (-> (ProcessBuilder. (into-array String cmd))
                    (.directory (java.io.File. current-dir))
                    (.redirectError java.lang.ProcessBuilder$Redirect/INHERIT)
                    (.redirectOutput java.lang.ProcessBuilder$Redirect/INHERIT))
        process (.start builder)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (.destroy process))))
    process))

(defn ^:export run-cmd-configure
  {:shadow.build/stage :configure}
  [build-state {:keys [cmd]}]
  (start-process cmd)
  build-state)

(defn ^:export run-cmd-flush
  {:shadow.build/stage :flush}
  [build-state {:keys [cmd]}]
  (let [process (start-process cmd)]
    (.waitFor process)
    build-state))
