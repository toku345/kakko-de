(ns coder-agent.output-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [coder-agent.output :as output]
            [coder-agent.test-helper :as helper]))

(use-fixtures :once helper/with-instrumentation)

(deftest extract-target-path-test
  (testing "read_file returns file_path"
    (is (= "/tmp/test.txt"
           (output/extract-target-path "read_file" {:file_path "/tmp/test.txt"}))))

  (testing "write_file returns file_path"
    (is (= "/out/file.txt"
           (output/extract-target-path "write_file" {:file_path "/out/file.txt"
                                                     :content "..."}))))

  (testing "list_dir returns dir_path"
    (is (= "/some/dir"
           (output/extract-target-path "list_dir" {:dir_path "/some/dir"}))))

  (testing "unknown tool returns nil"
    (is (nil? (output/extract-target-path "unknown_tool" {:foo "bar"})))))

(deftest format-tool-line-test
  (testing "success with file_path"
    (is (= "🔧 read_file: /tmp/test.txt ✓"
           (output/format-tool-line
            {:function {:name "read_file"
                        :arguments "{\"file_path\":\"/tmp/test.txt\"}"}}
            {:success true :content "file content"}))))

  (testing "success with dir_path"
    (is (= "🔧 list_dir: /some/dir ✓"
           (output/format-tool-line
            {:function {:name "list_dir"
                        :arguments "{\"dir_path\":\"/some/dir\"}"}}
            {:success true :listing "file1\nfile2"}))))

  (testing "failure with error message"
    (is (= "🔧 read_file: /tmp/missing.txt ✗ Error: File not found"
           (output/format-tool-line
            {:function {:name "read_file"
                        :arguments "{\"file_path\":\"/tmp/missing.txt\"}"}}
            {:success false :error "File not found"}))))
  (testing "unknown tool without path"
    (is (= "🔧 custom_tool: ✓"
           (output/format-tool-line
            {:function {:name "custom_tool"
                        :arguments "{\"data\":\"value\"}"}}
            {:success true}))))

  (testing "invalid JSON arguments handled gracefully"
    (is (= "🔧 read_file: ✓"
           (output/format-tool-line
            {:function {:name "read_file"
                        :arguments "invalid json"}}
            {:success true})))))
