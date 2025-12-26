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
      (is (= "What is Clojure?"
             (-> @captured-request
                 :messages
                 first
                 :content)))))

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
