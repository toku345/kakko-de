(ns coder-agent.tools-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [coder-agent.protocols :refer [FileSystem list-dir! read-file! write-file!]]
   [coder-agent.schema :as schema]
   [coder-agent.test-helper :as helper]
   [coder-agent.tools :as tools :refer [default-fs]]
   [malli.core :as m]
   [cheshire.core :as json]))

(defrecord MockFileSystem [calls config]
  FileSystem
  (write-file! [_ path content]
    (swap! calls conj {:op :write-file :path path :content content})
    (if-let [error (:write-file-error @config)]
      {:success false :error error}
      {:success true :file_path path}))

  (read-file! [_ path]
    (swap! calls conj {:op :read-file :path path})
    (if-let [error (:read-file-error @config)]
      {:success false :error error}
      {:success true :content (str "Mock content of " path)}))

  (list-dir! [_ path]
    (swap! calls conj {:op :list-dir :path path})
    (if-let [error (:list-dir-error @config)]
      {:success false :error error}
      {:success true :listing (str "Mocked listing of " path)})))

(defn mock-fs
  ([] (mock-fs {}))
  ([opts]
   (->MockFileSystem (atom []) (atom opts))))

(use-fixtures :once helper/with-instrumentation)

;; === RealFileSystem Tests ===

(def test-write-file-path "test/test_integration_output.txt")
(def test-read-file-path "test/fixtures/sample.txt")

(defn cleanup-test-file [f]
  (try
    (f)
    (finally
      (io/delete-file test-write-file-path true))))

(use-fixtures :each cleanup-test-file)

