# Documentation Index

This document provides a comprehensive table of contents for all documentation available in the `/documentation` folder.

## Table of Contents

### Core Project Documentation

- **[project.spec.md](./project.spec.md)** - The overview of the project
  - Technical overview of the project
  - Project structure and folder organization
  - Project-specific rules and guidelines

- **[frontend.spec.md](./frontend.spec.md)** - The technical specification for the frontend
  - Architecture and technology stack
  - Directory structure and coding standards
  - Feature specifications and global concepts
  - Deployment and build process

- **[component-map.specs.md](./component-map.specs.md)** - Component mapping and architecture specifications
  - System component relationships and dependencies

### Feature Documentation

- **[data-layer-entity-management.feat.md](./data-layer-entity-management.feat.md)** - Data layer entity management feature
  - Technical specification for data layer implementation
  - Entity management architecture and components

- **[communication-layer/](./communication-layer/)** - Communication layer feature (split into multiple files)
  - **[communication-layer-index.md](./communication-layer/communication-layer-index.md)** - Index and navigation for communication layer docs
  - Inter-component communication specifications
  - WebSocket implementation, authentication, message protocol

- **[background-services/](./background-services/)** - Background services feature (split into multiple files)
  - **[background-services-index.md](./background-services/background-services-index.md)** - Index and navigation for background services docs
  - Android foreground service implementation
  - Notification system, monitoring, and optimization

- **[ui-navigation-foundation/](./ui-navigation-foundation/)** - UI navigation foundation feature (split into multiple files)
  - **[ui-navigation-index.md](./ui-navigation-foundation/ui-navigation-index.md)** - Index and navigation for UI navigation docs
  - Navigation framework, theme system, base components
  - Screen scaffolding and state management

- **[screen-design.feat.md](./screen-design.feat.md)** - Screen design and UI layout feature
  - UI/UX specifications and design patterns
  - Screen layouts and component designs

- **[security-authentication.feat.md](./security-authentication.feat.md)** - Security and authentication feature
  - Authentication mechanisms and security protocols
  - User authorization and access control

- **[voice-integration.feat.md](./voice-integration.feat.md)** - Voice integration feature ⚠️ **Future Release**
  - Voice command and speech recognition specifications (deferred)
  - Audio processing and voice interaction design (deferred)

### Information Files

- **[feature-list.info.md](./feature-list.info.md)** - List of features and their status
  - Comprehensive feature inventory
  - Feature categorization and implementation status

### SDK and Integration Documentation

- **[claude-code-sdk-messages.spec.md](./claude-code-sdk-messages.spec.md)** - Claude Code SDK message format
  - Actual message schema from Claude Code SDK
  - Shows unstructured text format, not progress data

- **[wrapper-service-translation.spec.md](./wrapper-service-translation.spec.md)** - Wrapper service translation requirements
  - How wrapper translates between Claude Code SDK and mobile app formats
  - Natural language parsing requirements for progress extraction

- **[RENAMING_NOTES.md](./RENAMING_NOTES.md)** - Service renaming documentation
  - Tracks all service and component name changes
  - Documents v1 simplifications

### Templates and Resources

- **[templates/](./templates/)** - Documentation templates and examples
  - **[README.md](./templates/README.md)** - Template documentation overview
  - **[feature-document-structure.template.md](./templates/feature-document-structure.template.md)** - Standard feature documentation template

### Mockups and Prototypes

- **[mockups/](./mockups/)** - Design mockups and prototypes
  - **[gemini-01/](./mockups/gemini-01/)** - Gemini prototype version 1
    - **[index.html](./mockups/gemini-01/index.html)** - Interactive prototype

## Documentation File Patterns

- `*.spec.md`: Technical specification about a specific component (frontend, backend, external APIs)
- `*.feat.md`: Technical specification about a specific feature (usually under development or preparation)
- `*.info.md`: Information and reference files
- `*.template.md`: Template files for creating new documentation
- `CLAUDE.md`: This index file

## Usage Instructions

- **ALWAYS READ** `project.spec.md` before starting any work
- **ALWAYS READ** `frontend.spec.md` before working on frontend components
- **READ AS NEEDED** feature specification files (`*.feat.md`) when mentioned in current tasks
- **READ AS NEEDED** other specification files (`*.spec.md`) when mentioned in current tasks
- **USE TEMPLATES** from the `templates/` folder when creating new documentation