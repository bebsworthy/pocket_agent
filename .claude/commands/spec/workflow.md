# Spec Workflow Summary

Display a quick summary of the spec-driven workflow.

## Usage
```
/spec:workflow
```

## Instructions
You are providing a concise overview of the spec workflow process.

### Display the Following Information:

```
🚀 Spec-Driven Development Workflow (v2.0)

This project uses a structured workflow for feature development with multi-module support.

📋 WORKFLOW PHASES:
0. Architecture (/spec:architecture) - One-time codebase analysis
1. Create (/spec:1_create) - Initialize feature specification  
2. Research (/spec:2_research) - Analyze feature context
3. Requirements (/spec:3_requirements) - Define user stories & criteria
4. Design (/spec:4_design) - Create technical architecture
5. Tasks (/spec:5_tasks) - Break down implementation
6. Execute (/spec:6_execute) - Implement one task at a time

🏗️ FEATURE TYPES:
• Single-Module: Feature within one module (server, frontend-react, or frontend-android)
  Example: /spec:1_create dark-mode --module frontend-react
  
• Cross-Module: Feature spanning multiple modules  
  Example: /spec:1_create real-time-sync --cross-module

📁 FILE STRUCTURE:
Single-Module → documentation/modules/{module}/features/{feature}/
Cross-Module → documentation/features/{feature}/modules/{module}/

⚡ KEY COMMANDS:
• /spec:list - View all features
• /spec:status [feature] - Check feature progress
• /spec:modules - List available modules
• /spec:parallel_execute - Execute tasks in parallel

🔑 CRITICAL RULES:
✓ Follow phases in order (no skipping)
✓ Get explicit approval before proceeding
✓ Execute ONE task at a time
✓ Mark tasks complete [x] immediately
✓ Stop after each task for review

For full documentation, read: .spec/WORKFLOW.md
```

### Additional Notes:
- Emphasize the importance of following the workflow sequence
- Highlight that this is version 2.0 with multi-module support
- Remind users about the approval gates between phases
- Mention that architecture analysis is typically done once 