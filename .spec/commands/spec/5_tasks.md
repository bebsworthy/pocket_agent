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

## Review and Rework Process

**MANDATORY**: All development tracks must include Code Review (CR) and Product Review (PR) tasks after implementation:

1. After each track's development tasks, a **Code Review (CR)** is performed by the appropriate code reviewer agent
2. Following the code review, a **Product Review (PR)** is performed by the product-owner-reviewer
3. If either review fails (status: "REQUIRES CHANGES"), a **Rework (RW)** task is triggered
4. The appropriate developer agent addresses all findings from the failed reviews
5. After rework, the review process repeats until approval is achieved (status: "APPROVED")
6. Only after both CR and PR are approved can dependent tracks proceed

**Review Dependencies**: Reviews gate track progression - no dependent tracks can start until all prerequisite reviews are approved.

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
   - **Code reviewers**: `{technology}-code-reviewer` (e.g., typescript-react-code-reviewer, go-code-reviewer) - mandatory for all CR tasks
   - **Product reviewer**: `product-owner-reviewer` (mandatory for all PR tasks)
   - **Domain specialists**: `websocket-specialist`, `database-specialist`, etc.

3. **Agent Matching Logic**
   - Match based on technology stack in design.md
   - Consider language, frameworks, and architectural patterns
   - Prefer specialized agents over general ones
   - Always include technology-specific code reviewer for CR tasks
   - Always include product-owner-reviewer for PR tasks

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
   - Review tasks (CR/PR/RW): Quality gates after each development phase

2. **Dependency Management**
   - Clearly mark task dependencies
   - Ensure no circular dependencies
   - Group related tasks that share dependencies
   - Minimize dependency chains for maximum parallelism

3. **Agent Assignment**
   - Assign the most appropriate agent to each task
   - Consider agent specialization and tools
   - Group tasks by agent when possible for efficiency
   - Always assign technology-specific code reviewer to CR tasks
   - Always assign product-owner-reviewer to PR tasks
   - Use original developer agent for RW tasks

### Step 5: Task Format

