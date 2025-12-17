(ns coder-agent.test-helper
  "Test utilities including malli instrumentation setup."
  (:require [malli.dev :as dev]
            [malli.dev.pretty :as pretty]
            [malli.instrument :as mi]))

(defn with-instrumentation
  "Fixture to enable malli instrumentation for tests."
  [f]
  (mi/collect!)
  (dev/start! {:report (pretty/reporter)})
  (try
    (f)
    (finally
      (dev/stop!))))
