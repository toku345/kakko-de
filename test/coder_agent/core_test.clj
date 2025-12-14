(ns coder-agent.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [coder-agent.core :as core]
            [cheshire.core :as json]))

(deftest extract-content-test
  (testing "extracts content from standard response"
    (let [response {:choices [{:message {:content "Hello, world!"}}]}]
      (is (= "Hello, world!"
             (core/extract-content response)))))

  (testing "returns nil for empty response"
    (is (nil? (core/extract-content {})))
    (is (nil? (core/extract-content {:choices []})))))

(deftest chat-test
  (testing "chat returns content from LLM response"
    (let [mock-call-llm (fn [_request _config]
                          {:choices [{:message {:content "Mock response"}}]})]
      (is (= "Mock response"
             (core/chat "test input" :call-llm-fn mock-call-llm)))))

  (testing "chat passes correct request structure"
    (let [captured-request (atom nil)
          mock-call-llm (fn [request _config]
                          (reset! captured-request request)
                          {:choices [{:message {:content "OK"}}]})]
      (core/chat "What is Clojure?" :call-llm-fn mock-call-llm)
      (is (= "What is Clojure?"
             (-> @captured-request
                 :messages
                 first
                 :content))))))

(deftest chat-tool-loop-test
  (testing "chat executes tool and returns final response."
    (let [call-count (atom 0)
          mock-llm (fn [_request _config]
                     (swap! call-count inc)
                     (if (= 1 @call-count)
                       ;; First call: LLM requests tool
                       {:choices [{:message {:tool_calls [{:id "call_123"
                                                           :function {:name "write_file"
                                                                      :arguments (json/generate-string
                                                                                  {:file_path "hello.txt"
                                                                                   :content "hello"})}}]}}]}

                       ;; Second call: LLM returns final answer
                       {:choices [{:message {:content "File written successfully."}}]}))
          mock-execute (fn [_tc] {:success true})
          result (core/chat "Write hello to test.txt"
                            :call-llm-fn mock-llm
                            :execute-tool-fn mock-execute)]
      (is (= "File written successfully." result))
      (is (= 2 @call-count))))

  (testing "chat throws on max iterations exceeded."
    (let [infinite-tool-llm (fn [_request _config]
                              {:choices [{:message {:tool_calls [{:id "loop"
                                                                  :function {:name "write_file"
                                                                             :arguments "{}"}}]}}]})
          mock-execute (fn [_tc] {:success true})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Max tool iterations exceeded"
                            (core/chat "Loop forever"
                                       :call-llm-fn infinite-tool-llm
                                       :execute-tool-fn mock-execute))))))
