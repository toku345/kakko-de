(ns coder-agent.llm
  "LLM client implementation."
  (:require [coder-agent.protocols :refer [LLMClient]]
            [wkok.openai-clojure.api :as openai]))

(defrecord OpenAIClient [api-key api-endpoint]
  LLMClient
  (chat-completion [_ request]
    (openai/create-chat-completion
     request
     (cond-> {:api-key api-key}
       api-endpoint (assoc :api-endpoint api-endpoint)))))

(defn make-openai-client
  "Create an OpenAIClient from environment variables."
  []
  (->OpenAIClient
   (System/getenv "OPENAI_API_KEY")
   (System/getenv "OPENAI_API_ENDPOINT")))

;; For testing
(defrecord MockLLMClient [response-fn]
  LLMClient
  (chat-completion [_ request]
    (response-fn request)))

(defn make-mock-client
  "Create a MockLLMClient with the given response function."
  [response-fn]
  (->MockLLMClient response-fn))
