(ns coder-agent.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [coder-agent.core :as core]))

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
