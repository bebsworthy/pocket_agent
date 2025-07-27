---
description: List all feature specifications with their current status
argument-hint: [--module <module> | --cross-module] [--status <status>]
---

# Spec List Command

List all specs in the current project.

## Usage
```
/spec:list
```

## Instructions
Display a comprehensive list of all specs in the project.

### Step 1: Scan All Feature Locations

1. **Cross-Module Features**
   - Scan `./documentation/features/` directory
   - Identify features by presence of:
     - `context.md`
     - `modules/` subdirectory with module-specific files
     - `integration.md` (for cross-module coordination)

2. **Single-Module Features**
   - Scan each `./documentation/modules/*/features/` directory
   - Identify features by presence of standard spec files
   - Note which module contains each feature

### Step 2: Gather Feature Information

For each feature found:
1. **Determine Type**
   - Cross-module: Has module subdirectories
   - Single-module: Within specific module directory

2. **Check Phase**
   - Look for existence of: context.md, research.md, requirements.md, design.md, tasks.md
   - For cross-module: Check each module's progress
   - Determine current phase based on latest incomplete step

3. **Count Tasks**
   - Parse tasks.md files
   - Count completed [x] vs pending [ ] tasks
   - For cross-module: Sum across all modules

4. **Get Metadata**
   - Last modified date of most recent file
   - Brief description from context.md

### Step 3: Display Organized List

```
📋 Project Specs Overview

═══ Cross-Module Features ═══

1. communication-layer (In Progress)
   Modules: server, frontend-react, frontend-android
   Phase: Implementation
   Progress: 12/45 tasks complete (27%)
   ├─ server: 5/15 tasks
   ├─ frontend-react: 4/20 tasks
   └─ frontend-android: 3/10 tasks
   Last updated: 2025-01-27

2. security-authentication (Planning)
   Modules: server, frontend-react
   Phase: Design (awaiting approval)
   Last updated: 2025-01-26

═══ Single-Module Features ═══

3. [server] api-rate-limiting (Planning)
   Module: server
   Phase: Requirements
   Last updated: 2025-01-25

4. [frontend-react] dark-mode-toggle (In Progress)
   Module: frontend-react
   Phase: Implementation
   Progress: 5/8 tasks complete (63%)
   Last updated: 2025-01-24

5. [frontend-android] offline-sync (Complete)
   Module: frontend-android
   Phase: Complete
   Progress: 10/10 tasks complete (100%)
   Last updated: 2025-01-20

═══ Summary ═══
Total Features: 5
├─ Cross-Module: 2
└─ Single-Module: 3

By Status:
├─ Complete: 1
├─ In Progress: 2
└─ Planning: 2

By Module:
├─ server: 3 features
├─ frontend-react: 3 features
└─ frontend-android: 2 features
```

### Step 4: Provide Actionable Insights

1. **Features Needing Attention**
   - Highlight features stuck in a phase for too long
   - Flag features with no recent activity
   - Identify blocking dependencies

2. **Suggested Next Actions**
   ```
   Suggested Actions:
   • security-authentication: Needs design approval (/spec:4_design security-authentication)
   • api-rate-limiting: Ready for design phase (/spec:4_design api-rate-limiting)
   • dark-mode-toggle: Continue implementation (/spec:6_execute 6 dark-mode-toggle)
   ```

3. **Module Workload**
   - Show which modules have the most active features
   - Identify potential resource constraints
