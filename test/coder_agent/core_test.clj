(ns coder-agent.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [coder-agent.core :as core]
            [coder-agent.llm :as llm]
            [cheshire.core :as json]
            [coder-agent.test-helper :as helper]))

(use-fixtures :once helper/with-instrumentation)

(deftest chat-test
  (testing "chat returns content from LLM response"
    (let [mock-client (llm/make-mock-client
                       (fn [_request]
                         {:choices [{:message {:content "Mock response"}}]}))]
      (is (= "Mock response"
             (core/chat mock-client "test input")))))

  (testing "chat passes correct request structure"
    (let [captured-request (atom nil)
          mock-client (llm/make-mock-client
                       (fn [request]
                         (reset! captured-request request)
                         {:choices [{:message {:content "OK"}}]}))]
      (core/chat mock-client "What is Clojure?")
      (is (= "system"
             (-> @captured-request :messages first :role)))
      (is (= core/default-system-prompt
             (-> @captured-request :messages first :content)))
      (is (= "What is Clojure?"
             (-> @captured-request :messages second :content)))))

  (testing "chat propagates LLM client exceptions"
    (let [error-client (llm/make-mock-client
                        (fn [_request]
                          (throw (ex-info "API Error" {:status 500}))))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"API Error"
                            (core/chat error-client "test input"))))))

(deftest chat-tool-loop-test
  (testing "chat executes tool and returns final response."
    (let [call-count (atom 0)
          mock-client (llm/make-mock-client
                       (fn [_request]
                         (swap! call-count inc)
                         (if (= 1 @call-count)
                           {:choices [{:message {:tool_calls [{:id "1"
                                                               :function {:name "write_file"
                                                                          :arguments (json/generate-string
                                                                                      {:file_path "test.txt"
                                                                                       :content "hello"})}}]}}]}
                           {:choices [{:message {:content "File written successfully."}}]})))
          mock-execute (fn [_tc] {:success true})
          result (core/chat mock-client "Write hello to test.txt"
                            :execute-tool-fn mock-execute)]
      (is (= "File written successfully." result))
      (is (= 2 @call-count))))

  (testing "chat throws on max iterations exceeded."
    (let [mock-client (llm/make-mock-client
                       (fn [_request]
                         {:choices [{:message {:tool_calls [{:id "loop"
                                                             :function {:name "write_file"
                                                                        :arguments "{}"}}]}}]}))
          mock-execute (fn [_tc] {:success true})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Max tool iterations exceeded"
                            (core/chat mock-client "Loop forever"
                                       :execute-tool-fn mock-execute))))))

(deftest chat-step-test
  (testing "returns :complete when no tool calls"
    (let [mock-client (llm/make-mock-client
                       (fn [_request]
                         {:choices [{:message {:content "Hello"}}]}))
          result (core/chat-step mock-client
                                 [{:role "user" :content "Hi"}]
                                 :on-tool-execution nil)]
      (is (= :complete (:status result)))
      (is (= "Hello" (:content result)))))

  (testing "returns :continue when tool calls present"
    (let [mock-client (llm/make-mock-client
                       (fn [_request]
                         {:choices [{:message {:role "assistant"
                                               :tool_calls [{:id "tc1"
                                                             :function {:name "read_file"
                                                                        :arguments "{}"}}]}}]}))
          result (core/chat-step mock-client
                                 [{:role "user" :content "Write"}]
                                 :execute-tool-fn (constantly {:success true})
                                 :on-tool-execution nil)
          msg (:messages result)]
      (is (= :continue (:status result)))
      (is (= 3 (count msg)))
      (is (= "user" (:role (first msg))))
      (is (= "assistant" (:role (second msg))))
      (is (= "tool" (:role (nth msg 2))))
      (is (= "tc1" (:tool_call_id (nth msg 2))))))

  (testing "handles multiple tool calls in single response"
    (let [executed-tools (atom [])
          mock-client (llm/make-mock-client
                       (fn [_request]
                         {:choices [{:message {:tool_calls
                                               [{:id "tc1" :function {:name "read_file" :arguments "{}"}}
                                                {:id "tc2" :function {:name "list_dir" :arguments "{}"}}]}}]}))
          mock-execute (fn [tc]
                         (swap! executed-tools conj (:id tc))
                         {:success true})
          result (core/chat-step mock-client
                                 [{:role "user" :content "Multi"}]
                                 :execute-tool-fn mock-execute
                                 :on-tool-execution nil)]
      (is (= :continue (:status result)))
      (is (= ["tc1" "tc2"] @executed-tools))
      (is (= 4 (count (:messages result))))))

  (testing "survives callback exception via safe-invoke"
    (let [mock-client (llm/make-mock-client
                       (fn [_request]
                         {:choices [{:message {:tool_calls [{:id "1"
                                                             :function {:name "write_file"
                                                                        :arguments "{}"}}]}}]}))
          faulty-callback (fn [_ _] (throw (Exception. "Callback error")))
          result (core/chat-step mock-client
                                 [{:role "user" :content "Write"}]
                                 :execute-tool-fn (constantly {:success true})
                                 :on-tool-execution faulty-callback)]
      (is (some? result)))))

(deftest format-tool-result-message-test
  (testing "formats successful tool result correctly"
    (let [tool-call {:id "call_123" :function {:name "read_file"}}
          result {:success true :content "file contents"}
          msg (core/format-tool-result-message tool-call result)]
      (is (= "tool" (:role msg)))
      (is (= "call_123" (:tool_call_id msg)))
      (is (= (json/generate-string result) (:content msg)))))

  (testing "formats failed tool result correctly"
    (let [tool-call {:id "call_456" :function {:name "write_file"}}
          result {:success false :error "Permission denied"}
          msg (core/format-tool-result-message tool-call result)]
      (is (= "tool" (:role msg)))
      (is (string? (:content msg))))))
