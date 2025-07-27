# Spec Tasks Command

Generate implementation task list based on approved design.

## Usage
```
/spec:5_tasks [feature-name]
```

## Instructions
You are working on the tasks phase of the spec workflow.

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

### Step 2: Prerequisites and Context Loading

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

### Step 3: Generate Task Lists

1. **For Cross-Module Features**
   - Create coordinated task lists for each module
   - Save to `documentation/features/{feature-name}/modules/{module}/tasks.md`
   - Include:
     - Module-specific implementation tasks
     - Integration tasks that depend on other modules
     - Clear dependency markers between modules
   - Consider parallel execution opportunities
   - Mark cross-module dependencies explicitly

2. **For Single-Module Features**
   - Create tasks in `documentation/modules/{module}/features/{feature-name}/tasks.md`
   - Focus on module-specific implementation
   - Follow module's coding patterns

### Step 4: Task Guidelines

- Break design into atomic, executable coding tasks
- Use checkbox format with numbered hierarchy
- Each task should reference specific requirements
- Focus ONLY on coding tasks (no deployment, user testing, etc.)
- Tasks should be concrete and actionable
- Include specific file names and components
- Build incrementally (each task builds on previous)
- Reference requirements using _Requirements: X.Y_ format
- Use test-driven development approach

**For Cross-Module Features**:
- Group tasks by module
- Clearly mark inter-module dependencies
- Identify tasks that can be done in parallel
- Create integration testing tasks

### Step 5: Task Format

```markdown
# Implementation Tasks

## Module: {module-name} (if cross-module)

- [ ] 1. Task description
  - Sub-bullet with details
  - Specific files to create/modify
  - _Requirements: 1.1, 2.3_
  - _Dependencies: None_ or _Depends on: server-task-3_

- [ ] 2. Task with sub-tasks
- [ ] 2.1 Sub-task description
  - Implementation details
  - _Requirements: 2.1_
```

### Step 6: Excluded Tasks

- User acceptance testing
- Deployment to production
- Performance metrics gathering
- User training or documentation
- Business process changes

### Step 7: Approval Process

1. **Present Task Lists**
   - For cross-module: Show all module task lists with dependencies
   - For single-module: Show the complete task list
   - Highlight any complex dependencies or parallel execution opportunities

2. **Request Approval**
   - Ask: "Do the tasks look good?"
   - Make revisions based on feedback
   - Continue until explicit approval

**IMPORTANT**: No scripts or command generation in the new workflow

## Task Structure
```markdown
# Implementation Plan

- [ ] 1. Setup project structure
  - Create directory structure
  - Define core interfaces
  - _Requirements: 1.1_

- [ ] 2. Implement data models
- [ ] 2.1 Create base model classes
  - Define validation methods
  - Write unit tests
  - _Requirements: 2.1, 2.2_
```

## Next Phase
After approval and command generation, you can:
- Use `/spec:6_execute` to implement tasks
- Use individual task commands: `/{feature-name}-task-1`, `/{feature-name}-task-2`, etc.
- Check progress with `/spec:status {feature-name}`
