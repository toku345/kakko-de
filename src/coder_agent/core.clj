(ns coder-agent.core
  (:require [wkok.openai-clojure.api :as openai]))

(def config
  (cond-> {:api-key      (System/getenv "OPENAI_API_KEY")}
    (some? (System/getenv "OPENAI_API_ENDPOINT"))
    (assoc :api-endpoint (System/getenv "OPENAI_API_ENDPOINT"))))

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
     :call-llm-fn - Function to call LLM (default: default-call-llm)"
  [user-input & {:keys [call-llm-fn] :or {call-llm-fn default-call-llm}}]
  (println "🤖 Thinking..")
  (let [request {:model model
                 :messages [{:role "user" :content user-input}]}
        response (call-llm-fn request config)]
    (extract-content response)))

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

  (chat "What is Clojure?"))
