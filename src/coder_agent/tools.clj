(ns coder-agent.tools)

(defn write-file
  "Writes content to the specified file path."
  [{:keys [file_path content]}]
  (spit file_path content)
  {:success true :file_path file_path})

(comment
  ;; REPL Test
  (write-file {:file_path "test_output.txt"
               :content   "This is a test content!?"}))
