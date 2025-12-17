(ns user
  "Development namespace with malli instrumentation."
  (:require [malli.dev :as dev]
            [malli.dev.pretty :as pretty]))

(defn start-instrumentation!
  "Start malli instrumentation with pretty error reporting."
  []
  (dev/start! {:report (pretty/reporter)})
  (println "Malli instrumentation started."))

(defn stop-instrumentation!
  "Stop malli instrumentation."
  []
  (dev/stop!)
  (println "Malli instrumentation stopped."))

(comment
  (start-instrumentation!)
  (stop-instrumentation!))
