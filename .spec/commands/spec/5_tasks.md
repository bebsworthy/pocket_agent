---
description: Generate parallel-optimized implementation tasks with agent recommendations
argument-hint: [feature-name] [--optimize-existing]
---

# Spec Tasks Command

Generate parallel-optimized implementation task list with agent recommendations based on approved design.

## Usage
```
/spec:5_tasks [feature-name] [options]
```

## Options
- `--optimize-existing` - Review and optimize an existing task list without modifying completed tasks

## Instructions
You are working on the tasks phase of the spec workflow, generating tasks optimized for parallel execution with specialized agents.

### Step 0: Check for --optimize-existing Flag

If `--optimize-existing` flag is provided:
1. **Load Existing Task List**
   - Find and load the existing tasks.md for the feature
   - Preserve all completed tasks (marked with [x])
   - Identify pending tasks (marked with [ ])
   
2. **Optimization Process**
   - Analyze the existing task structure
   - Reorganize ONLY pending tasks into parallel tracks
   - Add agent recommendations for pending tasks
   - Insert code review tasks after pending development phases
   - Maintain all task IDs and descriptions for completed tasks
   - DO NOT renumber or modify completed tasks

3. **Output Format**
   - Show a summary of changes made
   - Preserve the original task numbers for traceability

### Step 1: Determine Feature Type and Location

1. **Identify Current Spec**
   - If no feature-name provided, search in both:
     - `./documentation/features/` (cross-module features)
     - `./documentation/modules/*/features/` (single-module features)
   - If multiple specs exist, ask user to specify which one

2. **Detect Feature Type**
   - **Cross-Module Feature**: If found in `documentation/features/{feature-name}/`
     - Tasks will be created per module with coordination
   - **Single-Module Feature**: If found in `documentation/modules/{module}/features/{feature-name}/`
     - Tasks will be created in that location

### Step 2: Agent Detection and Analysis

1. **Scan Available Agents**
   - List all files in `.claude/agents/` directory
   - Parse agent metadata (name, description, tools)
   - Build agent capability map

2. **Common Agent Types**
   - **Language-specific developers**: `{language}-developer` (e.g., go-developer, python-developer)
   - **Framework specialists**: `{language}-{framework}-developer` (e.g., typescript-react-developer)
   - **Testing specialists**: `{language}-test-engineer` (e.g., go-test-engineer)
   - **Architecture reviewers**: `system-architect`, `api-architect`
   - **Code reviewers**: `code-quality-reviewer` (mandatory for all features)
   - **Domain specialists**: `websocket-specialist`, `database-specialist`, etc.

3. **Agent Matching Logic**
   - Match based on technology stack in design.md
   - Consider language, frameworks, and architectural patterns
   - Prefer specialized agents over general ones
   - Always include code-quality-reviewer for review tasks

### Step 3: Prerequisites and Context Loading

1. **For Cross-Module Features**
   - Load `documentation/features/{feature-name}/integration.md`
   - Load design.md from each `modules/{module}/` subdirectory
   - Load requirements.md from each module
   - Understand cross-module dependencies

2. **For Single-Module Features**
   - Ensure design.md exists and is approved
   - Load both requirements.md and design.md
   - Load module architecture if available
   - Understand the complete feature scope

### Step 4: Generate Parallel-Optimized Task Lists

1. **Task Organization Principles**
   - Group tasks into parallel execution tracks (A-F)
   - Track A: Foundation tasks with no dependencies
   - Track B-E: Core implementation tasks that can run in parallel
   - Track F: Integration and finalization tasks
   - Code Review (CR) track: Reviews after each development phase

2. **Dependency Management**
   - Clearly mark task dependencies
   - Ensure no circular dependencies
   - Group related tasks that share dependencies
   - Minimize dependency chains for maximum parallelism

3. **Agent Assignment**
   - Assign the most appropriate agent to each task
   - Consider agent specialization and tools
   - Group tasks by agent when possible for efficiency
   - Always assign code-quality-reviewer to CR tasks

### Step 5: Task Format

