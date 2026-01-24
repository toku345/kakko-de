(ns coder-agent.llm
  "LLM client implementation."
  (:require
   [clojure.string :as string]
   [coder-agent.protocols :refer [LLMClient]]
   [clj-http.lite.client :as http]
   [cheshire.core :as json]))

;; For testing
(defrecord MockLLMClient [response-fn]
  LLMClient
  (chat-completion [_ request]
    (response-fn request)))

(defn make-mock-client
  "Create a MockLLMClient with the given response function."
  [response-fn]
  (->MockLLMClient response-fn))

(defrecord OpenAIClient [api-key api-endpoint]
  LLMClient
  (chat-completion [_ request]
    (let [url (str api-endpoint "/chat/completions")]
      (try
        (let [response (http/post url
                                  {:headers {"Authorization" (str "Bearer " api-key)
                                             "Content-Type" "application/json"}
                                   :body (json/generate-string request)
                                   :throw-exceptions true
                                   ;; socket-timeout is longer to accommodate LLM generation latency
                                   :conn-timeout 10000
                                   :socket-timeout 60000})]
          (json/parse-string (:body response) true))
        (catch Exception e
          (throw (ex-info "LLM API request failed"
                          {:url url
                           :model (:model request)
                           :message-count (count (:messages request))
                           :exception-type (type e)
                           :cause (.getMessage e)} e)))))))

(defn make-openai-client
  "Create an OpenAIClient for OpenAI-compatible endpoints
   api-key defaults to \"sk-dummy\" for local vLLM."
  [api-endpoint & {:keys [api-key] :or {api-key "sk-dummy"}}]
  (when (or (nil? api-endpoint) (string/blank? api-endpoint))
    (throw (ex-info "API endpoint is required for OpenAIClient"
                    {:hint "Provide endpoint like http://localhost:8000/v1"})))
  (->OpenAIClient api-key api-endpoint))
