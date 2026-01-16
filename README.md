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

#### Example Output

```bash
$ clj -M:run "Check the files under the test/ directory in this project and summarize their contents into test/README.md"
🤖 Thinking with tools..
🔧 list_dir: test/ ✓
🔧 list_dir: test/fixtures/ ✓
🔧 list_dir: test/coder_agent/ ✓
🔧 read_file: test/fixtures/sample.txt ✓
🔧 read_file: test/fixtures/another_sample.txt ✓
🔧 read_file: test/coder_agent/core_test.clj ✓
🔧 read_file: test/coder_agent/test_helper.clj ✓
🔧 read_file: test/coder_agent/integration_test.clj ✓
🔧 read_file: test/coder_agent/debug_test.clj ✓
🔧 read_file: test/coder_agent/output_test.clj ✓
🔧 read_file: test/coder_agent/tools_test.clj ✓
🔧 write_file: test/README.md ✓
Answer: I've analyzed the files in the `test/` directory and created a summary in `test/README.md`.
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | API key | (required) |
| `OPENAI_API_ENDPOINT` | API endpoint | OpenAI API |
| `OPENAI_MODEL` | Model name | `gpt-5-mini` |

### Development

```bash
# Run unit tests (default, excludes integration)
clj -X:test

# Run integration tests only (requires OPENAI_API_KEY)
clj -X:test :excludes '[]' :includes '[:integration]'

# Run all tests
clj -X:test :excludes '[]'

# Lint and format
clj -M:lint
clj -M:fmt/check
```

## License

MIT
