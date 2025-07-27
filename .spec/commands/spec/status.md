---
description: Check the implementation progress of a feature specification
argument-hint: <feature-name> [--verbose]
---

# Spec Status Command

Show current status of all specs or a specific spec.

## Usage
```
/spec:status [feature-name]
```

## Instructions
Display the current status of spec workflows.

### Step 1: Locate Features

1. **Search All Feature Locations**
   - Cross-module features: `./documentation/features/`
   - Single-module features: `./documentation/modules/*/features/`
   - Build comprehensive list of all features

2. **Categorize Features**
   - **Cross-Module**: Features with `integration.md` or module subdirectories
   - **Single-Module**: Features within a specific module directory

### Step 2: Display Status

#### If no feature-name provided:
1. **List All Specs**
   ```
   📋 Feature Status Overview
   
   Cross-Module Features:
   ├─ communication-layer
   │  Modules: server, frontend-react, frontend-android
   │  Phase: Implementation (12/45 tasks complete)
   │  
   └─ security-authentication
      Modules: server, frontend-react
      Phase: Design (awaiting approval)
   
   Single-Module Features:
   ├─ [server] api-rate-limiting
   │  Phase: Requirements
   │  
   └─ [frontend-react] dark-mode-toggle
      Phase: Implementation (5/8 tasks complete)
   ```

#### If feature-name provided:
1. **Determine Feature Type**
   - Check if cross-module or single-module
   - Load appropriate files

2. **For Cross-Module Features**
   - Check each module's status separately
   - Show overall progress and per-module breakdown
   ```
   Spec: communication-layer (Cross-Module)
   
   Overall Progress:
   ├─ Context: ✅
   ├─ Research: ✅
   └─ Integration Design: ✅
   
   Module Status:
   ├─ server
   │  Requirements: ✅ | Design: ✅ | Tasks: ✅
   │  Implementation: 5/15 tasks complete
   │  
   ├─ frontend-react
   │  Requirements: ✅ | Design: ✅ | Tasks: ✅
   │  Implementation: 3/20 tasks complete
   │  
   └─ frontend-android
      Requirements: ✅ | Design: 🚧 | Tasks: ⏳
      Status: Awaiting design approval
   
   Next Actions:
   - Complete design for frontend-android
   - Continue implementation in server (task 6)
   - Continue implementation in frontend-react (task 4)
   ```

3. **For Single-Module Features**
   - Show standard single-module status
   ```
   Spec: api-rate-limiting (Single-Module: server)
   
   Progress:
   ├─ Context: ✅
   ├─ Research: ✅
   ├─ Requirements: ✅
   ├─ Design: ✅
   └─ Tasks: ✅
   
   Implementation: 3/8 tasks complete
   
   Next: Execute task 4 - "Implement rate limit storage"
   Use: /spec:6_execute 4 api-rate-limiting
   ```

### Step 3: Status Information

Check existence and content of files to determine status:
- **Context**: Check if context.md exists
- **Research**: Check if research.md exists and has content
- **Requirements**: Check if requirements.md exists and has content
- **Design**: Check if design.md (and integration.md for cross-module) exists
- **Tasks**: Check if tasks.md exists and count [ ] vs [x]
- **Implementation**: Count completed tasks

### Step 4: Provide Actionable Next Steps

Based on current phase, suggest next command:
- Missing research: `/spec:2_research {feature-name}`
- Missing requirements: `/spec:3_requirements {feature-name}`
- Missing design: `/spec:4_design {feature-name}`
- Missing tasks: `/spec:5_tasks {feature-name}`
- Ready to implement: `/spec:6_execute {next-task-id} {feature-name}`

## Workflow Phases
- **Requirements**: Gathering and documenting requirements
- **Design**: Creating technical design and architecture
- **Tasks**: Breaking down into implementation tasks
- **Implementation**: Executing individual tasks
- **Complete**: All tasks finished and integrated
