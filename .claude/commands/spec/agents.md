---
description: Analyze features to identify and create specialized agents for task implementation
argument-hint: <feature-name> [--create] [--optimize]
---

# Agent Optimization Command

Automatically analyze feature specifications to identify, create, and optimize specialized agents for task implementation.

## Usage
```
/spec:agents <feature-name> [--create] [--optimize]
```

## Options
- `--create`: Create missing agents (requires user approval)
- `--optimize`: Update tasks with agent recommendations

## Prerequisites
- Feature must have completed requirements, design, and tasks phases
- `.claude/agents/` directory will be created if needed

## Instructions

You are analyzing a feature specification to identify what specialized agents would be most effective for implementing the tasks.

### Process

1. **Load Feature Specification**
   - Determine if feature is single-module or cross-module
   - Load all relevant documents:
     - requirements.md
     - design.md
     - tasks.md
     - architecture.md (global and module-specific)

2. **Pattern Recognition**
   
   Scan all documents for technology indicators:
   
   **Programming Languages**:
   - Look for: "Go", "Python", "TypeScript", "JavaScript", "Kotlin", "Java", "Swift", "Rust", "C++", "C#"
   - File extensions: `.go`, `.py`, `.ts`, `.js`, `.kt`, `.java`, `.swift`, `.rs`
   - Keywords: "implement in", "using", "with {language}"
   
   **Frameworks & Libraries**:
   - Frontend: React, Angular, Vue, Svelte, Next.js, Gatsby
   - Backend: Express, Fastify, Django, Flask, FastAPI, Gin, Echo
   - Mobile: React Native, Flutter, Jetpack Compose, SwiftUI
   - Testing: Jest, Mocha, Pytest, Go testing, JUnit
   
   **Architecture Patterns**:
   - API: REST, GraphQL, gRPC, WebSocket
   - Patterns: MVC, MVVM, Clean Architecture, Hexagonal
   - Systems: Microservices, Event-driven, Serverless
   - Data: SQL, NoSQL, Redis, Message Queues
   
   **Infrastructure & DevOps**:
   - Containers: Docker, Kubernetes
   - CI/CD: GitHub Actions, Jenkins, GitLab CI
   - Cloud: AWS, GCP, Azure
   - Monitoring: Prometheus, Grafana, ELK

3. **Identify Agent Profiles**
   
   Based on detected patterns, generate agent profiles:
   
   **Developer Agents**:
   - `{language}-developer`: Core language development
   - `{language}-{framework}-developer`: Framework-specific development
   - Example: `go-developer`, `typescript-react-developer`
   
   **Specialist Agents**:
   - `{domain}-architect`: Architecture and design
   - `{tech}-specialist`: Specific technology expertise
   - Example: `api-architect`, `websocket-specialist`
   
   **Testing Agents**:
   - `{language}-test-engineer`: Language-specific testing
   - `{type}-test-specialist`: Test type specialization
   - Example: `go-test-engineer`, `e2e-test-specialist`
   
   **Infrastructure Agents**:
   - `devops-engineer`: General DevOps tasks
   - `{platform}-engineer`: Platform-specific tasks
   - Example: `kubernetes-engineer`, `aws-engineer`

4. **Check Existing Agents**
   
   - List all files in `.claude/agents/`
   - Compare identified profiles with existing agents
   - Identify gaps and overlaps
   - Note which tasks can use existing agents

5. **Generate Agent Proposals**
   
   For each missing agent profile:
   
   ```markdown
   ## Proposed Agent: {agent-name}
   
   **Justification**: Found {count} tasks requiring {technologies}
   
   **Specialization**: {primary-domain}
   
   **Technologies**: {tech-list}
   
   **Recommended Tools**: {tools-based-on-domain}
   ```
   
   **Tool Selection Logic**:
   - Language development → Read, Write, Edit, MultiEdit, Grep, Glob
   - Testing → Above + Bash (for running tests)
   - Architecture → Read, Grep, Glob (read-only for analysis)
   - DevOps → All tools including Bash
   - Documentation → Read, Write, Edit

