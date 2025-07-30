import chalk from 'chalk';
import { mkdirSync } from 'fs';
import { PocketAgentClient } from '../client';
import { AgentMessage, ProjectStateMessage } from '../types/messages';

async function createHaikuScenario() {
  console.log(chalk.blue.bold('\n=== Pocket Agent Test: Create Haiku ===\n'));

  const client = new PocketAgentClient({
    url: 'ws://localhost:8443/ws',
    debug: true
  });

  let projectId: string | null = null;
  const agentMessages: string[] = [];
  let executionComplete = false;

  try {
    // Step 1: Connect to server
    console.log(chalk.yellow('1. Connecting to WebSocket server...'));
    await client.connect();
    console.log(chalk.green('✓ Connected successfully\n'));

    // Step 2: Create project directory
    const projectPath = `/tmp/pocket-agent-test-${Date.now()}`;
    console.log(chalk.yellow(`2. Creating project directory at: ${projectPath}`));
    try {
      mkdirSync(projectPath, { recursive: true });
      console.log(chalk.green('✓ Directory created'));
    } catch (error) {
      console.error(chalk.red(`✗ Failed to create directory: ${error}`));
      throw error;
    }

    // Step 3: Create project
    console.log(chalk.yellow(`3. Creating project...`));
    projectId = await client.createProject(projectPath);
    console.log(chalk.green(`✓ Project created with ID: ${projectId}\n`));

    // Step 4: Join the project
    console.log(chalk.yellow(`4. Joining project...`));
    await client.joinProject(projectId);
    console.log(chalk.green('✓ Joined project successfully\n'));

    // Step 5: Set up message handlers
    console.log(chalk.yellow('5. Setting up message handlers...'));
    
    // Handle agent messages (Claude's responses)
    client.onAgentMessage((message: AgentMessage) => {
      if (message.data.type === 'content_block_delta' && message.data.delta?.text) {
        process.stdout.write(message.data.delta.text);
        agentMessages.push(message.data.delta.text);
      } else if (message.data.type === 'message_start') {
        console.log(chalk.cyan('\n📝 Claude is responding...\n'));
      } else if (message.data.type === 'message_stop') {
        console.log(chalk.cyan('\n\n✅ Claude finished responding'));
      }
    });

    // Handle project state changes
    client.onProjectState((message: ProjectStateMessage) => {
      if (message.data.state === 'IDLE' && message.project_id === projectId) {
        executionComplete = true;
      } else if (message.data.state === 'EXECUTING') {
        console.log(chalk.magenta('⚡ Execution started'));
      }
    });

    // Handle errors
    client.onError((error) => {
      console.error(chalk.red(`\n❌ Error: ${error.data.message}`));
    });

    console.log(chalk.green('✓ Handlers configured\n'));

    // Step 6: Send the haiku prompt
    const prompt = "Create a HAIKU.md file with a haiku of your own choosing and tell it back to me";
    console.log(chalk.yellow(`6. Sending prompt: "${prompt}"`));
    
    await client.execute(projectId, prompt, { permission_mode: 'acceptEdits' });

    // Wait for execution to complete
    await client.waitForProjectState(projectId, 'IDLE', 60000);
    console.log(chalk.green('\n✓ Execution completed\n'));

    // Display the collected haiku
    const fullResponse = agentMessages.join('');
    const haikuMatch = fullResponse.match(/```[\s\S]*?```/);
    if (haikuMatch) {
      console.log(chalk.blue.bold('\n📜 The Haiku:'));
      console.log(chalk.white(haikuMatch[0]));
    }

    // Step 7: Close connection
    console.log(chalk.yellow('\n7. Closing connection...'));
    await client.close();
    console.log(chalk.green('✓ Connection closed\n'));

    console.log(chalk.green.bold('✅ Test completed successfully!\n'));
    
    // Ensure clean exit
    process.exit(0);

  } catch (error) {
    console.error(chalk.red.bold('\n❌ Test failed:'), error);
    await client.close();
    process.exit(1);
  }
}

// Run the scenario
createHaikuScenario().catch(console.error);