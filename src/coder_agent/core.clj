(ns coder-agent.core
  (:require [wkok.openai-clojure.api :as openai]))

(def config
  {:api-key      (System/getenv "OPENAI_API_KEY")
   :api-endpoint (System/getenv "OPENAI_API_ENDPOINT")})

(def model
  (or (System/getenv "OPENAI_MODEL")
      "gpt-5-mini"))

(defn chat [user-input]
  (println "🤖 Thinking..")
  (let [response (openai/create-chat-completion
                  {:model model
                   :messages [{:role "user" :content user-input}]}
                  config)]
    (-> response
        :choices
        first
        :message
        :content)))

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
