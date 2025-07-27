---
description: Create a new feature specification in the spec-driven workflow
argument-hint: <feature-name> [--module <module> | --cross-module] [description]
---

# Spec Create Command

Create a new feature specification following the spec-driven workflow.

## Usage
```
/spec:1_create <feature-name> [description]                    # Legacy: creates cross-module feature
/spec:1_create <feature-name> --module <module> [description]  # Module-specific feature
/spec:1_create <feature-name> --cross-module [description]     # Cross-module feature (explicit)
```

### Available Modules
- `server`: Backend server and API services
- `frontend-react`: React web application frontend  
- `frontend-android`: Android mobile application

## Instructions
You are helping create a new feature specification. Follow these steps:

**WORKFLOW SEQUENCE**: 1_Create → 2_Research → 3_Requirements → 4_Design → 5_Tasks → 6_Execute
**NEW**: Research phase now follows create phase for better context.

### Step 1: Determine Feature Type and Module

**Parse the command arguments:**
- If `--module <module>` is provided: Create **single-module feature**
- If `--cross-module` is provided: Create **cross-module feature**
- If neither flag: Create **cross-module feature** (legacy behavior)

### Step 2: Check Prerequisites
- Check if global `documentation/architecture.md` exists
- For module-specific features: Check if `documentation/modules/{module}/architecture.md` exists
- If missing: Inform user but continue (architecture is helpful but not required)

### Step 3: Create Directory Structure

#### For Single-Module Features (`--module <module>`)
**FILE LOCATION**: `documentation/modules/{module}/features/{feature-name}/`

1. Create directory: `./documentation/modules/{module}/features/{feature-name}`
2. Create files:
   - `context.md` with feature name, description, and module
   - `research.md` (empty)
   - `requirements.md` (empty)
   - `design.md` (empty)
   - `tasks.md` (empty)

#### For Cross-Module Features (`--cross-module` or no flags)
**FILE LOCATION**: `documentation/features/{feature-name}/`

1. Create directory: `./documentation/features/{feature-name}`
2. Create files:
   - `context.md` with feature name and description
   - `integration.md` (empty) 
3. Create module subdirectories:
   - `./documentation/features/{feature-name}/modules/server/`
   - `./documentation/features/{feature-name}/modules/frontend-react/`
   - `./documentation/features/{feature-name}/modules/frontend-android/`
4. In each module subdirectory, create:
   - `requirements.md` (empty)
   - `design.md` (empty) 
   - `tasks.md` (empty)

### Step 4: Save Feature Context
- Save feature name, description, and module information to `context.md`
- For cross-module features, note which modules are involved
- This context will be used by the research phase

### Step 5: Guide to Research Phase
- For single-module: "Feature spec created for {module}. Next: `/spec:2_research {feature-name}`"
- For cross-module: "Cross-module feature spec created. Next: `/spec:2_research {feature-name}` to analyze across all modules"

### Step 6: Complete Create Phase
- Do NOT generate requirements yet
- Do NOT run any scripts
- **NEXT STEP**: User should run `/spec:2_research {feature-name}`

6. **Rules**
   - Only create ONE spec at a time
   - Always use kebab-case for feature names
   - Follow the exact EARS format for acceptance criteria
   - Do not proceed without explicit user approval
   - **DO NOT** run scripts during /spec:1_create - only create requirements

## Example
```
/spec:1_create user-authentication "Allow users to sign up and log in securely"
```

## Next Steps
After creating the feature spec:
1. Run `/spec:2_research {feature-name}` to analyze codebase context
2. Or skip to `/spec:3_requirements {feature-name}` (not recommended)
