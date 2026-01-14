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

(defn- format-message
  "Format a single message for request log output."
  [msg]
  (str "  [" (:role msg) "]\n"
       (when-let [tool-call-id (:tool_call_id msg)]
         (str "    tool_call_id:" tool-call-id "\n"))
       (when-let [content (:content msg)]
         (str "    " (truncate content) "\n"))
       (when-let [tool-calls (:tool_calls msg)]
         (str "    tool_calls:\n"
              (->> tool-calls
                   (map (fn [tc]
                          (let [args (-> tc :function :arguments)]
                            (str "      - " (-> tc :function :name) "\n"
                                 "        args: " (or (format-json args) args) "\n"))))
                   (apply str))))))
(defn format-request-summary
  "Format request summary as log string."
  [summary]
  (str "\n========== LLM REQUEST ==========\n"
       "Model: " (:model summary) "\n"
       "Message count: " (:message-count summary) "\n"
       "Tool count: " (:tools-count summary) "\n"
       "\n--- Message ---\n"
       (->> (:messages summary)
            (map format-message)
            (apply str))))

(defn format-response-summary
  "Format response summary as log string."
  [summary]
  (str "\n========== LLM RESPONSE ==========\n"
       "Finish reason: " (:finish_reason summary) "\n"
       (when-let [content (:content summary)]
         (str "\n--- Content ---\n"
              content "\n"))
       (when (seq (:tool_calls summary))
         (str "\n--- Tool Calls ---\n"
              (->> (:tool_calls summary)
                   (map (fn [tc]
                          (let [args (-> tc :function :arguments)]
                            (str "  ID: " (:id tc) "\n"
                                 "  Function: " (-> tc :function :name) "\n"
                                 "  Arguments:\n"
                                 "    " (or (format-json args) args) "\n"))))
                   (apply str))))
       "=================================\n"))

(defn format-tool-execution-summary
  "Format tool execution summary as log string."
  [summary]
  (str "\n---------- Tool Execution ----------\n"
       "Tool: " (:tool-name summary) "\n"
       "Args: " (or (format-json (:arguments summary)) (:arguments summary)) "\n"
       "Result: " (if (:success summary) "SUCCESS" "FAILURE") "\n"
       (when-not (:success summary)
         (str "Error: " (:error summary) "\n"))
       "------------------------------------\n"))
(defn log-request
  "Log LLM request details when debug mode is enabled.
   Options:
     :output-fn - Output function (default: println)
     :enabled? - Override debug check (default: uses debug-active?)"
  [request & {:keys [output-fn enabled?]
              :or {output-fn println}}]
  (when (if (some? enabled?) enabled? (debug-active?))
    (-> request
        extract-request-summary
        format-request-summary
        output-fn)))

(defn log-response
  "Log LLM response details when debug mode is enabled.
   Options:
     :output-fn - Output function (default: println)
     :enabled? - Override debug check (default: uses debug-active?)"
  [response & {:keys [output-fn enabled?]
               :or {output-fn println}}]
  (when (if (some? enabled?) enabled? (debug-active?))
    (-> response
        extract-response-summary
        format-response-summary
        output-fn)))

(defn log-tool-execution
  "Log tool execution details when debug mode is enabled.
   Options:
     :output-fn - Output function (default: println)
     :enabled? - Override debug check (default: uses debug-active?)"
  [tool-call result & {:keys [output-fn enabled?]
                       :or {output-fn println}}]
  (when (if (some? enabled?) enabled? (debug-active?))
    (-> (extract-tool-execution-summary tool-call result)
        format-tool-execution-summary
        output-fn)))
