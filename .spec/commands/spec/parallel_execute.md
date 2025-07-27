# Spec Parallel Execute Command

Execute all pending tasks from the approved task list using parallel agents according to the defined execution strategy.

## Usage
```
/spec:parallel_execute [feature-name] [options]
```

## Options
- `--max-agents=N` - Maximum number of parallel agents (default: 6)
- `--dry-run` - Show execution plan without running tasks
- `--continue-on-error` - Continue executing other tasks if one fails
- `--track=TRACK` - Execute only specific track (A-F)

## Instructions
You are the parallel execution coordinator for the spec workflow.

### Phase 1: Analysis and Planning

1. **Determine Feature Type and Location**
   - Search for feature in both:
     - `./documentation/features/{feature-name}/` (cross-module)
     - `./documentation/modules/*/features/{feature-name}/` (single-module)
   - Detect if cross-module or single-module feature

2. **Load Feature Context**
   - **For Cross-Module Features**:
     - Load integration.md for overall design
     - Load tasks.md from each `modules/{module}/` subdirectory
     - Load module-specific requirements and designs
     - Build cross-module dependency map
   - **For Single-Module Features**:
     - Load tasks.md from the module's feature directory
     - Load requirements.md and design.md for agent context
   - Identify all pending tasks (marked with `[ ]`)

3. **Build Execution Plan**
   - Parse parallel execution groups from tasks.md
   - Identify task dependencies
   - **For Cross-Module**: 
     - Group tasks by module AND track
     - Identify cross-module dependencies
     - Plan module-parallel execution
   - **For Single-Module**:
     - Group tasks by tracks (A-F) as defined
   - Determine which tasks can start immediately

4. **Display Execution Plan** (if --dry-run or for confirmation)
   
   **For Cross-Module Features:**
   ```
   Execution Plan for communication-layer (Cross-Module):
   
   Module: server
   Ready to Execute:
   - Track A: Tasks 1-3 (WebSocket foundation)
   - Track B: Tasks 4-5 (API endpoints)
   
   Module: frontend-react
   Ready to Execute:
   - Track A: Tasks 1-2 (WebSocket client)
   Waiting on server:
   - Track B: Tasks 3-5 (depends on server API)
   
   Module: frontend-android
   Ready to Execute:
   - Track A: Tasks 1-2 (WebSocket client)
   Waiting on server:
   - Track B: Tasks 3-4 (depends on server API)
   
   Total: 16 tasks across 3 modules
   ```
   
   **For Single-Module Features:**
   ```
   Execution Plan for api-rate-limiting (Module: server):
   
   Ready to Execute (Group 1):
   - Track A: Tasks 2, 4-6 (4 tasks)
   - Track B: Tasks 7-8 (2 tasks)
   
   Waiting on Dependencies:
   - Group 2: Tasks 9-12 (depends on Group 1)
   
   Total: 12 tasks
   ```

### Phase 2: Parallel Execution

1. **Launch Parallel Agents**
   - Use the Task tool to launch multiple agents concurrently
   - Each agent receives:
     - Specific task ID(s) from their track
     - Full feature context (requirements, design)
     - Clear success criteria
     - Instruction to mark task complete in tasks.md

2. **Agent Instructions Template**
   
   **For Cross-Module Features:**
   ```
   You are executing task {task_id} for module {module} in the {feature_name} feature.
   
   Context files:
   - Integration design: documentation/features/{feature_name}/integration.md
   - Module files: documentation/features/{feature_name}/modules/{module}/
   
   Module: {module}
   Task Details: {task_description}
   Requirements: {requirements_reference}
   
   Instructions:
   1. Read integration.md for overall design
   2. Read module-specific requirements.md and design.md
   3. Implement in the {module} directory
   4. Respect module boundaries and interfaces
   5. Follow module-specific patterns
   6. Run module tests if applicable
   7. Mark task as complete [x] in modules/{module}/tasks.md
   8. Report completion status
   
   IMPORTANT: Stay within your module boundaries. Cross-module dependencies: {dependencies}
   ```
   
   **For Single-Module Features:**
   ```
   You are executing task {task_id} for the {feature_name} feature.
   
   Context files are in documentation/modules/{module}/features/{feature_name}/
   
   Task Details: {task_description}
   Requirements: {requirements_reference}
   
   Instructions:
   1. Read requirements.md and design.md for context
   2. Implement the task as specified
   3. Follow existing code patterns
   4. Run tests if applicable
   5. Mark task as complete [x] in tasks.md
   6. Report completion status
   
   IMPORTANT: Only work on the assigned task. Do not proceed to other tasks.
   ```

