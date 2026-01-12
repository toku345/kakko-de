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

# Run integration tests only (requires OPENAI_API_KEY)
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
- Clojure functions: hyphenated (`write-file`, `read-file`)

### Import Order & Formatting

- Organize requires alphabetically: external libs → clojure.* → project namespaces
- 2-space indentation, cljfmt defaults (run `clj -M:fmt/fix` before committing)
- Docstrings on public functions

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Functions | kebab-case | `execute-tool`, `read-file` |
| Protocols | PascalCase | `FileSystem`, `LLMClient` |
| Records | PascalCase | `OpenAIClient`, `RealFileSystem` |
| Constants | kebab-case | `default-model`, `available-tools` |
| Side-effect fns | suffix with `!` | `write-file!`, `read-file!` |
| Private fns | prefix with `-` | `(defn- validate-dir-path ...)` |

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

### Test Structure

| File | Purpose |
|------|---------|
| `test/coder_agent/core_test.clj` | Unit tests for core functionality |
| `test/coder_agent/tools_test.clj` | Tool and FileSystem tests |
| `test/coder_agent/integration_test.clj` | Real API tests (`^:integration`) |
| `test/coder_agent/test_helper.clj` | Test utilities |

### Design Principles

- **No `with-redefs`** - Avoid global state mutation for parallel test safety
- **Protocol/Record pattern** - Enables mock implementations
- **Test selector** - Integration tests use `^:integration` metadata
- **Inject mocks via keyword args:** `(tools/read-file {:file_path "test.txt"} :fs (mock-fs))`

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | API key | (required) |
| `OPENAI_API_ENDPOINT` | API endpoint | OpenAI default |
| `OPENAI_MODEL` | Model name | `gpt-5-mini` |
| `DEBUG` | Enable debug logging | `false` |

Setup: `cp .envrc.example .envrc && direnv allow`

## Important Notes

- openai-clojure uses `:api-endpoint` (not `:base-url`) for custom endpoints
- `tool-registry` keys must match `:name` in tool definitions exactly
- Tool names use underscores (OpenAI convention), Clojure functions use hyphens

## Git Conventions

- Commit messages and PR descriptions in English
- Conventional Commits format: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`
