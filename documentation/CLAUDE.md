# Documentation Index

This document provides an index for documentation in the `/documentation` folder.

## Table of Contents

### Core Project Documentation

- **[project.spec.md](./project.spec.md)** - The overview of the project
  - Technical overview of the entire system (mobile app + server)
  - Project structure and folder organization
  - Project-specific rules and guidelines

### SDK and Integration Documentation

- **[claude-code-sdk-messages.spec.md](./claude-code-sdk-messages.spec.md)** - Claude Code SDK message format
  - Actual message schema from Claude Code SDK
  - Shows unstructured text format used by Claude
  - Server-side integration reference

### Subdirectories

- **[mobile-app-spec/](./mobile-app-spec/)** - Mobile application specifications
  - **[CLAUDE.md](./mobile-app-spec/CLAUDE.md)** - Index of all mobile app documentation
  - Contains all mobile app features, components, and technical specifications

### Templates and Resources

- **[templates/](./templates/)** - Documentation templates and examples
  - **[README.md](./templates/README.md)** - Template documentation overview
  - **[feature-document-structure.template.md](./templates/feature-document-structure.template.md)** - Standard feature documentation template

### Mockups and Prototypes

- **[mockups/](./mockups/)** - Design mockups and prototypes
  - **[gemini-01/](./mockups/gemini-01/)** - Gemini prototype version 1
    - **[index.html](./mockups/gemini-01/index.html)** - Interactive prototype

## Documentation File Patterns

- `*.spec.md`: Technical specification about a specific component or system
- `*.feat.md`: Technical specification about a specific feature
- `*.info.md`: Information and reference files
- `*.template.md`: Template files for creating new documentation
- `CLAUDE.md`: Index files for documentation folders

## Usage Instructions

- **ALWAYS READ** `project.spec.md` for overall project understanding
- **FOR MOBILE APP WORK** navigate to `mobile-app-spec/` and read its CLAUDE.md index
- **FOR SERVER WORK** refer to `claude-code-sdk-messages.spec.md` for integration details
- **USE TEMPLATES** from the `templates/` folder when creating new documentation