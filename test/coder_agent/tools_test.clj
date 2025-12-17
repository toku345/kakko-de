(ns coder-agent.tools-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [coder-agent.schema :as schema]
   [coder-agent.test-helper :as helper]
   [coder-agent.tools :as tools :refer [FileSystem]]
   [malli.core :as m]))

(defrecord MockFileSystem [calls]
  FileSystem
  (tools/write-file! [_ path content]
    (swap! calls conj {:path path :content content})
    {:success true :file_path path}))

(defn mock-fs []
  (->MockFileSystem (atom [])))

(use-fixtures :once helper/with-instrumentation)

(deftest write-file!-test
  (testing "write-file! records calls with correct arguments."
    (let [fs (mock-fs)
          result (tools/write-file! fs "test.txt" "hello")]
      (is (= {:success true :file_path "test.txt"} result))
      (is (= [{:path "test.txt" :content "hello"}] @(:calls fs))))))

(deftest write-file-test
  (testing "write-file delegates to FileSystem protocol"
    (let [fs (mock-fs)
          result (tools/write-file {:file_path "test.txt" :content "hello"} :fs fs)]
      (is (= {:success true :file_path "test.txt"} result))
      (is (= [{:path "test.txt" :content "hello"}] @(:calls fs))))))

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