```markdown
# Implementation Tasks

## Parallel Execution Tracks

### Track A: Foundation (No Dependencies)
> Primary Agent: {agent-name}

- [ ] 1. **Task title**
  - Specific implementation details
  - Files to create/modify: `path/to/file.ext`
  - _Requirements: 1.1, 2.3_
  - _Agent: {agent-name}_

- [ ] 2. **Another foundation task**
  - Details...
  - _Requirements: 1.2_
  - _Agent: {agent-name}_

### Track B: Core Features (Dependencies: Track A)
> Primary Agent: {agent-name}

- [ ] 3. **Feature implementation**
  - Implementation specifics
  - _Requirements: 2.1_
  - _Dependencies: Task 1_
  - _Agent: {specialized-agent}_

### Track C: Additional Features (Dependencies: Track A)
> Primary Agent: {agent-name}

- [ ] 5. **Parallel feature**
  - Can run simultaneously with Track B
  - _Requirements: 3.1_
  - _Dependencies: Task 2_
  - _Agent: {agent-name}_

### Code Review Track (Progressive Reviews)

- [ ] CR1. **Review Track A implementation**
  - Review all foundation code
  - Security and best practices check
  - _Dependencies: Track A completion_
  - _Agent: code-quality-reviewer_

- [ ] CR2. **Review Track B & C implementation**
  - Review core features
  - Integration validation
  - _Dependencies: Track B, C completion_
  - _Agent: code-quality-reviewer_

### Track D: Testing (Dependencies: CR1, CR2)
> Primary Agent: {test-engineer-agent}

- [ ] 10. **Unit tests for core features**
  - Test coverage for all new code
  - _Requirements: Testing_
  - _Dependencies: CR2_
  - _Agent: {language}-test-engineer_

### Track E: Integration (Dependencies: Track D)
> Primary Agent: {agent-name}

- [ ] 15. **Integration tasks**
  - System integration
  - _Requirements: 4.1_
  - _Dependencies: Task 10_
  - _Agent: {agent-name}_

### Final Review Track

- [ ] CR3. **Final comprehensive review**
  - Full feature review
  - Performance and security audit
  - _Dependencies: All implementation tracks_
  - _Agent: code-quality-reviewer_

## Cross-Module Coordination (if applicable)

### Module Dependencies
- Module A Track B depends on Module B Track A
- Shared interfaces must be implemented first

## Execution Strategy

### Parallel Groups
1. **Group 1 (Immediate Start)**:
   - Track A (Tasks 1-2)
   
2. **Group 2 (After Group 1 + CR1)**:
   - Track B (Tasks 3-4)
   - Track C (Tasks 5-6)
   
3. **Group 3 (After Group 2 + CR2)**:
   - Track D (Tasks 10-12)
   
4. **Group 4 (After Group 3)**:
   - Track E (Tasks 15-17)
   - Final Review (CR3)

### Agent Utilization
- **Primary Agents**: {list of main agents}
- **Specialist Agents**: {list of specialized agents}
- **Review Agent**: code-quality-reviewer (mandatory)

### Time Estimates
- Parallel execution time: ~X days with 6 agents
- Sequential execution time: ~Y days
- Review overhead: ~Z hours per phase
```

### Step 6: Task Guidelines

- Break design into atomic, executable coding tasks
- Each task should be completable in 1-4 hours
- Group related micro-tasks under a single task number
- Focus ONLY on coding tasks (no deployment, user testing, etc.)
- Include specific file names and components
- Reference requirements using _Requirements: X.Y_ format
- Include agent recommendations for each task

**Agent Assignment Criteria**:
- Match task technology to agent expertise
- Prefer specialized agents for complex tasks
- Use general agents for simple tasks
- Always use code-quality-reviewer for reviews

**Code Review Integration**:
- Insert CR tasks after each major development phase
- Group reviews by logical feature boundaries
- Reviews should not block unrelated parallel work
- Final review covers entire feature

### Step 7: Excluded Tasks

- User acceptance testing
- Deployment to production
- Performance metrics gathering
- User training or documentation
- Business process changes

### Step 8: Approval Process

1. **For Standard Generation**
   - Show parallel track organization
   - Highlight agent assignments and rationale
   - Display execution timeline with parallelism
   - Show code review checkpoints

