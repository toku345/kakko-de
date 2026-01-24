(ns coder-agent.llm-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [coder-agent.llm :as llm]
            [coder-agent.test-helper :as helper]))

(use-fixtures :once helper/with-instrumentation)

(deftest make-openai-client-test
  (testing "throws when api-endpoint is nil"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"API endpoint is required"
                          (llm/make-openai-client nil))))

  (testing "throws when api-endpoint is blank"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"API endpoint is required"
                          (llm/make-openai-client "   "))))

  (testing "uses default api-key when not provided"
    (let [client (llm/make-openai-client "http://localhost:8000/v1")]
      (is (= "sk-dummy" (:api-key client)))))

  (testing "uses custom api-key when provided"
    (let [client (llm/make-openai-client "http://localhost:8000/v1"
                                         :api-key "custom-key")]
      (is (= "custom-key" (:api-key client))))))
