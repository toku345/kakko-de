(ns coder-agent.tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [coder-agent.tools :as tools :refer [FileSystem]]))

(defrecord MockFileSystem [calls]
  FileSystem
  (tools/write-file! [_ path content]
    (swap! calls conj {:path path :content content})
    {:success true :file_path path}))

(defn mock-fs []
  (->MockFileSystem (atom [])))

(deftest write-file!-test
  (testing "write-file! records calls with correct arguments."
    (let [fs (mock-fs)
          result (tools/write-file! fs "test.txt" "hello")]
      (is (= {:success true :file_path "test.txt"} result))
      (is (= [{:path "test.txt" :content "hello"}] @(:calls fs))))))

(deftest write-file-test
  (testing "write-file delegates to FileSystem protocol"
    (let [fs (mock-fs)
          result (tools/write-file {:file_path "test.txt" :content "hello"} :fs fs)]
      (is (= {:success true :file_path "test.txt"} result))
      (is (= [{:path "test.txt" :content "hello"}] @(:calls fs))))))

(deftest execute-tool-test (testing "execute-tool dispatches to correct tool."
                             (let [mock-write (fn [args] {:called-with args})
                                   tool-call {:function {:name "write_file"
                                                         :arguments "{\"file_path\":\"test.txt\",\"content\":\"hello\"}"}}
                                   result (tools/execute-tool tool-call :tool-impls {"write_file" mock-write})]
                               (is (= {:called-with {:file_path "test.txt" :content "hello"}} result))))

  (testing "execute-tool returns error for unknown tool."
    (let [tool-call {:function {:name "unknown_tool"
                                :arguments "{}"}}
          result (tools/execute-tool tool-call)]
      (is (= false (:success result)))
      (is (re-find #"Unknown tool" (:error result)))))

  (testing "execute-tool handles malformed JSON gracefully."
    (let [tool-call {:function {:name "write_file"
                                :arguments "not valid json"}}
          result (tools/execute-tool tool-call)]
      (is (= false (:success result)))
      (is (re-find #"Tool execution failed" (:error result))))))