```markdown
# Implementation Tasks

## Review and Rework Process
1. After each track's development tasks, a **Code Review (CR)** is performed by the {technology}-code-reviewer
2. Following the code review, a **Product Review (PR)** is performed by the product-owner-reviewer
3. If either review fails (status: "Requires changes"), a **Rework (RW)** task is triggered
4. The {technology}-developer addresses all findings from the failed reviews
5. After rework, the review process repeats until approval is achieved
6. Only after both CR and PR are approved can dependent tracks proceed

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

- [ ] CR-A. **Code Review: Foundation Setup**
  - Review project configuration and build setup
  - Verify all libraries use latest stable versions
  - Check linting and TypeScript configuration
  - Validate type definitions match server protocol
  - Ensure mobile viewport configuration is correct
  - _Dependencies: Tasks 1-2_
  - _Agent: {technology}-code-reviewer_

- [ ] PR-A. **Product Review: Track A Foundation**
  - Validate project setup meets all requirements in sections 1.x
  - Verify TypeScript strict mode configuration
  - Check mobile viewport and meta tags implementation
  - Ensure all type definitions match server protocol specs
  - Validate linting and formatting setup
  - Review output saved to: `{module}/product_review/track-a.md`
  - _Spec References: requirements.md sections 1.x, 5.3; design.md Project Setup_
  - _Dependencies: CR-A_
  - _Agent: product-owner-reviewer_

- [ ] RW-A. **Rework: Address Track A Review Findings**
  - Review findings from `{module}/code_review/CR-A.md` and/or `{module}/product_review/track-a.md`
  - Address all critical issues identified in reviews
  - Implement required changes and improvements
  - Re-run linting and type checking
  - Update documentation if needed
  - _Trigger: Only if CR-A or PR-A status is "Requires changes"_
  - _Dependencies: CR-A and/or PR-A (failed)_
  - _Agent: {agent-name}_

### Track B: Core Features (Dependencies: Track A, CR-A, PR-A)
> Primary Agent: {specialized-agent}

- [ ] 3. **Feature implementation**
  - Implementation specifics
  - _Requirements: 2.1_
  - _Dependencies: PR-A (approved)_
  - _Agent: {specialized-agent}_

- [ ] CR-B. **Code Review: Core Features**
  - Review feature implementation quality
  - Validate security and best practices
  - Check error handling patterns
  - Ensure code maintainability
  - _Dependencies: Task 3_
  - _Agent: {technology}-code-reviewer_

- [ ] PR-B. **Product Review: Track B Core Features**
  - Validate feature meets requirements 2.x
  - Verify functionality matches specifications
  - Check user experience flows
  - Ensure integration points work correctly
  - Review output saved to: `{module}/product_review/track-b.md`
  - _Spec References: requirements.md sections 2.x; design.md Core Features_
  - _Dependencies: CR-B_
  - _Agent: product-owner-reviewer_

- [ ] RW-B. **Rework: Address Track B Review Findings**
  - Review findings from failed reviews
  - Fix implementation issues
  - Improve code quality and security
  - Re-test functionality
  - _Trigger: Only if CR-B or PR-B status is "Requires changes"_
  - _Dependencies: CR-B and/or PR-B (failed)_
  - _Agent: {specialized-agent}_

### Track C: Additional Features (Dependencies: Track A, CR-A, PR-A)
> Primary Agent: {agent-name}

- [ ] 5. **Parallel feature**
  - Can run simultaneously with Track B
  - _Requirements: 3.1_
  - _Dependencies: PR-A (approved)_
  - _Agent: {agent-name}_

- [ ] CR-C. **Code Review: Additional Features**
  - Review parallel implementation
  - Check integration compatibility
  - _Dependencies: Task 5_
  - _Agent: {technology}-code-reviewer_

- [ ] PR-C. **Product Review: Track C Additional Features**
  - Validate additional features meet requirements 3.x
  - Review output saved to: `{module}/product_review/track-c.md`
  - _Spec References: requirements.md sections 3.x_
  - _Dependencies: CR-C_
  - _Agent: product-owner-reviewer_

- [ ] RW-C. **Rework: Address Track C Review Findings**
  - Fix additional feature issues
  - _Trigger: Only if CR-C or PR-C status is "Requires changes"_
  - _Dependencies: CR-C and/or PR-C (failed)_
  - _Agent: {agent-name}_

### Checkpoint Review 1
- [ ] CR1. **Comprehensive Review: Foundation and Core Features**
  - Review overall architecture consistency
  - Validate integration between tracks
  - Check performance implications
  - Ensure security standards
  - _Dependencies: All previous CR/PR approvals (PR-A, PR-B, PR-C)_
  - _Agent: {technology}-code-reviewer_

### Track D: Advanced Features (Dependencies: CR1)
> Primary Agent: {test-engineer-agent}

- [ ] 10. **Advanced implementation**
  - Implementation requiring foundation completion
  - _Requirements: Testing_
  - _Dependencies: CR1_
  - _Agent: {language}-test-engineer_

### Final Review Track

- [ ] CR-FINAL. **Final comprehensive review**
  - Full feature review
  - Performance and security audit
  - _Dependencies: All implementation tracks_
  - _Agent: {technology}-code-reviewer_

## Cross-Module Coordination (if applicable)

### Module Dependencies
- Module A Track B depends on Module B Track A
- Shared interfaces must be implemented first

## Execution Strategy

### Parallel Groups with Review Gates
1. **Group 1 (Immediate Start)**:
   - Track A Development (Tasks 1-2)
   
2. **Group 2 (After Track A Development)**:
   - CR-A (Code Review Track A)
   - PR-A (Product Review Track A)
   - RW-A (Rework Track A - only if reviews fail)
   
3. **Group 3 (After PR-A Approval)**:
   - Track B Development (Tasks 3-4) - parallel execution
   - Track C Development (Tasks 5-6) - parallel execution
   
4. **Group 4 (Individual Track Reviews)**:
   - CR-B, PR-B, RW-B (if needed) - for Track B
   - CR-C, PR-C, RW-C (if needed) - for Track C
   
5. **Group 5 (After All Track Reviews Approved)**:
   - CR1 (Comprehensive Checkpoint Review)
   
6. **Group 6 (After CR1 Approval)**:
   - Track D Development (Tasks 10-12)
   - Additional dependent tracks
   
7. **Group 7 (Final Review)**:
   - CR-FINAL (Complete feature review)

### Agent Utilization
- **Primary Agents**: {list of main agents}
- **Specialist Agents**: {list of specialized agents}
- **Code Review Agent**: {technology}-code-reviewer (mandatory for all CR tasks)
- **Product Review Agent**: product-owner-reviewer (mandatory for all PR tasks)

### Time Estimates
- Parallel execution time: ~X days with N agents
- Sequential execution time: ~Y days
- Review overhead: ~Z hours per track (CR + PR + potential RW)
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
- Always use {technology}-code-reviewer for CR tasks
- Always use product-owner-reviewer for PR tasks
- Use original developer agent for RW tasks

**Code Review Integration**:
- Insert CR tasks after each major development phase
- Group reviews by logical feature boundaries
- Reviews should not block unrelated parallel work
- Final review covers entire feature

**Review Output Requirements**:
- **Code Reviews (CR)**: Save output to `{module}/code_review/CR-{track}.md`
- **Product Reviews (PR)**: Save output to `{module}/product_review/track-{track}.md`
- **Review Status**: Must be either "APPROVED" or "REQUIRES CHANGES"
- **Spec References**: PR tasks must reference specific requirement sections and design elements
- **Rework Triggers**: RW tasks are conditional - only execute if reviews fail

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
   - Show code review and product review checkpoints
   - Explain review gates and dependency blocking

2. **For Optimization Mode (--optimize-existing)**
   - Show optimization summary first
   - Clearly separate completed vs pending tasks
   - Explain the reorganization rationale
   - Highlight new review task insertions (CR/PR/RW)
   - Show before/after parallelism improvement
   - Preserve completed review statuses

3. **Request Approval**
   - For new tasks: "Do the tasks, agent assignments, and review process look good?"
   - For optimization: "Does this optimization preserve your completed work while improving parallelism and adding review gates?"
   - Explain parallel execution benefits and quality gates
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
- New review tasks added: 9 (3 CR, 3 PR, 3 RW conditional)
- Parallel tracks created: 5 (B, C, D, E, F)
- Review gates established: 3 checkpoints
- Agents assigned: 6 unique agents (including reviewers)

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
- [x] CR-A. **Code Review: Foundation Implementation** ✓
  - Reviewed foundation code and build setup
  - Status: APPROVED
  - _Agent: go-code-reviewer_
  - _Completed: 2024-01-16_

- [x] PR-A. **Product Review: Track A Foundation** ✓
  - Validated requirements compliance
  - Status: APPROVED
  - _Agent: product-owner-reviewer_
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

### Review Track (New)

- [ ] CR-B. **Code Review: Data Models**
  - Review data models implementation
  - Check Go best practices and type safety
  - _Dependencies: Task 4_
  - _Agent: go-code-reviewer_

- [ ] PR-B. **Product Review: Track B Data Models**
  - Validate data models meet requirements 2.x, 3.x
  - Review output saved to: `server/product_review/track-b.md`
  - _Spec References: requirements.md sections 2.x, 3.x_
  - _Dependencies: CR-B_
  - _Agent: product-owner-reviewer_

- [ ] RW-B. **Rework: Address Track B Review Findings**
  - Fix data model issues if reviews fail
  - _Trigger: Only if CR-B or PR-B status is "Requires changes"_
  - _Dependencies: CR-B and/or PR-B (failed)_
  - _Agent: go-developer_

- [ ] CR-C. **Code Review: WebSocket Infrastructure**
  - Review WebSocket server implementation
  - _Dependencies: Task 15_
  - _Agent: go-websocket-specialist_

- [ ] PR-C. **Product Review: Track C WebSocket**
  - Validate WebSocket implementation meets requirements 1.x
  - Review output saved to: `server/product_review/track-c.md`
  - _Dependencies: CR-C_
  - _Agent: product-owner-reviewer_

- [ ] RW-C. **Rework: Address Track C Review Findings**
  - Fix WebSocket issues if reviews fail
  - _Trigger: Only if CR-C or PR-C status is "Requires changes"_
  - _Dependencies: CR-C and/or PR-C (failed)_
  - _Agent: go-websocket-specialist_

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
- go-code-reviewer: Go code review specialist
- product-owner-reviewer: Product specification compliance specialist

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

### Review Track

- [ ] CR-A. **Code Review: Foundation Code**
  - Review project structure and setup
  - Check Go module configuration and best practices
  - _Dependencies: Task 1_
  - _Agent: go-code-reviewer_

- [ ] PR-A. **Product Review: Track A Foundation**
  - Validate foundation meets requirements 1.x
  - Review output saved to: `server/product_review/track-a.md`
  - _Spec References: requirements.md sections 1.x_
  - _Dependencies: CR-A_
  - _Agent: product-owner-reviewer_

- [ ] RW-A. **Rework: Address Track A Review Findings**
  - Fix foundation issues if reviews fail
  - _Trigger: Only if CR-A or PR-A status is "Requires changes"_
  - _Dependencies: CR-A and/or PR-A (failed)_
  - _Agent: go-developer_
```

## Next Phase
After approval:
- Use `/spec:parallel_execute {feature-name}` to execute all tasks
- Use `/spec:parallel_execute {feature-name} --track=A` for specific tracks
- Check progress with `/spec:status {feature-name}`

For optimization workflow:
- Use `/spec:5_tasks {feature-name} --optimize-existing` to optimize partially completed task lists
- The optimization preserves completed work while improving parallel execution for remaining tasks