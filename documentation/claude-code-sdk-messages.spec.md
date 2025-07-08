# Claude Code SDK Message Schema Specification

## Overview

This document describes the actual message format used by Claude Code SDK, based on the official documentation at https://docs.anthropic.com/en/docs/claude-code/sdk#message-schema

## Message Types from Claude Code SDK

Claude Code sends messages via JSON API with the following strictly typed schema:

### 1. Assistant Message
```typescript
{
  type: "assistant";
  message: Message;      // Anthropic SDK Message type
  session_id: string;
}
```
- Contains Claude's actual responses
- The `message` field follows Anthropic's Message type structure

### 2. User Message
```typescript
{
  type: "user";
  message: MessageParam; // Anthropic SDK MessageParam type
  session_id: string;
}
```
- Represents user input echoed back
- Used for conversation tracking

### 3. Result Message - Success
```typescript
{
  type: "result";
  subtype: "success";
  duration_ms: float;
  duration_api_ms: float;
  is_error: boolean;
  num_turns: int;
  result: string;
  session_id: string;
  total_cost_usd: float;
}
```
- Sent as the final message when operation completes successfully
- Contains execution metrics and cost information

### 4. Result Message - Error
```typescript
{
  type: "result";
  subtype: "error_max_turns" | "error_during_execution";
  duration_ms: float;
  duration_api_ms: float;
  is_error: boolean;
  num_turns: int;
  session_id: string;
  total_cost_usd: float;
}
```
- Sent when hitting turn limit or execution error
- `is_error` will be true
- No `result` field in error case

### 5. System Message - Initialization
```typescript
{
  type: "system";
  subtype: "init";
  apiKeySource: string;
  cwd: string;
  session_id: string;
  tools: string[];
  mcp_servers: {
    name: string;
    status: string;
  }[];
  model: string;
  permissionMode: "default" | "acceptEdits" | "bypassPermissions" | "plan";
}
```
- First message in a conversation
- Provides session configuration and available tools
- Shows MCP (Model Context Protocol) server status

## Anthropic SDK Message Types

The `message` field in assistant messages follows the Anthropic SDK structure:

### Message Structure
```typescript
interface Message {
  id: string;
  type: 'message';
  role: 'assistant';
  content: Array<TextBlock | ToolUseBlock>;
  model: string;
  stop_reason: 'end_turn' | 'max_tokens' | 'stop_sequence' | 'tool_use' | null;
  stop_sequence: string | null;
  usage: {
    input_tokens: number;
    output_tokens: number;
  };
}
```

### Content Block Types

#### TextBlock
```typescript
{
  type: 'text';
  text: string;
}
```

#### ToolUseBlock
```typescript
{
  type: 'tool_use';
  id: string;
  name: string;
  input: object;
}
```

## Key Observations

1. **No Built-in Progress Tracking**: Claude Code SDK doesn't provide structured progress messages like `ProgressUpdateMessage`
2. **Tool Usage**: Claude communicates tool usage through `ToolUseBlock` in content
3. **Session-based**: All messages include `session_id` for tracking
4. **Turn-based**: Uses `num_turns` to track conversation progress
5. **Cost Tracking**: Includes API usage costs in result messages

## Implications for Wrapper Service

The wrapper service must:

1. **Parse Text Content**: Extract progress information from `TextBlock` content
   - Look for patterns like "Running test X of Y"
   - Detect sub-agent mentions in text
   - Parse command outputs

2. **Monitor Tool Usage**: Track `ToolUseBlock` to understand operations
   - File operations
   - Command execution
   - Code modifications

3. **Create Structured Messages**: Transform Claude's unstructured output into the mobile app's expected format:
   - Convert text mentions of progress into `ProgressUpdateMessage`
   - Parse sub-agent mentions into `SubAgentProgress`
   - Create `PermissionRequest` from tool usage

4. **Session Management**: Map Claude's `session_id` to mobile app's project concept

## Example Translation

### Claude Code Output:
```json
{
  "type": "assistant",
  "message": {
    "content": [{
      "type": "text",
      "text": "I'll run the test suite now. Running test 5 of 20..."
    }]
  },
  "session_id": "sess_123"
}
```

### Wrapper Translation to Mobile:
```json
{
  "type": "claude_response",
  "content": "I'll run the test suite now. Running test 5 of 20...",
  "conversationId": "sess_123"
}

// Plus a separate progress message:
{
  "type": "progress_update",
  "currentStep": "Running tests",
  "completedSteps": 5,
  "totalSteps": 20,
  "percentage": 25.0
}
```

## Conclusion

The mobile app's message protocol is an abstraction layer that the wrapper service must implement by:
- Parsing Claude's text output
- Tracking tool usage
- Maintaining session state
- Creating structured progress updates from unstructured text

This explains why components like `SubAgentProgressMonitor` and `ProgressParser` exist - they're parsing Claude's natural language output, not receiving structured progress data.