(ns coder-agent.debug-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [coder-agent.debug :as debug]
            [coder-agent.test-helper :as helper]))
(
use-fixtures :once helper/with-instrumentation)

(deftest extract-request-summary-test
  (testing "extracts model and counts"
    (let [summary (debug/extract-request-summary
                   {:model "gpt-4"
                    :messages [{:role "user" :content "hello"}]
                    :tools [{:type "function" :function {:name "read_file"}}]})]
      (is (= "gpt-4" (:model summary)))
      (is (= 1 (:message-count summary)))
      (is (= 1 (:tools-count summary)))))

  (testing "extracts message details with truncated content"
    (let [summary (debug/extract-request-summary
                   {:model "test"
                    :messages [{:role "assistant"
                                :content "hi"
                                :tool_calls [{:id "1" :function {:name "test" :arguments "{\"arg\":\"value\"}"}}]}]
                    :tools []})]
      (is (= 1 (count (:messages summary))))
      (is (= "assistant" (-> summary :messages first :role)))
      (is (= "hi" (-> summary :messages first :content-truncated)))
      (is (= "test" (-> summary :messages first :tool_calls-formatted first :name)))
      (is (string? (-> summary :messages first :tool_calls-formatted first :args-formatted)))))

  (testing "extracts tool result message with tool_call_id"
    (let [summary (debug/extract-request-summary
                   {:model "test"
                    :messages [{:role "tool"
                                :tool_call_id "call_abc123"
                                :content "{\"result\": \"success\"}"}]
                    :tools []})]
      (is (= 1 (count (:messages summary))))
      (is (= "tool" (-> summary :messages first :role)))
      (is (= "call_abc123" (-> summary :messages first :tool_call_id)))
      (is (string? (-> summary :messages first :content-truncated))))))

(deftest extract-response-summary-test
  (testing "extracts finish_reason and content"
    (let [summary (debug/extract-response-summary
                   {:choices [{:finish_reason "stop"
                               :message {:content "Hello!"}}]})]
      (is (= "stop" (:finish_reason summary)))
      (is (= "Hello!" (:content summary)))
      (is (empty? (:tool_calls-formatted summary)))))

  (testing "extracts tool_calls"
    (let [summary (debug/extract-response-summary
                   {:choices [{:finish_reason "tool_calls"
                               :message {:tool_calls [{:id "call_123"
                                                       :function {:name "read_file"
                                                                  :arguments "{\"path\":\"/tmp\"}"}}]}}]})]
      (is (= "tool_calls" (:finish_reason summary)))
      (is (= 1 (count (:tool_calls-formatted summary))))
      (is (= "call_123" (-> summary :tool_calls-formatted first :id)))
      (is (= "read_file" (-> summary :tool_calls-formatted first :name)))
      (is (string? (-> summary :tool_calls-formatted first :args-formatted)))))

  (testing "handles empty choices"
    (let [summary (debug/extract-response-summary
                   {:choices []})]
      (is (nil? (:finish_reason summary)))
      (is (nil? (:content summary))))))

