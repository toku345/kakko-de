(ns coder-agent.protocols
  "Protocols for dependency injection and testability.")

(defprotocol FileSystem
  "Protocol for file system operations."
  (write-file! [this path content]
    "Writes content to a file at the specific path.
     Returns a ResultMap: {:success true :file_path path} or {:success false :error msg}"))

(defprotocol LLMClient
  "Protocol for LLM API integrations."
  (chat-completion [this request]
    "Send a chat completion request and return the response.
     request: {:model string, :messages vector, :tools vector (optional)}
     Returns: OpenAI API compatible response map"))
