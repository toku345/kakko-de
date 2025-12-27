(ns coder-agent.integration-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [coder-agent.core :as core]
   [coder-agent.llm :as llm]
   [coder-agent.tools :as tools]))

(def test-file-path "test/test_integration_output.txt")
(def test-read-file-path "test/fixtures/sample.txt")

(defn cleanup-test-file [f]
  (try
    (f)
    (finally
      (io/delete-file test-file-path true) ; true to ignore if file does not exist
      )))

(use-fixtures :each cleanup-test-file)

(deftest ^:integration real-api-test
  (testing "Real API call returns non-empty response."
    (is (some? (System/getenv "OPENAI_API_KEY"))
        "OPENAI_API_KEY must be set for integration tests.")
    (let [client (llm/make-openai-client)
          response (core/chat client "Say hello in one word.")]
      (is (string? response))
      (is (pos? (count response))))))

(deftest ^:integration write-file-integration-test
  (testing "write-file writes to actual file"
    (let [file-path test-file-path
          content "Integration test content."
          result (tools/write-file {:file_path file-path :content content})]
      (is (= {:success true :file_path file-path} result))
      (is (= content (slurp file-path))))))

(deftest ^:integration read-file-integration-test
  (testing "read-file reads from actual file"
    (let [file-path test-read-file-path
          content "# Sample.txt\n\nThis is a sample text file for testing purposes.\n"
          result (tools/read-file {:file_path file-path})]
      (is (= {:success true :content content} result)))))
