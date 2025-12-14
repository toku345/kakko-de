(ns coder-agent.tools
  (:require [cheshire.core :as json]))

(defprotocol FileSystem
  "Protocol for file system operations."
  (write-file! [this path content] "Writes content to a file specified by path."))

(defrecord RealFileSystem []
  FileSystem
  (write-file! [_ path content]
    (spit path content)
    {:success true :file_path path}))

(def default-fs (->RealFileSystem))

(defn write-file
  "Write content to the specified file path."
  [{:keys [file_path content]}]
  (write-file! default-fs file_path content))

(def write-tool
  {:type "function"
   :function {:name "write_file"
              :description "Write content to a file. Overwrite the entire file."
              :parameters {:type "object"
                           :properties {:file_path {:type "string"
                                                    :description "Absolute path to the file."}
                                        :content {:type "string"
                                                  :description "Content to write into the file."}}
                           :required ["file_path" "content"]}}})

;; Tool registry & dispatcher
(def tool-registry
  {"write_file" write-file})

(defn execute-tool
  "Execute a tool call from LLM response."
  [tool-call & {:keys [tool-impls] :or {tool-impls tool-registry}}]
  (let [{:keys [function]} tool-call
        {:keys [name arguments]} function
        args (json/parse-string arguments true)
        tool-fn (get tool-impls name)]
    (if tool-fn
      (tool-fn args)
      {:success false :error (str "Unknown tool: " name)})))

(comment
  ;; REPL Test
  (write-file {:file_path "test_output.txt"
               :content   "This is a test content!?"})

  (write-file! default-fs "test_output_2" "Test content made via protocol!")

  (execute-tool
   {:function
    {:name "write_file"
     :arguments (json/generate-string
                 {:file_path "test_output_3.txt"
                  :content   "Content via execute-tool!"})}}))