2. **For Optimization Mode (--optimize-existing)**
   - Show optimization summary first
   - Clearly separate completed vs pending tasks
   - Explain the reorganization rationale
   - Highlight new code review insertions
   - Show before/after parallelism improvement

3. **Request Approval**
   - For new tasks: "Do the tasks and agent assignments look good?"
   - For optimization: "Does this optimization preserve your completed work while improving parallelism?"
   - Explain parallel execution benefits
   - Make revisions based on feedback
   - Continue until explicit approval

**IMPORTANT**: Tasks must be structured for `/spec:parallel_execute` compatibility

## Optimization Mode Example

When using `--optimize-existing`, the output should look like:

```markdown
# OPTIMIZED TASK LIST: WebSocket API Implementation

## Optimization Summary
- Completed tasks preserved: 5 (Tasks 1-5)
- Pending tasks reorganized: 32 (Tasks 6-37)
- New code review tasks added: 3 (CR2, CR3, CR4)
- Parallel tracks created: 5 (B, C, D, E, F)
- Agents assigned: 4 unique agents

## Completed Tasks (Preserved)

### Track A: Foundation ✓
- [x] 1. **Set up Go module structure** ✓
  - Created server/ directory with go.mod
  - _Requirements: 1.1_
  - _Completed: 2024-01-15_

- [x] 2. **Implement configuration management** ✓
  - Created Config struct
  - _Requirements: 8.1, 10.1_
  - _Completed: 2024-01-15_

### Code Review Track
- [x] CR1. **Review Track A implementation** ✓
  - Reviewed foundation code
  - _Agent: code-quality-reviewer_
  - _Completed: 2024-01-16_

## Pending Tasks (Optimized for Parallel Execution)

### Track B: Data Models (Dependencies: Track A)
> Primary Agent: go-developer

- [ ] 4. **Implement core data models**
  - Create models/project.go with Project struct
  - _Requirements: 2.1, 3.1, 6.1_
  - _Agent: go-developer_

### Track C: WebSocket Infrastructure (Dependencies: Track A)
> Primary Agent: go-websocket-specialist

- [ ] 15. **Set up WebSocket server**
  - Integrate Gorilla WebSocket
  - _Requirements: 1.1, 1.2_
  - _Agent: go-websocket-specialist_

### Code Review Track (New)

- [ ] CR2. **Review Track B & C implementation**
  - Review data models and WebSocket setup
  - _Dependencies: Track B, C completion_
  - _Agent: code-quality-reviewer_

[Additional tracks...]
```

## Standard Generation Example

For normal task generation (without --optimize-existing):

```markdown
# WebSocket API Implementation Tasks

## Available Agents Detected
- go-developer: Go server development
- go-websocket-specialist: WebSocket protocol expert
- go-test-engineer: Go testing specialist
- code-quality-reviewer: Code review specialist

## Parallel Execution Tracks

### Track A: Foundation (No Dependencies)
> Primary Agent: go-developer

- [ ] 1. **Set up Go module structure**
  - Create server/ directory with go.mod
  - Set up internal/ package structure
  - _Requirements: 1.1_
  - _Agent: go-developer_

### Track B: WebSocket Implementation (Dependencies: Track A)
> Primary Agent: go-websocket-specialist

- [ ] 5. **Implement WebSocket server**
  - Create WebSocket handler with Gorilla
  - _Requirements: 2.1_
  - _Dependencies: Task 1_
  - _Agent: go-websocket-specialist_

### Code Review Track

- [ ] CR1. **Review foundation code**
  - Review project structure and setup
  - _Dependencies: Track A_
  - _Agent: code-quality-reviewer_
```

## Next Phase
After approval:
- Use `/spec:parallel_execute {feature-name}` to execute all tasks
- Use `/spec:parallel_execute {feature-name} --track=A` for specific tracks
- Check progress with `/spec:status {feature-name}`

For optimization workflow:
- Use `/spec:5_tasks {feature-name} --optimize-existing` to optimize partially completed task lists
- The optimization preserves completed work while improving parallel execution for remaining tasks