(ns coder-agent.debug-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [coder-agent.debug :as debug]
            [coder-agent.test-helper :as helper]))

(use-fixtures :each helper/with-instrumentation)

;; === format-json ===

(deftest format-json-test
  (testing "valid JSON string"
    (let [result (debug/format-json "{\"a\":1}")]
      (is (string? result))
      (is (re-find #"\"a\"" result))))

  (testing "invalid JSON string returns nil"
    (is (nil? (debug/format-json "not json"))))

  (testing "Clojure map"
    (let [result (debug/format-json {:a 1})]
      (is (string? result))
      (is (re-find #"\"a\"" result)))))

;; === truncate (private) ===

(deftest truncate-test
  (let [truncate #'debug/truncate]
    (testing "short string unchanged"
      (is (= "abc" (truncate "abc" 10))))

    (testing "long string truncated with ellipsis"
      (is (= "abcde..." (truncate "abcdefghijk" 5))))

    (testing "nil returns nil"
      (is (nil? (truncate nil 10))))

    (testing "boundary - exact length unchanged"
      (is (= "abcde" (truncate "abcde" 5))))))

;; === log-request ===

(deftest log-request-test
  (testing "outputs when debug enabled"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* true]
                     (debug/log-request {:model "test-model"
                                         :messages [{:role "user" :content "hello"}]
                                         :tools []})))]
      (is (re-find #"LLM REQUEST" output))
      (is (re-find #"test-model" output))))

  (testing "no output when debug disabled"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* false]
                     (debug/log-request {:choices [{:message {}}]})))]
      (is (= "" output))))

  (testing "handle empty messages and tools"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* true]
                     (debug/log-request {:model "m" :messages [] :tools []})))]
      (is (re-find #"Message count: 0" output)))))

;; === log-response ===

(deftest log-response-test
  (testing "outputs when debug enabled"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* true]
                     (debug/log-response {:choices [{:finish_reason "stop"
                                                     :message {:content "hi"}}]})))]
      (is (re-find #"LLM RESPONSE" output))
      (is (re-find #"stop" output))))

  (testing "no output when debug disabled"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* false]
                     (debug/log-response {:choices [{:message {}}]})))]
      (is (= "" output))))

  (testing "handles empty choices"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* true]
                     (debug/log-response {:choices []})))]
      (is (re-find #"LLM RESPONSE" output)))))

;; === log-tool-execution ===

(deftest log-tool-execution-test
  (testing "outputs when debug enabled"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* true]
                     (debug/log-tool-execution
                      {:function {:name "read_file" :arguments "{}"}}
                      {:success true})))]
      (is (re-find #"Tool Execution" output))
      (is (re-find #"SUCCESS" output))))

  (testing "outputs failure with error"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* true]
                     (debug/log-tool-execution
                      {:function {:name "write_file" :arguments "{}"}}
                      {:success false :error "Permission denied"})))]
      (is (re-find #"FAILURE" output))
      (is (re-find #"Permission denied" output))))

  (testing "no output when debug disabled"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* false]
                     (debug/log-tool-execution
                      {:function {:name "test" :arguments "{}"}}
                      {:success true})))]
      (is (= "" output)))))
