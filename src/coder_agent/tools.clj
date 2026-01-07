(ns coder-agent.tools
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [coder-agent.protocols :refer [FileSystem list-dir! read-file! write-file!]]
   [coder-agent.schema :as schema]))

;; === Private helper functions ===

(defn- validate-dir-path [dir_path]
  (when (nil? dir_path)
    {:success false
     :error "Failed to list directory: dir_path parameter is required."}))

(defn- validate-dir-exists [dir path]
  (when-not (.exists dir)
    {:success false
     :error (str "Failed to list directory: " path " - Directory does not exist.")}))

(defn- validate-is-directory [dir path]
  (when-not (.isDirectory dir)
    {:success false
     :error (str "Failed to list directory: " path
                 " - Path exists but is a file, not a directory. Use read_file tool instead.")}))

(defn- format-entry
  "Format a file entry, appending / suffix for directories"
  [file]
  (let [name (.getName file)]
    (if (.isDirectory file)
      (str name "/")
      name)))

(defn do-list-dir [dir path]
  (if-let [files (.listFiles dir)]
    {:success true
     :listing (str/join "\n" (map format-entry files))}
    {:success false
     :error (str "Failed to list directory: " path " - Permission denied or I/O error.")}))

;; === RealFileSystem ===

(defrecord RealFileSystem []
  FileSystem

  (write-file! [_ path content]
    (try
      (spit path content)
      {:success true :file_path path}
      (catch Exception e
        {:success false
         :error (str "Failed to write file: " path " - " (.getMessage e))})))

  (read-file! [_ path]
    (try
      (let [content (slurp path)]
        {:success true :content content})
      (catch Exception e
        {:success false
         :error (str "Failed to read file: " path " - " (.getMessage e))})))

  (list-dir! [_ path]
    (try
      (or (validate-dir-path path)
          (let [dir (io/file path)]
            (or (validate-dir-exists dir path)
                (validate-is-directory dir path)
                (do-list-dir dir path))))
      (catch Exception e
        {:success false
         :error (str "Failed to list directory: " path " - " (.getMessage e))}))))

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

(defn list-dir
  "List files in the specified directory path.
   Returns {:success true :listing \"file1\\nfile2\\n...\"} with newline-separated names,
   or {:success false :error msg} on failure."
  [{:keys [dir_path]} & {:keys [fs] :or {fs default-fs}}]
  (list-dir! fs dir_path))

(def list-dir-tool
  {:type "function"
   :function {:name "list_dir"
              :description "List files in a directory. Returns newline-separated filenames. Directory entries are suffixed with '/'."
              :parameters {:type "object"
                           :properties {:dir_path {:type "string"
                                                   :description "Absolute path to the directory."}}
                           :required ["dir_path"]}}})

;; Tool registry & dispatcher
(def tool-registry
  {"write_file" write-file
   "read_file" read-file
   "list_dir" list-dir})

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

  (read-file! default-fs "test/fixtures/sample.txt")

  (list-dir! default-fs "test/fixtures")

  (execute-tool
   {:function
    {:name "write_file"
     :arguments (json/generate-string
                 {:file_path "test_output_3.txt"
                  :content   "Content via execute-tool!"})}}))
