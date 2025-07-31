import chalk from 'chalk';
import { mkdirSync } from 'fs';
import { createInterface } from 'readline';
import { PocketAgentClient } from '../client';
import { ServerMessage, AgentMessage, ProjectStateMessage } from '../types/messages';

async function interactiveScenario() {
  console.log(chalk.blue.bold('\n=== Pocket Agent Interactive CLI ===\n'));
  console.log(chalk.gray('Type "help" for available commands\n'));

  const client = new PocketAgentClient({
    url: 'ws://localhost:8443/ws',
    debug: false // Set to false for cleaner output
  });

  let projectId: string | null = null;
  let projectPath: string | null = null;
  let currentState: string = 'IDLE'; // Default to IDLE instead of UNKNOWN
  let isConnected = false;
  let showJson = false; // Track JSON display preference
  let permissionMode: string = 'default'; // Current permission mode
  const permissionModes = ['default', 'acceptEdits', 'bypassPermissions', 'plan']; // Available modes
  let connectionCheckInterval: NodeJS.Timeout | null = null;

  // Create readline interface for user input
  const rl = createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: chalk.cyan('claude> ')
  });

  // Enable keypress events (don't use raw mode with readline)
  if (process.stdin.isTTY) {
    const readline = require('readline');
    readline.emitKeypressEvents(process.stdin, rl);
  }

  try {
    // Connect to server
    console.log(chalk.yellow('Connecting to WebSocket server...'));
    await client.connect();
    isConnected = true;
    console.log(chalk.green('✓ Connected successfully\n'));

    // Set up project
    await setupProject();

    // Set up message handlers
    setupMessageHandlers();
    
    // Monitor connection
    client.on('disconnected', () => {
      console.log(chalk.red('\n\n❌ Disconnected from server'));
      console.log(chalk.yellow('Reason: Connection closed by server or network issue'));
      isConnected = false;
      // Don't automatically cleanup - let user see the error
      updatePrompt();
    });
    
    // Also check connection periodically
    connectionCheckInterval = setInterval(() => {
      if (!isConnected) {
        if (connectionCheckInterval) clearInterval(connectionCheckInterval);
        return;
      }
      // Check if still connected
      if (!client.isConnected()) {
        console.log(chalk.red('\n\n❌ Connection lost'));
        isConnected = false;
        if (connectionCheckInterval) clearInterval(connectionCheckInterval);
        cleanup();
      }
    }, 5000); // Check every 5 seconds

    // Show initial prompt
    updatePrompt();

    // Handle user input
    rl.on('line', async (input) => {
      const command = input.trim();
      
      if (!command) {
        updatePrompt();
        return;
      }

      // Check connection before handling command
      if (!isConnected && command !== 'exit' && command !== 'quit') {
        console.log(chalk.red('\n❌ Not connected to server. Please restart the client.'));
        updatePrompt();
        return;
      }

      try {
        await handleCommand(command);
      } catch (error) {
        console.error(chalk.red(`\nError: ${error}`));
      }
      
      updatePrompt();
    });

    // Handle special key combinations
    process.stdin.on('keypress', (str, key) => {
      // Tab key to cycle permission modes
      if (key && key.name === 'tab' && !key.shift && !key.ctrl) {
        cyclePermissionMode();
        // Clear current line and show new prompt
        process.stdout.write('\r\x1b[K');
        updatePrompt();
      }
    });

    // Handle Ctrl+C
    rl.on('SIGINT', () => {
      console.log(chalk.yellow('\n\nShutting down...'));
      cleanup();
    });

  } catch (error) {
    console.error(chalk.red.bold('\n❌ Failed to start:'), error);
    cleanup();
  }

  // Helper functions
  async function setupProject() {
    // Ask for project path
    const projectPathInput = await question(
      chalk.yellow('Enter project path (or press Enter for temp directory): ')
    );
    
    projectPath = projectPathInput || `/tmp/pocket-agent-interactive-${Date.now()}`;
    
    // Ask to create new or use existing
    const createNew = await question(
      chalk.yellow('Create new project? (y/n): ')
    );
    
    if (createNew.toLowerCase() === 'y' || createNew.toLowerCase() === 'yes') {
      // Create directory
      console.log(chalk.gray(`Creating directory: ${projectPath}`));
      try {
        mkdirSync(projectPath, { recursive: true });
      } catch (error) {
        console.error(chalk.red(`Failed to create directory: ${error}`));
        throw error;
      }
      
      // Create project
      console.log(chalk.yellow('Creating project...'));
      projectId = await client.createProject(projectPath);
      console.log(chalk.green(`✓ Project created with ID: ${projectId}`));
    } else {
      // Use existing project
      const existingId = await question(
        chalk.yellow('Enter project ID: ')
      );
      projectId = existingId.trim();
    }
    
    // Join the project
    console.log(chalk.yellow('Joining project...'));
    await client.joinProject(projectId);
    console.log(chalk.green('✓ Joined project successfully\n'));
    
    // Wait a moment for the initial state message
    await new Promise(resolve => setTimeout(resolve, 500));
  }

  function setupMessageHandlers() {
    // Handle all server messages and display as JSON
    client.on('message', (message: ServerMessage) => {
      // Clear the current line and move cursor up if needed
      process.stdout.write('\r\x1b[K');
      
      // Always show raw JSON if enabled
      if (showJson) {
        console.log(chalk.gray('\n📋 Raw Message:'));
        console.log(chalk.gray(JSON.stringify(message, null, 2)));
      }
      
      // Format and display the message
      if (message.type === 'agent_message') {
        handleAgentMessage(message as AgentMessage);
      } else if (message.type === 'project_state') {
        handleProjectState(message as ProjectStateMessage);
      } else if (message.type === 'error') {
        console.log(chalk.red('\n❌ Error:'), message.data);
      } else if (!showJson) {
        // Display other messages as formatted JSON only if not already shown
        console.log(chalk.gray('\n📨 Server Message:'));
        console.log(chalk.gray(JSON.stringify(message, null, 2)));
      }
      
      // Restore prompt
      updatePrompt();
    });
  }

  function handleAgentMessage(message: AgentMessage) {
    const data = message.data;
    
    // Handle different Claude message types
    switch (data.type) {
      case 'message_start':
        if (!showJson) {
          console.log(chalk.cyan('\n🤖 Claude is thinking...\n'));
        }
        break;
      
      case 'content_block_delta':
        if (data.delta?.text && !showJson) {
          process.stdout.write(data.delta.text);
        }
        break;
      
      case 'message_stop':
        if (!showJson) {
          console.log(chalk.cyan('\n\n✅ Claude finished\n'));
        }
        break;
      
      case 'result':
        // This is the final result message
        if (!showJson && data.result) {
          console.log(chalk.cyan('\n🤖 Claude says:\n'));
          console.log(data.result);
          console.log();
        }
        break;
      
      case 'text':
        // Handle text messages
        if (!showJson && data.text) {
          console.log(chalk.cyan('\n🤖 Claude:\n'));
          console.log(data.text);
          console.log();
        }
        break;
      
      default:
        // Show other message types as JSON only if not already shown
        if (!showJson) {
          console.log(chalk.gray('\n📋 Agent Message:'));
          console.log(chalk.gray(JSON.stringify(message, null, 2)));
        }
    }
  }

  function handleProjectState(message: ProjectStateMessage) {
    if (message.project_id === projectId) {
      currentState = message.data.state;
      // Debug: log state changes
      if (showJson) {
        console.log(chalk.gray(`\n[State changed to: ${currentState}]`));
      }
    }
  }

  async function handleCommand(command: string) {
    switch (command.toLowerCase()) {
      case 'help':
        showHelp();
        break;
      
      case 'status':
        showStatus();
        break;
      
      case 'clear':
        console.clear();
        break;
      
      case 'json on':
        console.log(chalk.green('JSON display enabled'));
        showJson = true;
        client.setDebug(true);
        break;
      
      case 'json off':
        console.log(chalk.green('JSON display disabled'));
        showJson = false;
        client.setDebug(false);
        break;
      
      case 'permission':
      case 'mode':
        showPermissionMode();
        break;
      
      case 'permission default':
      case 'mode default':
        permissionMode = 'default';
        console.log(chalk.green('Permission mode set to: default'));
        break;
      
      case 'permission acceptedits':
      case 'mode acceptedits':
        permissionMode = 'acceptEdits';
        console.log(chalk.green('Permission mode set to: acceptEdits'));
        break;
      
      case 'permission bypass':
      case 'permission bypasspermissions':
      case 'mode bypass':
      case 'mode bypasspermissions':
        permissionMode = 'bypassPermissions';
        console.log(chalk.green('Permission mode set to: bypassPermissions'));
        break;
      
      case 'permission plan':
      case 'mode plan':
        permissionMode = 'plan';
        console.log(chalk.green('Permission mode set to: plan'));
        break;
      
      case 'exit':
      case 'quit':
        cleanup();
        break;
      
      default:
        // Send as prompt if state is IDLE
        if (currentState.toLowerCase() !== 'idle') {
          console.log(chalk.yellow(`\n⚠️  Cannot send prompt - project is ${currentState}`));
          return;
        }
        
        console.log(chalk.gray(`\nSending prompt: "${command}"`));
        const options = permissionMode !== 'default' ? { permission_mode: permissionMode } : undefined;
        
        // Debug: Show what we're sending
        if (showJson) {
          console.log(chalk.gray('Sending message:'), {
            type: 'execute',
            project_id: projectId,
            data: { prompt: command, options }
          });
        }
        
        try {
          await client.execute(projectId!, command, options);
          // Log success if in debug mode
          if (showJson) {
            console.log(chalk.green('Execute message sent successfully'));
          }
        } catch (error) {
          console.error(chalk.red('Failed to send execute message:'), error);
          // Check if connection is still alive
          if (!client.isConnected()) {
            console.log(chalk.red('Connection lost - please restart the client'));
            isConnected = false;
          }
        }
    }
  }

  function showHelp() {
    console.log(chalk.cyan('\nAvailable Commands:'));
    console.log(chalk.white('  help        - Show this help message'));
    console.log(chalk.white('  status      - Show current project status'));
    console.log(chalk.white('  clear       - Clear the screen'));
    console.log(chalk.white('  json on     - Enable JSON message display'));
    console.log(chalk.white('  json off    - Disable JSON message display'));
    console.log(chalk.white('  permission  - Show current permission mode'));
    console.log(chalk.white('  mode <mode> - Set permission mode (default, acceptEdits, bypass, plan)'));
    console.log(chalk.white('  exit        - Exit the program'));
    console.log(chalk.white('  quit        - Exit the program'));
    console.log(chalk.gray('\nWhen project is IDLE, any other text will be sent as a prompt to Claude'));
    console.log(chalk.gray(`Current permission mode: ${permissionMode}\n`));
  }

  function showStatus() {
    console.log(chalk.cyan('\nProject Status:'));
    console.log(chalk.white(`  ID:         ${projectId || 'Not set'}`));
    console.log(chalk.white(`  Path:       ${projectPath || 'Not set'}`));
    console.log(chalk.white(`  State:      ${currentState}`));
    console.log(chalk.white(`  Connected:  ${isConnected ? 'Yes' : 'No'}`));
    console.log(chalk.white(`  Permission: ${permissionMode}\n`));
  }

  function showPermissionMode() {
    console.log(chalk.cyan('\nPermission Modes:'));
    permissionModes.forEach(mode => {
      const isCurrent = mode === permissionMode;
      const prefix = isCurrent ? chalk.green('→') : ' ';
      console.log(`${prefix} ${chalk.white(mode)}${isCurrent ? chalk.gray(' (current)') : ''}`);
    });
    console.log(chalk.gray('\nUsage: mode <mode> or permission <mode>'));
    console.log(chalk.gray('Example: mode acceptEdits'));
    console.log(chalk.gray('Tip: Press Tab to quickly cycle through modes\n'));
  }

  function cyclePermissionMode() {
    const currentIndex = permissionModes.indexOf(permissionMode);
    const nextIndex = (currentIndex + 1) % permissionModes.length;
    permissionMode = permissionModes[nextIndex];
    console.log(chalk.gray(`\nPermission mode: ${chalk.green(permissionMode)}`));
  }

  function updatePrompt() {
    const stateIndicator = currentState === 'IDLE' || currentState === 'idle' ? chalk.green('●') : 
                          currentState === 'EXECUTING' || currentState === 'executing' ? chalk.yellow('●') : 
                          currentState === 'ERROR' || currentState === 'error' ? chalk.red('●') :
                          chalk.gray('●');
    rl.setPrompt(`${stateIndicator} ${chalk.cyan('claude>')} `);
    rl.prompt();
  }

  function question(prompt: string): Promise<string> {
    return new Promise((resolve) => {
      rl.question(prompt, resolve);
    });
  }

  function cleanup() {
    console.log(chalk.yellow('\nClosing connection...'));
    
    // Clear the connection check interval
    if (connectionCheckInterval) {
      clearInterval(connectionCheckInterval);
    }
    
    rl.close();
    if (isConnected) {
      client.close().then(() => {
        console.log(chalk.green('✓ Connection closed'));
        process.exit(0);
      }).catch((error) => {
        console.error(chalk.red('Error closing connection:'), error);
        process.exit(1);
      });
    } else {
      process.exit(0);
    }
  }
}

// Run the scenario
interactiveScenario().catch(console.error);