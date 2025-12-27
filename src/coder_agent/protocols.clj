(ns coder-agent.protocols
  "Protocols for dependency injection and testability.")

(defprotocol FileSystem
  "Protocol for file system operations."
  (write-file! [this path content]
    "Writes content to a file at the specified path.
     Returns a ResultMap: {:success true :file_path path} on success.
     May throw Exception on failure (e.g., IOException, SecurityException).")
  (read-file! [this path]
    "Reads content from a file at the specified path.
     Returns a ResultMap: {:success true :content content} on success.
     May throw Exception on failure (e.g., IOException, SecurityException)."))

(defprotocol LLMClient
  "Protocol for LLM API integrations."
  (chat-completion [this request]
    "Send a chat completion request and return the response.
     request: {:model string, :messages vector, :tools vector (optional)}
     Returns: OpenAI API compatible response map
     Throws: May throw on API errors (network, auth, rate limit, etc.)"))
