(ns coder-agent.integration-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [coder-agent.core :as core]
   [coder-agent.llm :as llm]))

(deftest ^:integration real-api-test
  (testing "Real API call returns non-empty response."
    (is (some? (System/getenv "OPENAI_API_KEY"))
        "OPENAI_API_KEY must be set for integration tests.")
    (let [client (llm/make-openai-client)
          response (core/chat client "Say hello in one word.")]
      (is (string? response))
      (is (pos? (count response))))))
