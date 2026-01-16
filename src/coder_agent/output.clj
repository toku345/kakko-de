(ns coder-agent.output
  "CLI output utilities for normal operation visibility."
  (:require [cheshire.core :as json]))

(defn extract-target-path
  "Extract the primary target path from tool arguments."
  [tool-name args]
  (case tool-name
    "read_file" (:file_path args)
    "write_file" (:file_path args)
    "list_dir" (:dir_path args)
    nil))
(defn format-tool-line
  "Format a single tool execution as a concise one-line output."
  [tool-call result]
  (let [tool-name (-> tool-call :function :name)
        args-json (-> tool-call :function :arguments)
        args (try (json/parse-string args-json true) (catch Exception _ {}))
        target-path (extract-target-path tool-name args)
        success? (:success result)
        status-icon (if success? "✓" "✗")
        error-msg (when-not success? (:error result))]
    (str "🔧 " tool-name ":"
         (when target-path (str " " target-path))
         " " status-icon
         (when error-msg (str " Error: " error-msg)))))

(defn print-tool-execution
  "Print tool execution result to stdout."
  [tool-call result]
  (println (format-tool-line tool-call result)))
