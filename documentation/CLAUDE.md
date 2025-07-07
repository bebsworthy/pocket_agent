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

- **[communication-layer.feat.md](./communication-layer.feat.md)** - Communication layer feature
  - Inter-component communication specifications
  - API and messaging architecture

- **[background-services.feat.md](./background-services.feat.md)** - Background services feature
  - Service worker and background task specifications
  - Async processing and job management

- **[screen-design.feat.md](./screen-design.feat.md)** - Screen design and UI layout feature
  - UI/UX specifications and design patterns
  - Screen layouts and component designs

- **[security-authentication.feat.md](./security-authentication.feat.md)** - Security and authentication feature
  - Authentication mechanisms and security protocols
  - User authorization and access control

- **[ui-navigation-foundation.feat.md](./ui-navigation-foundation.feat.md)** - UI navigation foundation feature
  - Navigation structure and routing specifications
  - User interface navigation patterns

- **[voice-integration.feat.md](./voice-integration.feat.md)** - Voice integration feature
  - Voice command and speech recognition specifications
  - Audio processing and voice interaction design

### Information Files

- **[feature-list.info.md](./feature-list.info.md)** - List of features and their status
  - Comprehensive feature inventory
  - Feature categorization and implementation status

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