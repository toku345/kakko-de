(ns coder-agent.llm
  "LLM client implementation."
  (:require
   [clojure.string :as string]
   [coder-agent.protocols :refer [LLMClient]]
   [wkok.openai-clojure.api :as openai]
   [clj-http.lite.client :as http]
   [cheshire.core :as json]))

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

(defrecord VLLMClient [api-key api-endpoint]
  LLMClient
  (chat-completion [_ request]
    (let [url (str api-endpoint "/chat/completions")
          response (http/post url
                              {:headers {"Authorization" (str "Bearer " api-key)
                                         "Content-Type" "application/json"}
                               :body (json/generate-string request)
                               :throw-exceptions true})]
      (json/parse-string (:body response) true))))

(defn make-vllm-client
  "Create a VLLMClient for vLLM endpoints
   api-key defaults to \"sk-dummy\" for local vLLM."
  [api-endpoint & {:keys [api-key] :or {api-key "sk-dummy"}}]
  (when (or (nil? api-endpoint) (string/blank? api-endpoint))
    (throw (ex-info "API endpoint is required for VLLMClient"
                    {:hint "Provide endpoint like http://localhost:8000/v1"})))
  (->VLLMClient api-key api-endpoint))
