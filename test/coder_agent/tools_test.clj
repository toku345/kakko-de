(ns coder-agent.tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [coder-agent.tools :as tools]))

(deftest write-file-test
  (testing "write-file writes content to specified file"
    (let [file-path "test_output.txt"
          content "This is a test content!?"
          result (tools/write-file {:file_path file-path :content content})]
      (is (= {:success true :file_path file-path} result)))))
