(ns coder-agent.tools
  (:require [cheshire.core :as json]
            [coder-agent.protocols :refer [FileSystem write-file! read-file!]]
            [coder-agent.schema :as schema]))

(defrecord RealFileSystem []
  FileSystem
  (write-file! [_ path content]
    (spit path content)
    {:success true :file_path path})
  (read-file! [_ path]
    (let [content (slurp path)]
      {:success true :content content})))

(def default-fs (->RealFileSystem))

(defn write-file
  "Write content to the specified file path."
  [{:keys [file_path content]} & {:keys [fs] :or {fs default-fs}}]
  (write-file! fs file_path content))

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

(defn read-file
  "Read content from the specified file path."
  [{:keys [file_path]} & {:keys [fs] :or {fs default-fs}}]
  (read-file! fs file_path))

(def read-tool
  {:type "function"
   :function {:name "read_file"
              :description "Read content from a file."
              :parameters {:type "object"
                           :properties {:file_path {:type "string"
                                                    :description "Absolute path to the file."}}
                           :required ["file_path"]}}})

;; Tool registry & dispatcher
(def tool-registry
  {"write_file" write-file
   "read_file" read-file})

(defn execute-tool
  "Execute a tool call from LLM response."
  {:malli/schema schema/ExecuteToolSchema}
  [tool-call & {:keys [tool-impls] :or {tool-impls tool-registry}}]
  (try
    (let [{:keys [function]} tool-call
          {:keys [name arguments]} function
          args (json/parse-string arguments true)
          tool-fn (get tool-impls name)]
      (if tool-fn
        (tool-fn args)
        {:success false :error (str "Unknown tool: " name)}))
    (catch Exception e
      {:success false :error (str "Tool execution failed: " (.getMessage e))})))

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
