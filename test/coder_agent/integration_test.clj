(ns coder-agent.integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [coder-agent.core :as core]
            [coder-agent.tools :as tools]))

(deftest ^:integration real-api-test
  (testing "Real API call returns non-empty response."
    (is (some? (System/getenv "OPENAI_API_KEY"))
        "OPENAI_API_KEY must be set for integration tests.")
    (let [response (core/chat "Say hello in one word.")]
      (is (string? response))
      (is (pos? (count response))))))

(deftest ^:integration write-file-integration-test
  (testing "write-file writes to actual file"
    (let [file-path "test_output.txt"
          content "Integration test content."
          result (tools/write-file {:file_path file-path :content content})]
      (is (= {:success true :file_path file-path} result))
      (is (= content (slurp file-path))))))
