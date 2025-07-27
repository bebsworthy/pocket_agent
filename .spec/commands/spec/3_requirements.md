# Spec Requirements Command

Generate or update requirements document for an existing spec.

## Usage
```
/spec:3_requirements [feature-name]
```

## Instructions
You are working on the requirements phase of the spec workflow.

### Step 1: Determine Feature Type and Location

1. **Identify Current Spec**
   - If no feature-name provided, search in both:
     - `./documentation/features/` (cross-module features)
     - `./documentation/modules/*/features/` (single-module features)
   - If multiple specs exist, ask user to specify which one

2. **Detect Feature Type**
   - **Cross-Module Feature**: If found in `documentation/features/{feature-name}/`
     - Requirements will be created per module in `modules/{module}/requirements.md`
   - **Single-Module Feature**: If found in `documentation/modules/{module}/features/{feature-name}/`
     - Requirements will be created in that location

### Step 2: Load Context

1. **For Cross-Module Features**
   - Load `documentation/features/{feature-name}/context.md`
   - Load `documentation/features/{feature-name}/research.md` if exists
   - Check each module subdirectory for existing requirements

2. **For Single-Module Features**
   - Load `documentation/modules/{module}/features/{feature-name}/context.md`
   - Load `documentation/modules/{module}/features/{feature-name}/research.md` if exists
   - Load existing requirements.md if exists

### Step 3: Generate Requirements

1. **For Cross-Module Features**
   - Create module-specific requirements for each relevant module
   - Save to `documentation/features/{feature-name}/modules/{module}/requirements.md`
   - Ensure requirements are coordinated across modules
   - Reference integration points between modules

2. **For Single-Module Features**
   - Create requirements focused on the specific module
   - Save to `documentation/modules/{module}/features/{feature-name}/requirements.md`
   - Focus on module-specific constraints and patterns

3. **Requirements Structure**
   - **Check for Research**: If `research.md` exists, use its findings to inform requirements
   - Use EARS format (Easy Approach to Requirements Syntax)
   - Structure: Introduction, Requirements with User Stories and Acceptance Criteria
   - Each requirement should have:
     - User story: "As a [role], I want [feature], so that [benefit]"
     - Numbered acceptance criteria: "WHEN [event] THEN [system] SHALL [response]"
   - **Leverage Research**: Reference similar features, constraints, and recommendations from research

### Step 4: Content Guidelines

- Consider edge cases and error handling
- Include non-functional requirements (performance, security, etc.)
- **If research exists**, incorporate:
  - Constraints identified in research
  - Security considerations from research
  - Performance requirements from research
  - Integration points discovered
- Reference existing codebase patterns from research findings
- Ensure requirements are testable and verifiable
- **For cross-module**: Clearly define module boundaries and interfaces
- **For single-module**: Focus on module-specific implementation details

### Step 5: Approval Process

- Present the complete requirements document(s)
- For cross-module features, show requirements for each module
- Ask: "Do the requirements look good? If so, we can move on to the design."
- Make revisions based on feedback
- Continue until explicit approval is received

## Requirements Format
```markdown
# Requirements Document

## Introduction
[Brief summary of the feature]

### Research Context
[If research.md exists, reference key findings that inform these requirements]

## Requirements

### Requirement 1
**User Story:** As a [role], I want [feature], so that [benefit]

#### Acceptance Criteria
1. WHEN [event] THEN [system] SHALL [response]
2. IF [condition] THEN [system] SHALL [response]
```

## Next Phase
After approval, proceed to `/spec:4_design`.
