# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

kakko-de is a Clojure-based AI coding agent, inspired by Claude Code. The target models are OpenAI API-compatible LLMs, particularly the Qwen3-Coder series.

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

# Run specific tests
clj -X:test :nses '[coder-agent.core-test]'
clj -X:test :vars '[coder-agent.core-test/chat-test]'

# Linting and formatting
clj -M:lint        # Run clj-kondo static analysis
clj -M:fmt/check   # Check formatting violations
clj -M:fmt/fix     # Auto-fix formatting
```

## Architecture

The project uses Clojure CLI (deps.edn) for dependency management.

**Entry point:** `src/coder_agent/core.clj` - Contains the `-main` function and chat loop

**Tools:** `src/coder_agent/tools.clj` - Tool definitions and execution dispatcher

The namespace convention is `coder-agent.*` (hyphenated in namespace declarations, underscored in file paths).

## Tech Stack

- **Language:** Clojure
- **Build:** Clojure CLI (deps.edn)
- **LLM Client:** openai-clojure
- **JSON:** cheshire
- **Testing:** cognitect-labs/test-runner, matcher-combinators
- **Linting:** clj-kondo
- **Formatting:** cljfmt

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | API key | (required) |
| `OPENAI_API_ENDPOINT` | API endpoint | OpenAI API |
| `OPENAI_MODEL` | Model name | `gpt-5-mini` |

Setup: `cp .envrc.example .envrc && direnv allow`

## REPL Development

Rich comment in `src/coder_agent/core.clj` contains config for local Qwen3-Coder.
Evaluate the `def` forms to override settings at runtime.

## Notes

- openai-clojure uses `:api-endpoint` (not `:base-url`) for custom endpoints

## Testing

### Design Principles

- **Protocol/Record pattern:** `FileSystem` protocol enables mock implementations for testing without file I/O
- **Dependency Injection:** Functions accept optional `:call-llm-fn`, `:execute-tool-fn` parameters for testability
- **No `with-redefs`:** Avoid global state mutation for parallel test safety
- **Test selector:** Integration tests use `^:integration` metadata and are excluded by default

### Test Structure

- `test/coder_agent/core_test.clj` - Unit tests + mock tests
- `test/coder_agent/integration_test.clj` - Real API tests (`^:integration` tagged, excluded by default)

## Error Handling

### Design Principles

- **Boundary-only catch:** `try/catch` is used only at system boundaries (e.g., `execute-tool`)
- **Result map pattern:** Functions that can fail return `{:success true/false ...}` instead of throwing
- **Contract:** `execute-tool` always returns a Result map, never throws exceptions

### Result Map Structure

```clojure
;; Success
{:success true :file_path "/path/to/file" ...}

;; Failure
{:success false :error "Error message"}
```

### Guidelines

| Layer | Error Handling |
|-------|----------------|
| Internal functions (e.g., `write-file!`) | Let exceptions propagate |
| Boundary functions (e.g., `execute-tool`) | Catch and convert to Result map |
| Callers of boundary functions | No try/catch needed; check `:success` key |

## Schema Validation (Malli)

### Overview

This project uses [malli](https://github.com/metosin/malli) for runtime schema validation of function contracts.

### ResultMap Schema

All tool execution functions return a `ResultMap`:

```clojure
;; Success case
{:success true :file_path "/path/to/file" ...}

;; Failure case
{:success false :error "Error message"}
```

The schema enforces:
- `:success` key is always required (boolean)
- When `:success` is `false`, `:error` key is required (string)

### Development Mode

Start REPL with `:dev` alias and enable instrumentation:

```bash
clj -A:dev
```

```clojure
(require '[user])
(user/start-instrumentation!)
```

This enables runtime validation of function schemas with pretty error reporting.

### Testing

Instrumentation is automatically enabled for all tests via `use-fixtures :once`.
Schema violations will cause test failures with detailed error messages.

### Production

Instrumentation is NOT enabled in production. Schemas serve as documentation
and are validated only during development and testing.

## Git Conventions

- Commit messages and PR descriptions in English
- Follow Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`)
- Branch naming: `{type}/{short-description}` (e.g., `feat/add-auth`, `fix/null-pointer`)