(deftest write-file!-test
  (testing "write-file! writes to actual file system"
    (let [fs default-fs
          file-path test-write-file-path
          content "Test content."
          result (write-file! fs file-path content)]
      (is (= {:success true :file_path file-path} result))
      (is (= content (slurp file-path)))))

  (testing "write-file! returns error when parent directory does not exist"
    (let [fs default-fs
          file-path "/nonexistent/test.txt"
          result (write-file! fs file-path "data")]
      (is (false? (:success result)))
      (is (re-find #"Failed to write file:" (:error result)))
      (is (re-find #"No such file or directory" (:error result))))))

(deftest read-file!-test
  (testing "read-file! reads from actual file"
    (let [fs default-fs
          file-path test-read-file-path
          content "# Sample.txt\n\nThis is a sample text file for testing purposes.\n"
          result (read-file! fs file-path)]
      (is (= {:success true :content content} result))))

  (testing "read-file! returns error for non-existent file"
    (let [fs default-fs
          file-path "nonexistent_file.txt"
          result (read-file! fs file-path)]
      (is (false? (:success result)))
      (is (re-find #"Failed to read file:" (:error result)))
      (is (re-find #"No such file or directory" (:error result))))))

(deftest list-dir!-test
  (testing "list-dir! lists files in specified directory"
    (let [fs default-fs
          dir-path "test/fixtures"
          result (list-dir! fs dir-path)]
      (is (= true (:success result)))
      (is (re-find #"sample.txt" (:listing result)))
      (is (re-find #"another_sample.txt" (:listing result)))))

  (testing "list-dir! returns error for nil path"
    (let [fs default-fs
          result (list-dir! fs nil)]
      (is (= false (:success result)))
      (is (re-find #"dir_path parameter is required" (:error result)))))

  (testing "list-dir! returns error for non-existent directory"
    (let [fs default-fs
          dir-path "nonexistent_dir/"
          result (list-dir! fs dir-path)]
      (is (= false (:success result)))
      (is (re-find #"Failed to list directory:" (:error result)))
      (is (re-find #"Directory does not exist" (:error result)))))

  (testing "list-dir! returns error for nil path"
    (let [fs default-fs
          result (list-dir! fs nil)]
      (is (= false (:success result)))
      (is (re-find #"Failed to list directory:" (:error result)))))

  (testing "list-dir! handle empty directory"
    (let [empty-dir (io/file "test/fixtures/empty_dir")]
      (.mkdir empty-dir)
      (try
        (let [result (list-dir! default-fs (.getPath empty-dir))]
          (is (= true (:success result)))
          (is (= "" (:listing result))))
        (finally
          (.delete empty-dir)))))

  (testing "list-dir! returns error when path points to a file"
    (let [fs default-fs
          file-path test-read-file-path
          result (list-dir! fs file-path)]
      (is (= false (:success result)))
      (is (re-find #"is a file, not a directory" (:error result))))))

;; === Wrapper Function Tests ===

(deftest write-file-test
  (testing "write-file delegates to FileSystem protocol"
    (let [fs (mock-fs)
          result (tools/write-file {:file_path "test.txt" :content "hello"} :fs fs)]
      (is (= {:success true :file_path "test.txt"} result))
      (is (= [{:op :write-file :path "test.txt" :content "hello"}] @(:calls fs))))))

(deftest read-file-test
  (testing "read-file delegates to FileSystem protocol"
    (let [fs (mock-fs)
          result (tools/read-file {:file_path "test.txt"} :fs fs)]
      (is (= {:success true :content "Mock content of test.txt"} result)))))

(deftest list-dir-test
  (testing "list-dir delegates to FileSystem protocol with correct path"
    (let [fs (mock-fs)
          result (tools/list-dir {:dir_path "/some/dir"} :fs fs)]
      (is (= {:success true :listing "Mocked listing of /some/dir"} result))
      (is (= [{:op :list-dir :path "/some/dir"}] @(:calls fs))))))

(deftest list-dir-error-simulation-test
  (testing "list-dir returns error when MockFileSystem is configured with error"
    (let [fs (mock-fs {:list-dir-error "Simulated I/O error"})
          result (tools/list-dir {:dir_path "/some/dir"} :fs fs)]
      (is (= {:success false :error "Simulated I/O error"} result)))))

;; === Tool Dispatcher Tests ===

(deftest tool-registry-test
  (testing "tool-registry contains correct tool functions"
    (is (= tools/write-file (get tools/tool-registry "write_file")))
    (is (= tools/read-file (get tools/tool-registry "read_file")))
    (is (= tools/list-dir (get tools/tool-registry "list_dir")))))

(deftest execute-tool-test
  (testing "execute-tool dispatches to correct tool."
    (let [mock-write (fn [args] {:success true :called-with args})
          tool-call {:function {:name "write_file"
                                :arguments "{\"file_path\":\"test.txt\",\"content\":\"hello\"}"}}
          result (tools/execute-tool tool-call :tool-impls {"write_file" mock-write})]
      (is (= {:success true :called-with {:file_path "test.txt" :content "hello"}} result))))

  (testing "execute-tool returns error for unknown tool."
    (let [tool-call {:function {:name "unknown_tool"
                                :arguments "{}"}}
          result (tools/execute-tool tool-call)]
      (is (= false (:success result)))
      (is (re-find #"Unknown tool" (:error result)))))

  (testing "execute-tool handles malformed JSON gracefully."
    (let [tool-call {:function {:name "write_file"
                                :arguments "not valid json"}}
          result (tools/execute-tool tool-call)]
      (is (= false (:success result)))
      (is (re-find #"Tool execution failed" (:error result))))))

(deftest execute-tool-dispatch-test
  (testing "dispatches write_file with correct arguments"
    (let [file-path test-write-file-path
          content "hello"
          tool-call {:function {:name "write_file"
                                :arguments (json/generate-string {:file_path file-path
                                                                  :content content})}}
          result (tools/execute-tool tool-call)]
      (is (:success result))
      (is (= content (slurp file-path)))))

  (testing "dispatches read_file with correct arguments"
    (let [file-path test-read-file-path
          tool-call {:function {:name "read_file"
                                :arguments (json/generate-string {:file_path file-path})}}
          result (tools/execute-tool tool-call)]
      (is (:success result))
      (is (re-find #"Sample.txt" (:content result)))))

  (testing "dispatches list_dir with correct arguments"
    (let [dir-path "test/fixtures"
          tool-call {:function {:name "list_dir"
                                :arguments (json/generate-string {:dir_path dir-path})}}
          result (tools/execute-tool tool-call)]
      (is (:success result))
      (is (re-find #"sample.txt" (:listing result))))))

;; === Schema Contract Tests ===

(deftest result-map-schema-test
  (testing "SuccessResult schema validation"
    (is (m/validate schema/SuccessResult {:success true}))
    (is (m/validate schema/SuccessResult {:success true :file_path "/tmp/test.txt"}))
    (is (not (m/validate schema/SuccessResult {:success false})))
    (is (not (m/validate schema/SuccessResult {}))))

  (testing "FailureResult schema validation"
    (is (m/validate schema/FailureResult {:success false :error "Something went wrong"}))
    (is (not (m/validate schema/FailureResult {:success false})))
    (is (not (m/validate schema/FailureResult {:success true :error "msg"}))))

  (testing "ResultMap discriminated union schema validation"
    (is (m/validate schema/ResultMap {:success true}))
    (is (m/validate schema/ResultMap {:success true :extra "data"}))
    (is (m/validate schema/ResultMap {:success false :error "msg"}))
    (is (not (m/validate schema/ResultMap {:success false})))
    (is (not (m/validate schema/ResultMap {:error "Error"})))))

(deftest tool-call-contract-test
  (testing "execute-tool returns valid ResultMap on success"
    (let [mock-write (fn [_] {:success true :file_path "/test.txt"})
          tool-call {:function {:name "write_file"
                                :arguments "{\"file_path\":\"/test.txt\",\"content\":\"hello\"}"}}
          result (tools/execute-tool tool-call :tool-impls {"write_file" mock-write})]
      (is (schema/valid-result? result))
      (is (:success result))))

  (testing "execute-tool returns valid ResultMap on unknown tool"
    (let [tool-call {:function {:name "unknown_tool"
                                :arguments "{}"}}
          result (tools/execute-tool tool-call)]
      (is (schema/valid-result? result))
      (is (false? (:success result)))
      (is (string? (:error result)))))

  (testing "execute-tool returns valid ResultMap on malformed JSON"
    (let [tool-call {:function {:name "write_file"
                                :arguments "invalid json"}}
          result (tools/execute-tool tool-call)]
      (is (schema/valid-result? result))
      (is (false? (:success result)))
      (is (string? (:error result)))))

  (testing "execute-tool returns valid ResultMap when tool throws"
    (let [throwing-tool (fn [_] (throw (Exception. "Tool error")))
          tool-call {:function {:name "throwing-tool"
                                :arguments "{}"}}
          result (tools/execute-tool tool-call :tool-impls {"throwing-tool" throwing-tool})]
      (is (schema/valid-result? result))
      (is (false? (:success result)))
      (is (re-find #"Tool execution failed" (:error result))))))

(deftest tool-call-schema-test
  (testing "Valid ToolCall structure"
    (is (m/validate schema/ToolCall
                    {:function {:name "write_file"
                                :arguments "{}"}})))

  (testing "Invalid ToolCall structures"
    (is (not (m/validate schema/ToolCall {})))
    (is (not (m/validate schema/ToolCall {:function {}})))
    (is (not (m/validate schema/ToolCall {:function {:name 123}})))))
