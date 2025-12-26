(ns coder-agent.llm
  "LLM client implementation."
  (:require
   [clojure.string :as string]
   [coder-agent.protocols :refer [LLMClient]]
   [wkok.openai-clojure.api :as openai]))

(defrecord OpenAIClient [api-key api-endpoint]
  LLMClient
  (chat-completion [_ request]
    (openai/create-chat-completion
     request
     (cond-> {:api-key api-key}
       api-endpoint (assoc :api-endpoint api-endpoint)))))

(defn make-openai-client
  "Create an OpenAIClient from environment variables.
   Throws if OPENAI_API_KEY is not set."
  []
  (let [api-key (System/getenv "OPENAI_API_KEY")]
    (when (or (nil? api-key) (string/blank? api-key))
      (throw (ex-info "OPENAI_API_KEY environment variable is required"
                      {:env-var "OPENAI_API_KEY"
                       :hint "Set the OPENAI_API_KEY. See .envrc.example"})))
    (->OpenAIClient api-key (System/getenv "OPENAI_API_ENDPOINT"))))

;; For testing
(defrecord MockLLMClient [response-fn]
  LLMClient
  (chat-completion [_ request]
    (response-fn request)))

(defn make-mock-client
  "Create a MockLLMClient with the given response function."
  [response-fn]
  (->MockLLMClient response-fn))
