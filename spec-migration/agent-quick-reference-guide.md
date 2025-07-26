# Agent Quick Reference Guide

This guide consolidates all format requirements, templates, and best practices for feature agents. Use this as your primary reference during spec workflow execution.

## Table of Contents
1. [Workflow Overview](#workflow-overview)
2. [Format Requirements](#format-requirements)
3. [Document Templates](#document-templates)
4. [Common Mistakes & Solutions](#common-mistakes--solutions)
5. [Agent-Specific Tasks](#agent-specific-tasks)
6. [Quick Lookup Tables](#quick-lookup-tables)

## Workflow Overview

### Phase Sequence (MUST follow in order)
1. **Architecture** (`/spec:architecture`) - One-time setup
2. **Create** (`/spec:1_create`) - Initialize feature
3. **Research** (`/spec:2_research`) - Feature-specific research
4. **Requirements** (`/spec:3_requirements`) - User stories & acceptance criteria
5. **Design** (`/spec:4_design`) - Technical architecture
6. **Tasks** (`/spec:5_tasks`) - Implementation breakdown
7. **Execute** (`/spec:6_execute`) - One task at a time

### Critical Rules
- ✅ **ALWAYS** wait for user approval between phases
- ✅ **NEVER** skip phases or run scripts early
- ✅ **EXECUTE** only one task at a time
- ✅ **MARK** completed tasks as [x] in tasks.md
- ✅ **STOP** after completing each task

## Format Requirements

### 1. Requirement Numbering (X.Y Format)

**Format**: `X.Y` where X = Story number, Y = Acceptance criteria number

```markdown
### Story 1: Navigate App Screens
**Acceptance Criteria**:
- 1.1: WHEN I launch the app THEN the system SHALL display appropriate screen based on authentication state
- 1.2: WHEN I tap navigation elements THEN the system SHALL perform smooth transitions within 300ms
- 1.3: WHEN network is unavailable THEN the system SHALL show cached content with offline indicator
```

### 2. EARS Format (Event-Action-Response-System)

**Correct Patterns**:
- `WHEN [event] THEN the system SHALL [response]`
- `IF [condition] THEN the system SHALL [response]`
- `WHILE [state] the system SHALL [behavior]`

**Common Mistakes**:
- ❌ "WHEN I open the app THEN I SHALL see..."
- ❌ "User shall be able to..."
- ❌ Missing "the system"
- ✅ "WHEN the user opens the app THEN the system SHALL display..."

### 3. Task Reference Format

**Required Format**:
```markdown
- [ ] 1.1 Implement navigation controller
  - Create NavigationController.kt
  - Add navigation graph
  - Configure deep links
  - _Requirements: 1.1, 1.2, 1.5_
```

**Key Elements**:
- Checkbox format `- [ ]`
- Hierarchical numbering
- Specific subtasks
- Italic requirement references: `_Requirements: X.Y_`

### 4. Code Block Purpose Statements

**Before EVERY code block**:
```markdown
**Purpose**: This code demonstrates how to initialize the navigation component with proper error handling and state restoration.

```kotlin
@Composable
fun AppNavigation() {
    // implementation
}
```
```

## Document Templates

### Prerequisites Section (Required in all tasks.md)

```markdown
## Prerequisites

### Required Reading
- [ ] Review `context.md` for business understanding
- [ ] Review `research.md` for technical decisions
- [ ] Review `requirements.md` for all user stories
- [ ] Review `design.md` for implementation approach
- [ ] Review global `architecture.md` for system context

### Development Environment
- [ ] Android Studio configured
- [ ] Kotlin 1.9+ installed
- [ ] Required dependencies available
- [ ] Test devices/emulators ready

### Dependencies
- [ ] Data Layer feature completed (if applicable)
- [ ] Communication Layer available (if applicable)
```

### Requirements Template Structure

```markdown
## Requirements

### Requirement 1: [Feature Name]
**User Story:** As a [role], I want [feature], so that [benefit]

#### Acceptance Criteria
1.1: WHEN [event] THEN the system SHALL [response]
1.2: IF [condition] THEN the system SHALL [response]
1.3: WHEN [event] AND [condition] THEN the system SHALL [response]
```

### Task Template Structure

```markdown
- [ ] 1. Task description
  - Specific implementation detail
  - Files to create/modify
  - _Requirements: 1.1, 2.3_
  
- [ ] 2. Task group
- [ ] 2.1 Subtask description
  - Implementation steps
  - _Requirements: 2.1_
- [ ] 2.2 Another subtask
  - Implementation steps
  - _Requirements: 2.2, 2.3_
```

## Common Mistakes & Solutions

### 1. Requirements Issues

| Mistake | Example | Solution |
|---------|---------|----------|
| Missing system actor | "WHEN user clicks THEN display menu" | "WHEN user clicks THEN **the system SHALL** display menu" |
| No numbering | Random criteria listed | Use X.Y format: 1.1, 1.2, 2.1, etc. |
| Using "I SHALL" | "THEN I SHALL see results" | "THEN **the system SHALL** display results" |
| Vague criteria | "System should work properly" | "System SHALL respond within 300ms" |

### 2. Task Issues

| Mistake | Example | Solution |
|---------|---------|----------|
| No requirement refs | Task without traceability | Add `_Requirements: X.Y_` to each task |
| Wrong reference format | "See requirements section" | Use `_Requirements: 1.1, 1.2_` |
| Multiple tasks at once | Executing tasks 1-5 together | Execute ONE task, mark [x], stop |
| Not marking complete | Leaving tasks as [ ] | Change to [x] when done |

### 3. Design Issues

| Mistake | Example | Solution |
|---------|---------|----------|
| No purpose statements | Code blocks without context | Add **Purpose**: statement before code |
| Missing package declarations | Incomplete code examples | Include full package/import statements |
| Non-compilable code | Pseudo-code examples | Provide working, testable code |

### 4. Research Issues

| Mistake | Example | Solution |
|---------|---------|----------|
| No executive summary | Diving into details | Start with 1-2 paragraph overview |
| Missing file paths | "In the data layer..." | "In `app/src/main/java/com/pocketagent/data/`" |
| No version specifics | "Use Room" | "Use Room 2.6.0+" |

## Agent-Specific Tasks

### Architecture Agent (`/spec:architecture`)
**When**: One-time setup or major changes
**Focus**: Document overall codebase structure
**Output**: `documentation/architecture.md`

Key sections:
- Technology stack
- Project structure
- Design patterns
- Coding conventions
- Common components

### Research Agent (`/spec:2_research`)
**When**: After feature creation
**Focus**: Feature-specific investigation
**Output**: `documentation/features/{name}/research.md`

Must include:
- Executive Summary (1-2 paragraphs)
- Codebase Analysis with file paths
- Similar features found
- Integration points
- Risk assessment (3+ risks)
- Technology recommendations with versions

### Requirements Agent (`/spec:3_requirements`)
**When**: After research approval
**Focus**: User stories and acceptance criteria
**Output**: `documentation/features/{name}/requirements.md`

Must follow:
- X.Y numbering system
- EARS format with "the system SHALL"
- Complete user stories (As a... I want... So that...)
- Non-functional requirements
- Success metrics

### Design Agent (`/spec:4_design`)
**When**: After requirements approval
**Focus**: Technical architecture
**Output**: `documentation/features/{name}/design.md`

Must include:
- Architecture overview
- Component specifications
- Mermaid diagrams
- Purpose statements before code
- Compilable code examples
- Error handling strategy
- Testing approach

### Tasks Agent (`/spec:5_tasks`)
**When**: After design approval
**Focus**: Implementation breakdown
**Output**: `documentation/features/{name}/tasks.md`

Must follow:
- Checkbox format `- [ ]`
- Hierarchical numbering
- Requirement references `_Requirements: X.Y_`
- Prerequisites section
- Atomic, executable tasks
- No user testing/deployment tasks

### Implementation Agent (`/spec:6_execute`)
**When**: After tasks approval
**Focus**: Execute ONE task at a time

Critical protocol:
1. Load all context (requirements, design, tasks)
2. Execute ONLY specified task
3. Update tasks.md: `- [ ]` → `- [x]`
4. Confirm completion to user
5. STOP and wait for next instruction

## Quick Lookup Tables

### EARS Format Quick Reference

| Pattern | Template | Example |
|---------|----------|---------|
| Event-Response | WHEN [event] THEN the system SHALL [response] | WHEN user taps login THEN the system SHALL validate credentials |
| Condition-Response | IF [condition] THEN the system SHALL [response] | IF network unavailable THEN the system SHALL show offline mode |
| State-Behavior | WHILE [state] the system SHALL [behavior] | WHILE downloading the system SHALL show progress indicator |

### Requirement Numbering Quick Reference

| Level | Format | Example | Usage |
|-------|--------|---------|-------|
| Story | X | Story 1, Story 2 | Major feature grouping |
| Criteria | X.Y | 1.1, 1.2, 2.1 | Specific testable criteria |

### Task Status Indicators

| Symbol | Meaning | When to Use |
|--------|---------|-------------|
| `- [ ]` | Pending | Task not started |
| `- [x]` | Complete | Task finished successfully |

### File Structure Reference

```
documentation/
├── architecture.md              # Global (from /spec:architecture)
├── features/
│   └── {feature-name}/
│       ├── context.md          # Business context
│       ├── research.md         # Technical research
│       ├── requirements.md     # User stories
│       ├── design.md          # Technical design
│       └── tasks.md           # Implementation tasks
```

### Validation Checklist

Before submitting any document:
- [ ] All requirements numbered X.Y
- [ ] All tasks reference requirements with `_Requirements: X.Y_`
- [ ] All code blocks have Purpose statements
- [ ] All tasks.md have Prerequisites sections
- [ ] All research.md have Executive Summaries
- [ ] All acceptance criteria use "the system SHALL"
- [ ] Cross-references use relative paths
- [ ] No TODO or placeholder text remains

## Summary

This guide consolidates all quality standards and format requirements. Always refer to this document when:
- Starting a new feature specification
- Reviewing document quality
- Training new agents
- Resolving format questions

Remember: Consistency and completeness are key to successful feature implementation.