3. **Progress Monitoring**
   - Track completion status from each agent
   - Update overall progress display
   - Launch new agents as dependencies are satisfied

### Phase 3: Coordination

1. **Dependency Management**
   - After each agent completes, check for newly available tasks
   - Launch agents for tasks whose dependencies are now met
   - Maintain maximum agent limit

2. **Error Handling**
   - If an agent reports failure:
     - Log the error with task details
     - Mark task as failed in tracking
     - Decide whether to continue (based on --continue-on-error)
     - Skip dependent tasks if parent fails

3. **Status Updates**
   
   **For Cross-Module Features:**
   ```
   Progress: 12/32 tasks complete across 3 modules
   
   Module Status:
   server: 5/10 complete
   - Active: Task 6 (API endpoint)
   - Completed: Tasks 1-5
   
   frontend-react: 4/12 complete  
   - Active: Task 5 (UI component)
   - Completed: Tasks 1-4
   - Blocked: Tasks 6-8 (waiting on server)
   
   frontend-android: 3/10 complete
   - Active: Task 4 (UI component)
   - Completed: Tasks 1-3
   
   Time Elapsed: 2h 15m
   ```
   
   **For Single-Module Features:**
   ```
   Progress: 12/32 tasks complete
   Active: Track A (Task 5), Track C (Task 10), Track E (Task 13)
   Completed: Tasks 1-4, 7-9, 19-21
   Failed: None
   Time Elapsed: 2h 15m
   ```

### Implementation Example

```python
# Pseudo-code for parallel execution logic
def parallel_execute(feature_name, options):
    # Load task list and dependencies
    tasks = parse_tasks_md(feature_name)
    groups = identify_execution_groups(tasks)
    
    # Initialize tracking
    active_agents = []
    completed_tasks = []
    failed_tasks = []
    
    # Execute groups in order
    for group in groups:
        # Wait for dependencies
        wait_for_dependencies(group.dependencies, completed_tasks)
        
        # Launch agents for ready tasks
        for track in group.tracks:
            if len(active_agents) < options.max_agents:
                agent = launch_agent(track.tasks, feature_name)
                active_agents.append(agent)
        
        # Monitor progress
        while active_agents:
            for agent in active_agents:
                if agent.is_complete():
                    if agent.success:
                        completed_tasks.extend(agent.tasks)
                    else:
                        failed_tasks.extend(agent.tasks)
                    active_agents.remove(agent)
                    
                    # Launch new agents for freed dependencies
                    check_and_launch_new_agents()
            
            display_progress(completed_tasks, active_agents, failed_tasks)
            sleep(5)  # Check every 5 seconds
```

### Special Considerations

1. **Task Batching**
   - Small related tasks in the same track can be batched to a single agent
   - Example: Tasks 4-6 (Configuration System) could be one agent assignment

2. **Resource Management**
   - Default max agents: 6 (one per track)
   - Adjust based on task complexity and system resources

3. **Failure Modes**
   - Critical path failures should stop execution
   - Independent track failures can continue if --continue-on-error

4. **Completion Criteria**
   - All tasks marked as [x] in tasks.md
   - Summary report generated
   - Return success only if all tasks complete

## Examples

### Basic Usage
```
/spec:parallel_execute {feature-name}
```

### Limited Parallelism
```
/spec:parallel_execute {feature-name} --max-agents=3
```

### Dry Run
```
/spec:parallel_execute {feature-name} --dry-run
```

### Execute Specific Track
```
/spec:parallel_execute {feature-name} --track=A
```

### Continue on Error
```
/spec:parallel_execute {feature-name} --continue-on-error
```

## Important Rules
- Always respect task dependencies
- Each agent must work independently
- Tasks must be marked complete in tasks.md
- Provide clear progress visibility
- Handle failures gracefully
- Generate comprehensive completion report