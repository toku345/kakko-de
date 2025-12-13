(ns coder-agent.tools)

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

(comment
  ;; REPL Test
  (write-file {:file_path "test_output.txt"
               :content   "This is a test content!?"})

  (write-file! default-fs "test_output_2" "Test content made via protocol!"))
