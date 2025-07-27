# Spec Execute Command

Execute specific tasks from the approved task list.

## Usage
```
/spec:6_execute [task-id] [feature-name] [--module <module>]
```

## Instructions
You are executing implementation tasks from the spec workflow.

### Step 1: Determine Feature Type and Location

1. **Parse Arguments**
   - Extract task-id (e.g., "1", "2.1", "3")
   - Extract feature-name
   - Check for --module flag (indicates module context for cross-module features)

2. **Locate Feature**
   - Search in both:
     - `./documentation/features/{feature-name}/` (cross-module)
     - `./documentation/modules/*/features/{feature-name}/` (single-module)
   - If not found, report error

3. **Determine Context**
   - **Cross-Module Feature**: Found in `documentation/features/{feature-name}/`
     - If --module provided: Work in that module's context
     - If no --module: Ask which module's task to execute
   - **Single-Module Feature**: Found in `documentation/modules/{module}/features/{feature-name}/`
     - Module context is implicit

### Step 2: Load Task Context

1. **For Cross-Module Features**
   - Load from `documentation/features/{feature-name}/`:
     - `integration.md` for overall design
     - `modules/{module}/requirements.md`
     - `modules/{module}/design.md`
     - `modules/{module}/tasks.md`
   - Focus on the specified module's task

2. **For Single-Module Features**
   - Load from `documentation/modules/{module}/features/{feature-name}/`:
     - `requirements.md`
     - `design.md`
     - `tasks.md`
   - Load module architecture if available

### Step 3: Execute Task

1. **Identify Specific Task**
   - Parse tasks.md to find the requested task-id
   - Verify task is not already completed [x]
   - Check for task dependencies

2. **Task Execution**
   - Focus on ONE task at a time
   - If task has sub-tasks, start with those
   - Follow the implementation details from design.md
   - Verify against requirements specified in the task
   - **For cross-module**: Be aware of module boundaries

3. **Implementation Guidelines**
   - Write clean, maintainable code
   - Follow existing code patterns and conventions
   - Include appropriate error handling
   - Add unit tests where specified
   - Document complex logic
   - **Module-aware**: Use appropriate module directory

### Step 4: Validation

- Verify implementation meets acceptance criteria
- Run tests if they exist
- Check for lint/type errors
- Ensure integration with existing code
- **For cross-module**: Verify module interfaces are respected

### Step 5: Task Completion

1. **Update Task Status**
   - **CRITICAL**: Mark task as complete in tasks.md by changing [ ] to [x]
   - Update in the correct location:
     - Cross-module: `documentation/features/{feature-name}/modules/{module}/tasks.md`
     - Single-module: `documentation/modules/{module}/features/{feature-name}/tasks.md`

2. **Report Completion**
   - Confirm task completion status to user
   - Example: "Task 2.1 for module 'server' has been marked as complete"
   - Stop and wait for user review
   - DO NOT automatically proceed to next task

## Task Selection
If no task-id specified:
- Look at tasks.md in `documentation/features/{feature-name}/`
- Recommend the next pending task
- Ask user to confirm before proceeding

## Examples
```
/spec:6_execute 1 user-authentication
/spec:6_execute 2.1 user-authentication
```

## Important Rules
- Only execute ONE task at a time
- **ALWAYS** mark completed tasks as [x] in tasks.md
- Always stop after completing a task
- Wait for user approval before continuing
- Never skip tasks or jump ahead
- Confirm task completion status to user
