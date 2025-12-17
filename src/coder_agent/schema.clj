(ns coder-agent.schema
  "Malli schemas for coder-agent contracts."
  (:require [malli.core :as m]))

;; === Result Map Schemas ===

(def SuccessResult
  "Schema for successful operation results."
  [:map
   [:success [:= true]]])

(def FailureResult
  "Schema for failed operation results."
  [:map
   [:success [:= false]]
   [:error :string]])

(def ResultMap
  "Discriminated union schema for operation results."
  [:multi {:dispatch :success}
   [true SuccessResult]
   [false FailureResult]])

;; === Tool Call Schemas ===

(def ToolCallFunction
  "Schema for the :function value within a tool call.
   Contains the tool name and JSON-encoded arguments."
  [:map
   [:name :string]
   [:arguments :string]])

(def ToolCall
  "Schema for LLM tool call structure.
   Wraps ToolCallFunction in a :function key."
  [:map
   [:function ToolCallFunction]])

;; === Function Schemas ===

(def ExecuteToolSchema
  "Function schema for execute-tool: ToolCall -> ResultMap"
  [:=> [:cat ToolCall [:* :any]] ResultMap])

;; === Validation Helpers ===

(defn valid-result?
  "Check if value conforms to ResultMap schema."
  [value]
  (m/validate ResultMap value))

(defn explain-result
  "Explain why value doesn't conform to ResultMap schema."
  [value]
  (m/explain ResultMap value))
