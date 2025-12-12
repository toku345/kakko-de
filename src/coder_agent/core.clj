(ns coder-agent.core
  (:require [wkok.openai-clojure.api :as openai]))

(def model
  "Qwen/Qwen3-Coder-30B-A3B-Instruct")

(def config
  {:api-key "sk-dummy"
   :api-endpoint "http://localhost:8000/v1"})


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
