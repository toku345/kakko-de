# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project Overview

kakko-de is a Clojure-based AI coding agent, inspired by Claude Code. Target models are OpenAI API-compatible LLMs, particularly the Qwen3-Coder series.

## Development Commands

```bash
# Run the application
clj -M:run

# Start a REPL
clj

# Run unit tests (default, excludes integration)
clj -X:test

# Run integration tests only (requires OPENAI_API_ENDPOINT)
clj -X:test :excludes '[]' :includes '[:integration]'

# Run all tests
clj -X:test :excludes '[]'

# Run tests for a single namespace
clj -X:test :nses '[coder-agent.tools-test]'

# Run a single test function
clj -X:test :vars '[coder-agent.tools-test/execute-tool-test]'

# Linting and formatting
clj -M:lint        # Run clj-kondo static analysis
clj -M:fmt/check   # Check formatting violations
clj -M:fmt/fix     # Auto-fix formatting
```

## Architecture

| Component | File | Description |
|-----------|------|-------------|
| Entry point | `src/coder_agent/core.clj` | `-main` function and chat loop |
| Protocols | `src/coder_agent/protocols.clj` | `LLMClient`, `FileSystem` protocol definitions |
| LLM Client | `src/coder_agent/llm.clj` | `OpenAIClient`, `MockLLMClient` implementations |
| Tools | `src/coder_agent/tools.clj` | Tool definitions and `execute-tool` dispatcher |
| Schemas | `src/coder_agent/schema.clj` | Malli schemas for ResultMap and ToolCall |
| Debug | `src/coder_agent/debug.clj` | Debug logging utilities |

## Code Style Guidelines

### Namespace Conventions

- Namespace: hyphenated (`coder-agent.tools`)
- File path: underscored (`src/coder_agent/tools.clj`)
- Tool names: underscored for OpenAI API (`"write_file"`, `"read_file"`)
- JSON keys: snake_case for OpenAI API compatibility (`file_path`, `dir_path`)
- Clojure functions: hyphenated (`write-file`, `read-file`)

### Error Handling

**Result Map Pattern** - Return maps instead of throwing:

```clojure
;; Success
{:success true :file_path "/path/to/file"}
{:success true :content "file contents"}

;; Failure
{:success false :error "Failed to read file: /path - No such file or directory"}
```

**Error Message Guidelines:**
- Include operation context: `"Failed to read file:"`
- Include the path/resource: `/path/to/file`
- Include the cause: `No such file or directory`
- Help LLM agents decide next actions

**Exception Boundaries:**
- Protocol implementations catch domain errors, return ResultMaps
- `execute-tool` is the fallback boundary for unexpected exceptions

### Protocol & Dependency Injection

- Define protocols in `protocols.clj` for testability
- Functions accept protocol instances as optional keyword args:

```clojure
(defn read-file
  [{:keys [file_path]} & {:keys [fs] :or {fs default-fs}}]
  (read-file! fs file_path))
```

### Schema Validation (Malli)

- Define schemas in `schema.clj`
- Annotate functions with `:malli/schema` metadata:

```clojure
(defn execute-tool
  {:malli/schema schema/ExecuteToolSchema}
  [tool-call & {:keys [tool-impls] :or {tool-impls tool-registry}}]
  ...)
```

- Instrumentation enabled in tests via `use-fixtures :once helper/with-instrumentation`
- NOT enabled in production

## Testing

- Integration tests use `^:integration` metadata (excluded by default)
- Inject mocks via keyword args: `(tools/read-file {:file_path "test.txt"} :fs (mock-fs))`

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_ENDPOINT` | API endpoint | (required) |
| `OPENAI_API_KEY` | API key | `sk-dummy` |
| `OPENAI_MODEL` | Model name | `gpt-5-mini` |
| `DEBUG` | Enable debug logging | `false` |
| `ECHO` | Show internal prompt (vLLM echo mode) | `false` |

Setup: `cp .envrc.example .envrc && direnv allow`

## Important Notes

- `tool-registry` keys must match `:name` in tool definitions exactly
- Tool names use underscores (OpenAI convention), Clojure functions use hyphens

## Git Conventions

- Commit messages and PR descriptions in English
- Conventional Commits format: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`
