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

(def default-system-prompt
  "You are a helpful coding assistant. Use the provided tools to assist with coding tasks.")

(def default-client
  "Default LLM client created from environment variables."
  (delay (llm/make-vllm-client (System/getenv "OPENAI_API_ENDPOINT"))))

(def default-output-handlers
  "Default output handlers for chat functions."
  {:on-thinking (fn [] (println "🤖 Thinking..."))
   :on-tool-execution output/print-tool-execution})

(defn- safe-invoke
  "Invoke callback safely, printing warnings on error but not propagating them."
  [callback & args]
  (when callback
    (try
      (apply callback args)
      (catch Exception e
        (println (str "Warning: callback failed - " (.getMessage e)))))))

(defn format-tool-result-message
  [tool-call result]
  {:role "tool"
   :tool_call_id (:id tool-call)
   :content (json/generate-string result)})

(defn build-initial-messages
  [system-prompt user-input]
  [{:role "system" :content system-prompt}
   {:role "user" :content user-input}])

(defn append-turn
  [messages assistant-message tool-results]
  (-> messages
      (conj assistant-message)
      (into tool-results)))

(defn has-tool-calls?
  [message]
  (seq (:tool_calls message)))

(defn chat-step
  "Execute one LLM round-trip. Decoupled from loop control.
   Returns:
     {:status :continue :messages [...]} - continue with updated messages
     {:status :complete :content ...}    - final content (may be nil)"
  [client messages & {:keys [model tools execute-tool-fn on-tool-execution echo]
                      :or {model default-model
                           tools available-tools
                           execute-tool-fn tools/execute-tool
                           on-tool-execution (:on-tool-execution default-output-handlers)
                           echo false}}]
  (let [request (cond-> {:model model :messages messages :tools tools}
                  echo (assoc :echo true))
        _ (debug/log-request request)
        response (chat-completion client request)
        _ (debug/log-response response)
        _ (when echo
            (debug/log-internal-prompt (:prompt_logprobs response)))
        message (-> response :choices first :message)]
    (if (has-tool-calls? message)
      (let [tools-results (mapv
                           (fn [tc]
                             (let [result (execute-tool-fn tc)]
                               (debug/log-tool-execution tc result)
                               (safe-invoke on-tool-execution tc result)
                               (format-tool-result-message tc result)))
                           (:tool_calls message))]
        {:status :continue
         :messages (append-turn messages message tools-results)})
      {:status :complete
       :content (:content message)})))

(defn chat
  "Send a message to the LLM and return the response content.
   Orchestrates the chat loop with injected dependencies.

   Options:
     :execute-tool-fn   - Function to execute tools (default: tools/execute-tool)
     :tools             - Available tools (default: available-tools)
     :model             - Model name (default: default-model)
     :max-iterations    - Max tool loop iterations (default: 30)
     :system-prompt     - System prompt (default: default-system-prompt)
     :on-thinking       - Callback when thinking starts. Pass nil to disable. (default: prints emoji)
     :on-tool-execution - Callback (fn [tool-call result]) after tool execution. Pass nil to disable. (default: print-tool-execution)"
  [client user-input & {:keys [execute-tool-fn tools model max-iterations system-prompt
                               on-thinking on-tool-execution echo]
                        :or {execute-tool-fn tools/execute-tool
                             tools available-tools
                             model default-model
                             max-iterations 30
                             system-prompt default-system-prompt
                             on-thinking (:on-thinking default-output-handlers)
                             on-tool-execution (:on-tool-execution default-output-handlers)
                             echo false}}]
  (safe-invoke on-thinking)
  (loop [messages (build-initial-messages system-prompt user-input)
         iteration 0]
    (when (>= iteration max-iterations)
      (throw (ex-info "Max tool iterations exceeded." {:iterations iteration})))
    (let [result (chat-step client messages
                            :model model
                            :tools tools
                            :execute-tool-fn execute-tool-fn
                            :on-tool-execution on-tool-execution
                            :echo echo)]
      (case (:status result)
        :continue (recur (:messages result) (inc iteration))
        :complete (:content result)))))

(defn -main [& args]
  (let [input (first args)]
    (if input
      (println "Answer:" (chat @default-client input :echo true))
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

(comment
  (def vllm-client (llm/make-vllm-client "http://localhost:8000/v1"))

  (def response (chat-completion vllm-client
                                 {:model "Qwen/Qwen3-Coder-30B-A3B-Instruct"
                                  :messages [{:role "system"
                                              :content "You are a helpful coding assistant."}
                                             {:role "user"
                                              :content "What is Clojure?"}]
                                  :echo true}))

  (debug/log-internal-prompt (:prompt_logprobs response)))
