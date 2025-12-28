(ns coder-agent.tools-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [coder-agent.protocols :refer [FileSystem list-dir! read-file! write-file! list-dir!]]
   [coder-agent.schema :as schema]
   [coder-agent.test-helper :as helper]
   [coder-agent.tools :as tools :refer [default-fs]]
   [malli.core :as m]))

(defrecord MockFileSystem [calls]
  FileSystem
  (write-file! [_ path content]
    (swap! calls conj {:path path :content content})
    {:success true :file_path path})
  (read-file! [_ path]
    (let [content (str "Mock content of " path)]
      {:success true :content content}))
  (list-dir! [_ _path]
    {:success true :output "Mocked directory listing"}))

(defn mock-fs []
  (->MockFileSystem (atom [])))

(use-fixtures :once helper/with-instrumentation)

;; === RealFileSystem Tests ===

(def test-file-path "test/test_integration_output.txt")
(def test-read-file-path "test/fixtures/sample.txt")

(defn cleanup-test-file [f]
  (try
    (f)
    (finally
      (io/delete-file test-file-path true))))

(use-fixtures :each cleanup-test-file)

(deftest write-file!-test
  (testing "write-file! writes to actual file system"
    (let [fs default-fs
          file-path test-file-path
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
      (is (re-find #"sample.txt" (:output result)))
      (is (re-find #"another_sample.txt" (:output result)))))

  (testing "list-dir! returns error for non-existent directory"
    (let [fs default-fs
          dir-path "nonexistent_dir/"
          result (list-dir! fs dir-path)]
      (is (= false (:success result)))
      (is (re-find #"No such file or directory" (:error result))))))

;; === Wrapper Function Tests ===

(deftest write-file-test
  (testing "write-file delegates to FileSystem protocol"
    (let [fs (mock-fs)
          result (tools/write-file {:file_path "test.txt" :content "hello"} :fs fs)]
      (is (= {:success true :file_path "test.txt"} result))
      (is (= [{:path "test.txt" :content "hello"}] @(:calls fs))))))

(deftest read-file-test
  (testing "read-file delegates to FileSystem protocol"
    (let [fs (mock-fs)
          result (tools/read-file {:file_path "test.txt"} :fs fs)]
      (is (= {:success true :content "Mock content of test.txt"} result)))))

(deftest list-dir-test
  (testing "list-dir delegates to FileSystem protocol"
    (let [fs (mock-fs)
          result (tools/list-dir {:dir_path "some/dir"} :fs fs)]
      (is (= {:success true :output "Mocked directory listing"} result)))))

;; === Tool Dispatcher Tests ===

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
