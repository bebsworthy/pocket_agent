# Pocket Agent Test Client

A TypeScript test client for testing the Pocket Agent WebSocket server.

## Setup

1. Install dependencies:
```bash
npm install
```

2. Make sure the Pocket Agent server is running:
```bash
# In the server directory
bin/pocket-agent-server
```

## Running Tests

### Create Haiku Scenario

This test scenario:
1. Connects to the WebSocket server
2. Creates a new project in a temporary directory
3. Sends a prompt asking Claude to create a haiku
4. Displays Claude's response in real-time
5. Closes the connection

Run the test:
```bash
npm run scenario:create-haiku
```

## Configuration

The test client connects to `ws://localhost:8443/ws` by default. 

If your server is running with TLS enabled, update the URL in `src/scenarios/create-haiku.ts`:
```typescript
const client = new PocketAgentClient({
  url: 'wss://localhost:8443/ws',  // Use wss:// for TLS
  debug: false
});
```

## Output

The test will show:
- Connection status
- Project creation confirmation
- Claude's response as it streams in
- The final haiku content
- Test completion status

Example output:
```
=== Pocket Agent Test: Create Haiku ===

1. Connecting to WebSocket server...
✓ Connected successfully

2. Creating project at: /tmp/pocket-agent-test-1704123456789
✓ Project created with ID: 550e8400-e29b-41d4-a716-446655440000

3. Setting up message handlers...
✓ Handlers configured

4. Sending prompt: "Create a HAIKU.md file with a haiku of your own choosing and tell it back to me"
⚡ Execution started

📝 Claude is responding...

I'll create a haiku for you...

✅ Claude finished responding
✓ Execution completed

📜 The Haiku:
```
Morning dewdrops shine
Reflecting the rising sun
Nature awakens
```

5. Closing connection...
✓ Connection closed

✅ Test completed successfully!
```

## Debugging

To enable debug output showing all WebSocket messages:

```typescript
const client = new PocketAgentClient({
  url: 'ws://localhost:8443/ws',
  debug: true  // Enable debug logging
});
```

## Adding More Scenarios

To add a new test scenario:

1. Create a new file in `src/scenarios/`
2. Import the client and types
3. Implement your test logic
4. Add a script to `package.json`:
```json
"scenario:your-test": "ts-node src/scenarios/your-test.ts"
```