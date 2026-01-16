(ns coder-agent.core
  (:require [cheshire.core :as json]
            [coder-agent.protocols :refer [chat-completion]]
            [coder-agent.llm :as llm]
            [coder-agent.tools :as tools]
            [coder-agent.debug :as debug]
            [coder-agent.output :as output]))

(def default-model
  (or (System/getenv "OPENAI_MODEL")
      "gpt-5-mini"))

(def available-tools [tools/write-tool tools/read-tool tools/list-dir-tool])

(def system-prompt
  "System prompt for the AI assistant."
  "You are a helpful coding assistant. Use the provided tools to assist with coding tasks.")

(def default-client
  "Default LLM client created from environment variables."
  (delay (llm/make-openai-client)))

(defn chat
  "Send a message to the LLM and return the response content.
   client: LLMClient instance
   user-input: User's input string
   Options:
     :execute-tool-fn - Function to execute tools (default: tools/execute-tool)
     :tools - Available tools (default: available-tools)
     :model - Model name (default: default-model)"
  [client user-input & {:keys [execute-tool-fn tools model]
                        :or {execute-tool-fn tools/execute-tool
                             tools available-tools
                             model default-model}}]
  (println "🤖 Thinking with tools..")
  (loop [messages [{:role "system" :content system-prompt}
                   {:role "user" :content user-input}]
         iteration 0]
    (when (>= iteration 30)
      (throw (ex-info "Max tool iterations exceeded." {:iterations iteration})))
    (let [request {:model model :messages messages :tools tools}
          _ (debug/log-request request)
          response (chat-completion client request)
          _ (debug/log-response response)
          message (-> response :choices first :message)
          tool-calls (:tool_calls message)]
      (if (seq tool-calls)
        (let [tools-results (mapv (fn [tc]
                                    (let [result (execute-tool-fn tc)]
                                      (debug/log-tool-execution tc result)
                                      (output/print-tool-execution tc result)
                                      {:role "tool"
                                       :tool_call_id (:id tc)
                                       :content (json/generate-string result)}))
                                  tool-calls)
              updated (-> messages
                          (conj message)
                          (into tools-results))]
          (recur updated (inc iteration)))
        (:content message)))))

(defn -main [& args]
  (let [input (first args)]
    (if input
      (println "Answer:" (chat @default-client input))
      (println "Please input your question as the first argument."))))

(comment
  ;; REPL: Local Qwen3-Coder configuration
  (def client (llm/->OpenAIClient "sk-dummy" "http://localhost:8000/v1"))

  (chat client "What is Clojure?" :model "Qwen/Qwen3-Coder-30B-A3B-Instruct")
  (chat client "Write \"Hello, World!\" to a file named test_output_hello.txt"
        :model "Qwen/Qwen3-Coder-30B-A3B-Instruct")
  (chat client "Read the content of test/fixtures/sample.txt"
        :model "Qwen/Qwen3-Coder-30B-A3B-Instruct")
  (chat client "Read the number from test_output_count.txt and increment it by 1, then write it back."
        :model "Qwen/Qwen3-Coder-30B-A3B-Instruct"))
