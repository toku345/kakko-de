# kakko-de

AI coding agent written in Clojure.

## Usage

### Setup

```bash
cp .envrc.example .envrc
# Edit .envrc with your settings
direnv allow
```

### Run

```bash
clj -M:run "Your question here"
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | API key | (required) |
| `OPENAI_API_ENDPOINT` | API endpoint | OpenAI API |
| `OPENAI_MODEL` | Model name | `gpt-5-mini` |

### Development

```bash
# Run tests
clj -X:test

# Lint and format
clj -M:lint
clj -M:fmt/check
```

## License

MIT
