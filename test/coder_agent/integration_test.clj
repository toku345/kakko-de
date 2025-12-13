(ns coder-agent.integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [coder-agent.core :as core]))

(deftest ^:integration real-api-test
  (testing "Real API call returns non-empty response."
    (is (some? (System/getenv "OPENAI_API_KEY"))
        "OPENAI_API_KEY must be set for integration tests.")
    (let [response (core/chat "Say hello in one word.")]
      (is (string? response))
      (is (pos? (count response))))))
