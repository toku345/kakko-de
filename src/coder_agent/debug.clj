(ns coder-agent.debug
  "Debug logging utilities for LLM request/response visibility."
  (:require [cheshire.core :as json]))

(def max-context-length
  "Maximum length for truncated content display."
  200)

(def ^:dynamic *debug-enabled*
  "Dynamic var for debug mode. nil means use debug-enabled? function."
  nil)

(defn debug-enabled?
  "Check if debug mode is enabled. Evaluates DEBUG env var on each call."
  []
  (= "true" (System/getenv "DEBUG")))

(defn debug-active?
  "Check if debug is active. Binding takes precedence over env var."
  []
  (if (some? *debug-enabled*)
    *debug-enabled*
    (debug-enabled?)))

(defn extract-request-summary
  "Extract summary data from LLM request."
  [request]
  {:model (:model request)
   :message-count (count (:messages request))
   :tools-count (count (:tools request))
   :messages (mapv (fn [msg]
                     {:role (:role msg)
                      :tool_call_id (:tool_call_id msg)
                      :content (:content msg)
                      :tool_calls (:tool_calls msg)})
                   (:messages request))})

(defn extract-response-summary
  "Extract summary data from LLM response."
  [response]
  (let [choice (-> response :choices first)
        message (:message choice)]
    {:finish_reason (:finish_reason choice)
     :content (:content message)
     :tool_calls (:tool_calls message)}))

(defn extract-tool-execution-summary
  "Extract summary data from tool execution."
  [tool-call result]
  {:tool-name (-> tool-call :function :name)
   :arguments (-> tool-call :function :arguments)
   :success (:success result)
   :error (:error result)})

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
  "Truncate string to max-len characters, appending '...' if truncated.
   Non-string values are converted to string first."
  [s & {:keys [max-len] :or {max-len max-context-length}}]
  (when s
    (let [s (str s)]
      (if (> (count s) max-len)
        (str (subs s 0 max-len) "...")
        s))))

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
        (println (str "    " (truncate content))))
      (when-let [tool-calls (:tool_calls msg)]
        (println "    tool_calls:")
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
