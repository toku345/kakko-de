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

# Run tests (when added)
clj -M:test

# Linting and formatting
clj -M:lint        # Run clj-kondo static analysis
clj -M:fmt/check   # Check formatting violations
clj -M:fmt/fix     # Auto-fix formatting
```

## Architecture

The project uses Clojure CLI (deps.edn) for dependency management.

**Entry point:** `src/coder_agent/core.clj` - Contains the `-main` function

The namespace convention is `coder-agent.*` (hyphenated in namespace declarations, underscored in file paths).

## Tech Stack

- **Language:** Clojure
- **Build:** Clojure CLI (deps.edn)
- **LLM Client:** openai-clojure
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

## Git Conventions

- Commit messages and PR descriptions in English
- Follow Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`)
- Branch naming: `{type}/{short-description}` (e.g., `feat/add-auth`, `fix/null-pointer`)
