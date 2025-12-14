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
- **Pure function separation:** `extract-content` is pure, `default-call-llm` handles side effects
- **Test selector:** Integration tests use `^:integration` metadata and are excluded by default

### Test Structure

- `test/coder_agent/core_test.clj` - Unit tests + mock tests
- `test/coder_agent/integration_test.clj` - Real API tests (`^:integration` tagged, excluded by default)

## Git Conventions

- Commit messages and PR descriptions in English
- Follow Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`)
- Branch naming: `{type}/{short-description}` (e.g., `feat/add-auth`, `fix/null-pointer`)
