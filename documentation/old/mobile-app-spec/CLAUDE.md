# Mobile App Specification Index

This document provides a comprehensive table of contents for all mobile app documentation.

## Table of Contents

### Core Mobile App Documentation

- **[frontend.spec.md](./frontend.spec.md)** - The technical specification for the mobile app frontend
  - Architecture and technology stack
  - Directory structure and coding standards
  - Feature specifications and global concepts
  - Deployment and build process

- **[component-map.specs.md](./component-map.specs.md)** - Component mapping and architecture specifications
  - System component relationships and dependencies
  - Mobile app architectural overview

### Feature Documentation

- **[data-layer-entity-management.feat.md](./data-layer-entity-management.feat.md)** - Data layer entity management feature
  - Technical specification for data layer implementation
  - Entity management architecture and components

- **[communication-layer/](./communication-layer/)** - Communication layer feature (split into multiple files)
  - **[communication-layer-index.md](./communication-layer/communication-layer-index.md)** - Index and navigation for communication layer docs
  - **[communication-layer-overview.feat.md](./communication-layer/communication-layer-overview.feat.md)** - Communication layer overview
  - **[communication-layer-websocket.feat.md](./communication-layer/communication-layer-websocket.feat.md)** - WebSocket implementation
  - **[communication-layer-authentication.feat.md](./communication-layer/communication-layer-authentication.feat.md)** - Authentication flow
  - **[communication-layer-messages.feat.md](./communication-layer/communication-layer-messages.feat.md)** - Message protocol and types
  - **[communication-layer-testing.feat.md](./communication-layer/communication-layer-testing.feat.md)** - Testing requirements

- **[background-services/](./background-services/)** - Background services feature (split into multiple files)
  - **[background-services-index.md](./background-services/background-services-index.md)** - Index and navigation for background services docs
  - **[background-services-overview.feat.md](./background-services/background-services-overview.feat.md)** - Background services overview
  - **[background-services-foreground.feat.md](./background-services/background-services-foreground.feat.md)** - Android foreground service
  - **[background-services-notifications.feat.md](./background-services/background-services-notifications.feat.md)** - Notification system
  - **[background-services-monitoring.feat.md](./background-services/background-services-monitoring.feat.md)** - Resource monitoring
  - **[background-services-testing.feat.md](./background-services/background-services-testing.feat.md)** - Testing requirements

- **[ui-navigation-foundation/](./ui-navigation-foundation/)** - UI navigation foundation feature (split into multiple files)
  - **[ui-navigation-index.md](./ui-navigation-foundation/ui-navigation-index.md)** - Index and navigation for UI navigation docs
  - **[ui-navigation-overview.feat.md](./ui-navigation-foundation/ui-navigation-overview.feat.md)** - Navigation overview
  - **[ui-navigation-navigation.feat.md](./ui-navigation-foundation/ui-navigation-navigation.feat.md)** - Navigation framework
  - **[ui-navigation-theme.feat.md](./ui-navigation-foundation/ui-navigation-theme.feat.md)** - Theme system
  - **[ui-navigation-components.feat.md](./ui-navigation-foundation/ui-navigation-components.feat.md)** - Base components
  - **[ui-navigation-scaffolding.feat.md](./ui-navigation-foundation/ui-navigation-scaffolding.feat.md)** - Screen scaffolding
  - **[ui-navigation-state.feat.md](./ui-navigation-foundation/ui-navigation-state.feat.md)** - State management
  - **[ui-navigation-implementation.feat.md](./ui-navigation-foundation/ui-navigation-implementation.feat.md)** - Implementation guide
  - **[ui-navigation-testing.feat.md](./ui-navigation-foundation/ui-navigation-testing.feat.md)** - Testing requirements

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

### Legacy Documentation

- **[background-services.feat.md](./background-services.feat.md)** - Original background services specification (before split)
- **[ui-navigation-foundation.feat.md](./ui-navigation-foundation.feat.md)** - Original UI navigation specification (before split)

## Documentation File Patterns

- `*.spec.md`: Technical specification about a specific component (frontend, backend, external APIs)
- `*.feat.md`: Technical specification about a specific feature (usually under development or preparation)
- `*.info.md`: Information and reference files
- `*-index.md`: Index files for split feature documentation

## Usage Instructions

- **ALWAYS READ** `frontend.spec.md` before working on mobile app components
- **READ AS NEEDED** feature specification files (`*.feat.md`) when mentioned in current tasks
- **USE INDEX FILES** (`*-index.md`) to navigate split feature documentation