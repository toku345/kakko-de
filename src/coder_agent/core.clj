(ns coder-agent.core
  (:require [wkok.openai-clojure.api :as openai]
            [cheshire.core :as json]
            [coder-agent.tools :as tools]))

(def config
  (cond-> {:api-key      (System/getenv "OPENAI_API_KEY")}
    (some? (System/getenv "OPENAI_API_ENDPOINT"))
    (assoc :api-endpoint (System/getenv "OPENAI_API_ENDPOINT"))))

(def available-tools [tools/write-tool])

(def model
  (or (System/getenv "OPENAI_MODEL")
      "gpt-5-mini"))

(defn extract-content
  "Extract content from LLM response."
  [response]
  (-> response
      :choices
      first
      :message
      :content))

(defn default-call-llm
  "Default LLM call function using openai-clojure."
  [request config]
  (openai/create-chat-completion request config))

(defn chat
  "Send a message to the LLM and return the response content.
   Options:
     :call-llm-fn - Function to call LLM (default: default-call-llm)
     :execute-tool-fn - Function to execute tools (default: tools/execute-tool)
                        Should return a map with :success key. Should not throw.
     :tools - Available tools (default: available-tools)"
  [user-input & {:keys [call-llm-fn execute-tool-fn tools]
                 :or {call-llm-fn default-call-llm
                      execute-tool-fn tools/execute-tool
                      tools available-tools}}]
  (println "🤖 Thinking with tools..")
  (loop [messages [{:role "user" :content user-input}]
         iteration 0]
    (when (>= iteration 10)
      (throw (ex-info "Max tool iterations exceeded." {:iterations iteration})))
    (let [request {:model model :messages messages :tools tools}
          response (call-llm-fn request config)
          message (-> response :choices first :message)
          tool-calls (:tool_calls message)]
      (if (seq tool-calls)
        (do
          (println "🔧 Executing tools..")
          (let [tools-results (for [tc tool-calls]
                                (let [result (try
                                               (execute-tool-fn tc)
                                               (catch Exception e
                                                 {:success false
                                                  :error (.getMessage e)}))]
                                  {:role "tool"
                                   :tool_call_id (:id tc)
                                   :content (json/generate-string result)}))
                updated (-> messages
                            (conj message)
                            (into tools-results))]
            (recur updated (inc iteration))))
        (:content message)))))

(defn -main [& args]
  (let [input (first args)]
    (if input
      (println "Answer:" (chat input))
      (println "Please input your question as the first argument."))))

(comment
  ;; REPL: Local Qwen3-Coder configuration
  (def config
    {:api-key      "sk-dummy"
     :api-endpoint "http://localhost:8000/v1"})

  (def model "Qwen/Qwen3-Coder-30B-A3B-Instruct")

  (chat "What is Clojure?")
  (chat "Write \"Hello, World!\" to a file named hello.txt"))