(deftest extract-tool-execution-summary-test
  (testing "extracts success case"
    (let [summary (debug/extract-tool-execution-summary
                   {:function {:name "read_file"
                               :arguments "{\"path\":\"/tmp\"}"}}
                   {:success true})]
      (is (= "read_file" (:tool-name summary)))
      (is (string? (:args-formatted summary)))
      (is (re-find #"path" (:args-formatted summary)))
      (is (true? (:success summary)))
      (is (nil? (:error summary)))))

  (testing "extracts failure case"
    (let [summary (debug/extract-tool-execution-summary
                   {:function {:name "write_file"
                               :arguments "{}"}}
                   {:success false :error "Permission denied"})]
      (is (= "write_file" (:tool-name summary)))
      (is (string? (:args-formatted summary)))
      (is (false? (:success summary)))
      (is (= "Permission denied" (:error summary)))))
  (testing "fall back to raw arguments when JSON parsing fails"
    (let [summary (debug/extract-tool-execution-summary
                   {:function {:name "test_tool"
                               :arguments "invalid json"}}
                   {:success true})]
      (is (= "invalid json" (:args-formatted summary))))))

(deftest format-request-summary-test
  (testing "formats basic request info"
    (let [summary {:model "gpt-4"
                   :message-count 2
                   :tools-count 1
                   :messages []}
          output (debug/format-request-summary summary)]
      (is (string? output))
      (is (re-find #"LLM REQUEST" output))
      (is (re-find #"Model: gpt-4" output))
      (is (re-find #"Message count: 2" output))
      (is (re-find #"Tool count: 1" output))))

  (testing "formats message content"
    (let [summary {:model "test"
                   :message-count 1
                   :tools-count 0
                   :messages [{:role "user"
                               :content-truncated "hello world"
                               :tool_call_id nil
                               :tool_calls-formatted []}]}
          output (debug/format-request-summary summary)]
      (is (re-find #"\[user\]" output))
      (is (re-find #"hello world" output))))

  (testing "displays pre-truncated context"
    (let [truncated-content (str (apply str (repeat 200 "x")) "...")
          summary {:model "test"
                   :message-count 1
                   :tools-count 0
                   :messages [{:role "user"
                               :content-truncated truncated-content
                               :tool_call_id nil
                               :tool_calls-formatted []}]}
          output (debug/format-request-summary summary)]
      (is (re-find #"\.\.\." output))))

  (testing "formats assistant message with tool calls"
    (let [summary {:model "test"
                   :message-count 1
                   :tools-count 0
                   :messages [{:role "assistant"
                               :content-truncated nil
                               :tool_call_id nil
                               :tool_calls-formatted [{:name "read_file"
                                                       :args-formatted "{\"path\":\"/tmp\"}"}]}]}
          output (debug/format-request-summary summary)]
      (is (re-find #"\[assistant\]" output))
      (is (re-find #"tool_calls:" output))
      (is (re-find #"read_file" output))
      (is (re-find #"args:" output)))))

(deftest format-response-summary-test
  (testing "formats finish_reason and content"
    (let [summary {:finish_reason "stop"
                   :content "Hello, world!"
                   :tool_calls []}
          output (debug/format-response-summary summary)]
      (is (string? output))
      (is (re-find #"LLM RESPONSE" output))
      (is (re-find #"Finish reason:.*stop" output))
      (is (re-find #"--- Content ---" output))
      (is (re-find #"Hello, world!" output))))

  (testing "formats tool_calls"
    (let [summary {:finish_reason "tool_calls"
                   :content nil
                   :tool_calls-formatted [{:id "call_123"
                                           :name "read_file"
                                           :args-formatted "{\"path\":\"/tmp\"}"}]}
          output (debug/format-response-summary summary)]
      (is (re-find #"--- Tool Calls ---" output))
      (is (re-find #"ID: call_123" output))
      (is (re-find #"Function: read_file" output))))

  (testing "handles empty response"
    (let [summary {:finish_reason nil
                   :content nil
                   :tool_calls nil}
          output (debug/format-response-summary summary)]
      (is (re-find #"LLM RESPONSE" output))
      (is (not (re-find #"--- Content ---" output)))
      (is (not (re-find #"--- Tool Calls ---" output))))))

(deftest format-tool-execution-summary-test
  (testing "formats success case"
    (let [summary {:tool-name "read_file"
                   :args-formatted "{\n  \"path\" : \"/tmp\"\n}"
                   :success true
                   :error nil}
          output (debug/format-tool-execution-summary summary)]
      (is (string? output))
      (is (re-find #"Tool Execution" output))
      (is (re-find #"Tool: read_file" output))
      (is (re-find #"Args:" output))
      (is (re-find #"Result: SUCCESS" output))))

  (testing "formats failure case"
    (let [summary {:tool-name "write_file"
                   :args-formatted "{}"
                   :success false
                   :error "Permission denied"}
          output (debug/format-tool-execution-summary summary)]
      (is (re-find #"Tool: write_file" output))
      (is (re-find #"Result: FAILURE" output))
      (is (re-find #"Error: Permission denied" output)))))

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
      (is (re-find #"\"a\"" result))))

  (testing "nil input returns null string"
    (is (= "null" (debug/format-json nil))))

  (testing "nested structure"
    (let [result (debug/format-json {:outer {:inner [1 2 3]}})]
      (is (re-find #"\"outer\"" result))
      (is (re-find #"\"inner\"" result))
      (is (re-find #"\[ 1, 2, 3 \]" result))))

  (testing "empty object"
    (is (= "{ }" (debug/format-json {}))))

  (testing "empty array"
    (is (= "[ ]" (debug/format-json []))))

  (testing "empty string returns nil (nil-punning)"
    (is (nil? (debug/format-json ""))))

  (testing "blank string returns nil (nil-punning)"
    (is (nil? (debug/format-json "   ")))))

;; === truncate (private) ===

(deftest truncate-test
  (let [truncate #'debug/truncate]
    (testing "short string unchanged"
      (is (= "abc" (truncate "abc" :max-len 10))))

    (testing "long string truncated with ellipsis"
      (is (= "abcde..." (truncate "abcdefghijk" :max-len 5))))

    (testing "nil returns nil"
      (is (nil? (truncate nil :max-len 10))))

    (testing "boundary - exact length unchanged"
      (is (= "abcde" (truncate "abcde" :max-len 5))))))

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
                     (debug/log-request {:model "test" :messages [] :tools []})))]
      (is (= "" output))))

  (testing "handle empty messages and tools"
    (let [output (with-out-str
                   (binding [debug/*debug-enabled* true]
                     (debug/log-request {:model "m" :messages [] :tools []})))]
      (is (re-find #"Message count: 0" output)))))

(deftest log-request-with-options-test
  (testing "uses custom output-fn"
    (let [captured (atom "")]
      (debug/log-request {:model "test" :messages [] :tools []}
                         :output-fn #(reset! captured %)
                         :enabled? true)
      (is (string? @captured))
      (is (re-find #"test" @captured))))

  (testing "respects enabled? false"
    (let [captured (atom nil)]
      (debug/log-request {:model "test" :messages [] :tools []}
                         :output-fn #(reset! captured %)
                         :enabled? false)
      (is (nil? @captured)))))

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

(deftest log-response-with-options-test
  (testing "uses custom output-fn"
    (let [captured (atom nil)]
      (debug/log-response {:choices [{:finish_reason "stop"
                                      :message {:content "hi"}}]}
                          :output-fn #(reset! captured %)
                          :enabled? true)
      (is (string? @captured))
      (is (re-find #"stop" @captured))))

  (testing "respects enabled? false"
    (let [captured (atom nil)]
      (debug/log-response {:choices [{:message {}}]}
                          :output-fn #(reset! captured %)
                          :enabled? false)
      (is (nil? @captured)))))

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

(deftest log-tool-execution-test-with-options
  (testing "uses custom output-fn"
    (let [captured (atom nil)]
      (debug/log-tool-execution {:function {:name "read_file" :arguments "{}"}}
                                {:success true}
                                :output-fn #(reset! captured %)
                                :enabled? true)
      (is (string? @captured))
      (is (re-find #"read_file" @captured))))

  (testing "respects enabled? false"
    (let [captured (atom nil)]
      (debug/log-tool-execution {:function {:name "test_tool" :arguments "{}"}}
                                {:success true}
                                :output-fn #(reset! captured %)
                                :enabled? false)
      (is (nil? @captured)))))
