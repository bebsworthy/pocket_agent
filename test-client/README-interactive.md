# Interactive Test Client

The interactive scenario provides a CLI interface for testing the Pocket Agent server interactively.

## Running the Interactive Scenario

```bash
cd test-client
npm install  # If not already done
npm run interactive
```

Or directly:
```bash
npx ts-node src/scenarios/interactive.ts
```

## Features

### 1. Project Setup
On startup, the interactive client will:
- Ask for a project path (defaults to a temp directory)
- Ask whether to create a new project or use an existing one
- If using existing, prompt for the project ID
- Automatically join the project

### 2. Interactive Commands

When the project is **IDLE** (indicated by a green ● in the prompt):
- Type any text to send it as a prompt to Claude
- Claude's responses will be displayed in real-time

Available commands:
- `help` - Show available commands
- `status` - Show current project status (ID, path, state, connection, permission mode)
- `clear` - Clear the screen
- `json on` - Enable raw JSON message display
- `json off` - Disable raw JSON message display (default)
- `permission` or `mode` - Show current permission mode and available modes
- `mode <mode>` - Set permission mode (default, acceptEdits, bypass, plan)
- `exit` or `quit` - Exit the program

**Permission Modes:**
- `default` - Standard permission handling
- `acceptEdits` - Automatically accept file edits
- `bypass` or `bypassPermissions` - Skip all permission prompts
- `plan` - Enable planning mode

**Quick Mode Switching:**
- Press `Tab` to cycle through permission modes

### 3. Visual Indicators

The prompt shows the current state with colored indicators:
- 🟢 Green (●) - IDLE: Ready to accept prompts
- 🟡 Yellow (●) - EXECUTING: Claude is processing
- 🔴 Red (●) - ERROR or UNKNOWN state

### 4. Message Display

By default, messages are formatted for readability:
- Claude's responses are shown as streaming text
- Errors are highlighted in red
- System messages are shown in gray

With `json on`, all raw WebSocket messages are displayed as formatted JSON.

## Example Session

```
=== Pocket Agent Interactive CLI ===

Type "help" for available commands

Connecting to WebSocket server...
✓ Connected successfully

Enter project path (or press Enter for temp directory): ~/my-project
Create new project? (y/n): y
Creating directory: /Users/username/my-project
Creating project...
✓ Project created with ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
Joining project...
✓ Joined project successfully

● claude> Write a haiku about coding

🤖 Claude is thinking...

Code flows like water
Bugs hide in syntax shadows
Coffee fuels the mind

✅ Claude finished

● claude> status

Project Status:
  ID:    a1b2c3d4-e5f6-7890-abcd-ef1234567890
  Path:  /Users/username/my-project
  State: IDLE
  Connected: Yes

● claude> json on
JSON display enabled

● claude> What is 2+2?

📋 Raw Message:
{
  "type": "agent.message",
  "project_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "data": {
    "type": "message_start",
    ...
  }
}

[Additional JSON messages would appear here]

● claude> exit

Shutting down...
Closing connection...
✓ Connection closed
```

## Tips

1. The client maintains a persistent connection, so you can send multiple prompts without reconnecting
2. Use `json on` to debug message flow and see the exact server responses
3. The state indicator in the prompt helps you know when Claude is ready for the next prompt
4. Press Ctrl+C at any time to gracefully shut down