# Claude Mock

A standalone mock implementation of Claude CLI that replays conversations from log files. This tool allows developers to test pocket_agent server and develop frontends without consuming API tokens or incurring costs.

## Overview

Claude Mock reads conversation logs in JSONL format and replays them as if they were real Claude responses. It accepts the same command-line arguments as the real Claude CLI, making it a drop-in replacement for development and testing.

## Features

- **Full CLI Compatibility**: Accepts all Claude CLI arguments (`-c`, `-p`, `--verbose`, `--output-format`, etc.)
- **Log-based Replay**: Replays conversations from JSONL log files
- **Realistic Timing**: Configurable delays between messages to simulate real response times
- **Session Support**: Maintains session continuity using the `-c` flag
- **Streaming Output**: Outputs messages in Claude's streaming JSON format
- **Zero API Cost**: Perfect for development, testing, and demos

## Installation

```bash
cd claude-mock
go build -o claude-mock .
```

## Usage

### Basic Usage

```bash
# Set the log file to replay
export CLAUDE_MOCK_LOG_FILE=/path/to/messages.jsonl

# Run the mock (reads prompt from stdin)
echo "Hello" | ./claude-mock -p
```

### With Pocket Agent Server

```bash
# Start the server with the mock
./server --claude-path ./claude-mock

# Or set in config
{
  "execution": {
    "claude_binary_path": "/path/to/claude-mock"
  }
}
```

## Configuration

### Environment Variables

- `CLAUDE_MOCK_LOG_FILE`: Path to the JSONL log file containing the conversation (defaults to `conversations/hello.jsonl`)
- `CLAUDE_MOCK_DELAY_MS`: Delay between messages in milliseconds (default: 100)
- `CLAUDE_MOCK_SPEED`: Speed multiplier for playback (e.g., 2.0 for 2x speed, 0.5 for half speed)

### Log File Format

The log file should be in JSONL format with each line containing:

```json
{"timestamp":"2025-08-01T07:08:08.578057+02:00","message":{...},"direction":"claude"}
```

The mock extracts the `message` field and outputs it in Claude's streaming format.

## Command Line Arguments

The mock accepts all standard Claude CLI arguments:

- `-c SESSION_ID`: Resume a specific session
- `-p`: Print response and exit (non-interactive mode)
- `--verbose`: Enable verbose output
- `--output-format stream-json`: Output format (always uses stream-json)
- `--model MODEL`: Model selection (ignored, for compatibility)
- `--dangerously-skip-permissions`: Skip permission checks (ignored)

## Examples

### Simple Replay

```bash
# Use default hello conversation
echo "Hi" | ./claude-mock -p

# Or specify a different conversation
export CLAUDE_MOCK_LOG_FILE=./conversations/full_project.jsonl
echo "What's the weather?" | ./claude-mock -p
```

### Session Continuity

```bash
# First call creates session
echo "Hello" | ./claude-mock -p
# Output includes session ID in system message

# Continue session
echo "Follow up question" | ./claude-mock -c session-123 -p
```

### Custom Timing

```bash
# Slow playback (500ms between messages)
export CLAUDE_MOCK_DELAY_MS=500

# Fast playback (2x speed)
export CLAUDE_MOCK_SPEED=2.0
```

## Development

### Building

```bash
make build
```

### Testing

```bash
make test
```

### Creating Test Logs

You can create test logs from actual Claude conversations:

1. Run pocket_agent server with real Claude
2. Find the conversation logs in `projects/PROJECT_ID/logs/`
3. Use the `messages_*.jsonl` files as input

## Included Conversations

The module includes sample conversations in the `conversations/` directory:

- `hello.jsonl` - Simple greeting conversation (default)
- `full_project.jsonl` - Complete tomato tracker project creation

## How It Works

1. On startup, the mock loads the specified log file (or defaults to `conversations/hello.jsonl`)
2. It parses each line to extract Claude messages
3. When executed, it replays messages sequentially:
   - Outputs each message in streaming JSON format
   - Waits for the configured delay between messages (default: 100ms)
   - Exits after replaying all messages for the conversation

## Limitations

- Replays conversations sequentially (no prompt matching)
- Each execution replays from the beginning
- No persistent state between executions
- Ignores actual prompt content (replays predetermined responses)

## Troubleshooting

### No Output

Check that:
- `CLAUDE_MOCK_LOG_FILE` is set and points to a valid file
- The log file contains Claude messages (`"direction":"claude"`)
- The file is in proper JSONL format

### Unexpected Responses

The mock replays conversations in order. If responses seem mismatched:
- Verify you're using the correct log file
- Check if the conversation flow matches your test scenario
- Consider creating a custom log file for your specific test case

## License

Part of the pocket_agent project. See main repository for license details.