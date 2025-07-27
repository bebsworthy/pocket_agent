---
description: Research and analyze requirements for a feature specification
argument-hint: [feature-name]
---

# Feature Research Command

Perform lightweight, feature-specific research leveraging the global architecture documentation.

## Usage
```
/spec:2_research <feature-name>
```

## Prerequisites
- Must have `documentation/architecture.md` (run `/spec:architecture` first)
- Must have created the feature spec with `/spec:1_create`

## Instructions

You are performing focused research for a specific feature, building on the existing architecture documentation to avoid redundant analysis.

**FILE LOCATIONS**: 
- Single-module features: `documentation/modules/{module}/features/{feature-name}/`
- Cross-module features: `documentation/features/{feature-name}/`

### Process

1. **Determine Feature Type**
   - Check spec-config.json to determine if feature is single-module or cross-module
   - Identify base path for feature documentation

2. **Validate Prerequisites**
   - Check if `documentation/architecture.md` exists
     - If not: "Architecture documentation not found. Please run `/spec:architecture` first."
   - For single-module features:
     - Check if `documentation/modules/{module}/features/{feature-name}/context.md` exists
   - For cross-module features:
     - Check if `documentation/features/{feature-name}/context.md` exists
   - If context not found: "Feature spec not found. Please run `/spec:1_create {feature-name}` first."

3. **Load Context**
   - Read `documentation/architecture.md` for global codebase overview
   - For single-module features:
     - Read module-specific architecture: `documentation/modules/{module}/architecture.md` (if exists)
     - Read feature context from module path
   - For cross-module features:
     - Read feature context and integration.md from feature path
     - Load architecture docs for all involved modules
   - Understand the feature scope and requirements

4. **Feature-Specific Analysis** (Focused Search Only)
   
   a. **Find Similar Features**
      - For single-module: Search within the specific module
      - For cross-module: Search across all affected modules
      - Look for patterns that can be reused
      - Identify code that solves similar problems
      - Note what worked well and what to avoid
   
   b. **Identify Affected Components**
      - Based on architecture.md, identify which components will be touched
      - For single-module: Focus on module-specific components
      - For cross-module: Map components across all involved modules
      - Find integration points with existing code
      - Note which APIs or services will be affected
   
   c. **Assess Integration Requirements**
      - For single-module:
        - Identify module-specific API endpoints to modify or create
        - Find internal module integration points
      - For cross-module:
        - Map cross-module communication requirements
        - Identify shared data models and contracts
        - Plan synchronization and consistency strategies
      - Locate services that need integration
      - Map authentication/authorization touchpoints
   
   d. **Feature-Specific Constraints**
      - Check for patterns this feature MUST follow (from architecture.md)
      - For cross-module: Identify module-specific conventions
      - Identify code that CANNOT be changed
      - Find performance requirements specific to this area
      - Note security considerations for this feature

5. **Technical Considerations**
   - Identify if new dependencies are needed (only for this feature)
   - For cross-module: Check dependency compatibility across modules
   - Find technical debt in the specific area
   - Assess security implications of the feature
   - Consider performance impacts

6. **Generate Research Document**
   - Use the research template from `.spec/templates/research-template.md`
   - For cross-module features: Include module-specific sections
   - Focus on feature-specific findings only
   - Reference architecture.md instead of re-documenting general patterns
   - Keep the document concise (target: under 2000 tokens)

7. **Save and Continue**
   - For single-module features:
     - Save to `documentation/modules/{module}/features/{feature-name}/research.md`
   - For cross-module features:
     - Save overall research to `documentation/features/{feature-name}/research.md`
     - If module-specific research needed, save to respective module paths
   - Ask: "Research complete. Proceed to requirements phase?"
   - If yes, guide user to run `/spec:3_requirements`

### Key Principles

- **Leverage Existing Knowledge**: Reference architecture.md, don't repeat it
- **Stay Focused**: Only research what's specific to this feature
- **Be Practical**: Find actionable insights, not theoretical analysis
- **Save Tokens**: Keep analysis lightweight and targeted
- **Think Integration**: Focus on how this feature fits into existing code

### What NOT to Include

- General architecture patterns (already in architecture.md)
- Technology stack details (already in architecture.md)
- Overall project structure (already in architecture.md)
- Generic coding conventions (already in architecture.md)

### What TO Include

- Specific similar features and their implementation
- Exact files and components affected
- Feature-specific patterns to follow
- Integration points unique to this feature
- Risks and constraints for this specific feature
- For cross-module: Module-specific implementation patterns

### Module-Specific Research Focus

#### Single-Module Features
- Deep dive into module-specific patterns
- Internal module dependencies
- Module-specific conventions and standards

#### Cross-Module Features  
- Communication patterns between modules
- Data synchronization strategies
- API contracts and versioning
- Deployment dependencies
- Error handling across module boundaries

### Example Output Focus

**Single-Module Example:**
Instead of: "The project uses Express.js with middleware pattern"
Write: "The user-profile feature at `/api/users/profile` uses similar validation middleware that we can extend"

**Cross-Module Example:**
Instead of: "The project has frontend and backend modules"
Write: "The real-time notification feature uses WebSocket connections between server (`/server/src/websocket/`) and React client (`/frontend-react/src/hooks/useWebSocket.ts`)"

## Next Steps

After research approval, proceed to `/spec:3_requirements` to generate requirements informed by the research findings.