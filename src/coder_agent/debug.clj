(ns coder-agent.debug
  "Debug logging utilities for LLM request/response visibility."
  (:require [cheshire.core :as json]))

(def ^:dynamic *debug-enabled*
  "Dynamic var for debug mode. True if DEBUG env var is exactly \"true\", false otherwise."
  (= "true" (System/getenv "DEBUG")))

(defn format-json
  "Format JSON data as pretty-printed string.
   Returns nil on failure (nil-punning pattern)."
  [data]
  (try
    (if (string? data)
      (json/generate-string (json/parse-string data) {:pretty true})
      (json/generate-string data {:pretty true}))
    (catch Exception _ nil)))

(defn- truncate
  "Truncate string to max-len characters, appending '...' if truncated. Returns s unchanged if nil or within limit."
  [s max-len]
  (if (and s (> (count s) max-len))
    (str (subs s 0 max-len) "...")
    s))

(defn log-request
  "Log LLM request details when debug mode is enabled."
  [request]
  (when *debug-enabled*
    (println "\n========== LLM REQUEST ==========")
    (println "Model:" (:model request))
    (println "Message count:" (count (:messages request)))
    (println "Tools count:" (count (:tools request)))
    (println "\n--- Messages ---")
    (doseq [msg (:messages request)]
      (println (str "  [" (:role msg) "]"))
      (when-let [tool-call-id (:tool_call_id msg)]
        (println (str "    tool_call_id: " tool-call-id)))
      (when-let [content (:content msg)]
        (println (str "    " (truncate content 200))))
      (when-let [tool-calls (:tool_calls msg)]
        (println "   tool_calls:")
        (doseq [tc tool-calls]
          (println (str "      - " (-> tc :function :name)))
          (let [args (-> tc :function :arguments)]
            (println (str "        args: " (or (format-json args) args)))))))
    (println "=================================")))

(defn log-response
  "Log LLM response details when debug mode is enabled."
  [response]
  (when *debug-enabled*
    (println "\n========== LLM RESPONSE ==========")
    (let [choice (-> response :choices first)
          message (:message choice)
          tool-calls (:tool_calls message)
          content (:content message)]
      (println "Finish reason: " (:finish_reason choice))
      (when content
        (println "\n--- Content ---")
        (println content))
      (when (seq tool-calls)
        (println "\n--- Tool Calls ---")
        (doseq [tc tool-calls]
          (println (str "  ID: " (:id tc)))
          (println (str "  Function: " (-> tc :function :name)))
          (println "  Arguments:")
          (let [args (-> tc :function :arguments)]
            (println (str "    " (or (format-json args) args)))))))
    (println "=================================")))

(defn log-tool-execution
  "Log tool execution details when debug mode is enabled."
  [tool-call result]
  (when *debug-enabled*
    (println "\n---------- Tool Execution ----------")
    (println "Tool:" (-> tool-call :function :name))
    (let [args (-> tool-call :function :arguments)]
      (println (str "Args: " (or (format-json args) args))))
    (println "Result:" (if (:success result) "SUCCESS" "FAILURE"))
    (when-not (:success result)
      (println "Error:" (:error result)))
    (println "------------------------------------")))
