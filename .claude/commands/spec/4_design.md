# Spec Design Command

Generate design document based on approved requirements.

## Usage
```
/spec:4_design [feature-name]
```

## Instructions
You are working on the design phase of the spec workflow.

### Step 1: Determine Feature Type and Location

1. **Identify Current Spec**
   - If no feature-name provided, search in both:
     - `./documentation/features/` (cross-module features)
     - `./documentation/modules/*/features/` (single-module features)
   - If multiple specs exist, ask user to specify which one

2. **Detect Feature Type**
   - **Cross-Module Feature**: If found in `documentation/features/{feature-name}/`
     - Design will include integration.md + module-specific designs
   - **Single-Module Feature**: If found in `documentation/modules/{module}/features/{feature-name}/`
     - Design will be created in that location

### Step 2: Prerequisites and Context Loading

1. **For Cross-Module Features**
   - Load `documentation/features/{feature-name}/context.md`
   - Load `documentation/features/{feature-name}/research.md` if available
   - Ensure all module requirements exist in `modules/{module}/requirements.md`
   - Load requirements from each module subdirectory
   - Load global `documentation/architecture.md` for system context

2. **For Single-Module Features**
   - Load `documentation/modules/{module}/features/{feature-name}/context.md`
   - Load `documentation/modules/{module}/features/{feature-name}/research.md` if available
   - Ensure requirements.md exists and is approved
   - Load module-specific architecture from `documentation/modules/{module}/architecture.md` if exists

### Step 3: Generate Design Documents

1. **For Cross-Module Features**
   - Create `documentation/features/{feature-name}/integration.md`:
     - Overall system design and architecture
     - Module interaction diagrams
     - API contracts between modules
     - Data flow across modules
     - Synchronization and consistency strategies
   - Create module-specific designs in `documentation/features/{feature-name}/modules/{module}/design.md`:
     - Module-specific implementation details
     - Internal architecture within the module
     - Module-specific components and patterns
   
2. **For Single-Module Features**
   - Create design in `documentation/modules/{module}/features/{feature-name}/design.md`
   - Focus on module-specific implementation
   - Reference module's architectural patterns

### Step 4: Design Content Structure

Include all required sections:
- **Overview**: High-level description and goals
- **Architecture**: System design with Mermaid diagrams
- **Components and Interfaces**: Detailed specifications
- **Data Models**: Structures and validation rules
- **Error Handling**: Error scenarios and recovery
- **Testing Strategy**: Testing approach and coverage

**For Cross-Module Features**, also include:
- **Module Boundaries**: Clear separation of concerns
- **Integration Points**: How modules communicate
- **Data Consistency**: Cross-module data integrity
- **Deployment Considerations**: Module dependencies

### Step 5: Technical Considerations

- Analyze existing codebase for patterns
- Identify integration points
- Research technical dependencies
- Consider scalability and maintainability
- Use Mermaid diagrams for visual representations
- Define clear interfaces and contracts
- Plan comprehensive error handling
- **For cross-module**: Design with loose coupling in mind
- **For single-module**: Optimize for module cohesion

### Step 6: Approval Process

1. **Present Design Documents**
   - For cross-module: Show integration.md first, then module-specific designs
   - For single-module: Show the complete design document
   
2. **Request Approval**
   - Ask: "Does the design look good? If so, we can move on to the implementation plan."
   - Incorporate feedback and revisions
   - Continue until explicit approval

## Design Structure
```markdown
# Design Document

## Overview
[High-level description]

## Architecture
[System architecture and patterns]

## Components and Interfaces
[Detailed component specifications]

## Data Models
[Data structures and validation]

## Error Handling
[Error scenarios and responses]

## Testing Strategy
[Testing approach and coverage]
```

## Next Phase
After approval, proceed to `/spec:5_tasks`.