6. **Create Agents** (if --create flag)
   
   Present all proposals and ask:
   "Would you like to create these {count} specialized agents? (y/n)"
   
   If approved, for each agent create:
   
   ```markdown
   ---
   name: {agent-name}
   description: {specialization-description}
   tools: {selected-tools}
   ---
   
   You are a specialized {role} with deep expertise in {primary-technology}.
   
   ## Your Expertise
   
   **Primary Focus**: {main-responsibility}
   
   **Technologies**:
   {bulleted-list-of-technologies}
   
   **Best Practices**:
   - {practice-1-for-domain}
   - {practice-2-for-domain}
   - {practice-3-for-domain}
   
   ## Task Approach
   
   When implementing tasks:
   1. {domain-specific-guidance-1}
   2. {domain-specific-guidance-2}
   3. {domain-specific-guidance-3}
   
   ## Quality Standards
   
   - {quality-metric-1}
   - {quality-metric-2}
   - {quality-metric-3}
   
   {additional-context-based-on-specialization}
   ```

7. **Optimize Tasks** (if --optimize flag)
   
   Update tasks.md to include agent recommendations:
   
   - Add agent hints before tasks: `> Use {agent-name} agent`
   - Group related tasks by recommended agent
   - Add section headers for agent groupings
   
   Example transformation:
   ```markdown
   ### Backend Development Tasks
   > Recommended agent: go-developer
   
   - [ ] 1. Set up Go module structure
   - [ ] 2. Implement WebSocket handler
   
   ### Frontend Development Tasks  
   > Recommended agent: typescript-react-developer
   
   - [ ] 3. Create React components
   - [ ] 4. Implement state management
   ```

8. **Generate Summary Report**
   
   ```markdown
   ## Agent Analysis Summary
   
   **Feature**: {feature-name}
   **Technologies Detected**: {count} unique technologies
   **Agent Profiles Identified**: {count} specializations
   
   ### Existing Agents Applicable
   {list-of-existing-agents-and-task-counts}
   
   ### New Agents Created
   {list-of-created-agents}
   
   ### Task Distribution
   {agent-name}: {task-count} tasks
   {agent-name}: {task-count} tasks
   
   ### Next Steps
   - Run `/spec:6_execute <task-id>` to start implementation
   - Agents will be automatically selected based on task type
   ```

### Pattern Recognition Rules

**Language Detection Priority**:
1. Explicit mentions in design.md
2. File extensions in tasks
3. Framework implications (e.g., React → JavaScript/TypeScript)
4. Keywords in task descriptions

**Agent Naming Conventions**:
- Use lowercase with hyphens
- Language first, then framework/specialization
- Keep names under 30 characters
- Examples: `go-developer`, `typescript-react-developer`, `api-architect`

**Tool Assignment Guidelines**:
- Developers: Full code manipulation tools
- Architects: Read-only tools for analysis
- Test Engineers: Code tools + Bash for execution
- DevOps: All tools including system commands

### Error Handling

- No tasks.md found: "Feature must complete tasks phase first"
- No patterns detected: "Unable to identify technologies. Please ensure tasks include technology details"
- Agent creation failed: "Error creating agent: {reason}"

## Examples

### Single-Module Feature
```
/spec:agents user-authentication --create --optimize
```
Might identify: `go-developer`, `go-test-engineer`, `api-architect`

### Cross-Module Feature
```
/spec:agents real-time-chat --create --optimize
```
Might identify: `go-developer`, `typescript-react-developer`, `kotlin-android-developer`, `websocket-specialist`

## Next Steps

After running this command:
1. Review created agents in `.claude/agents/`
2. Check optimized tasks.md for agent recommendations
3. Use `/spec:6_execute` to implement tasks with specialized agents