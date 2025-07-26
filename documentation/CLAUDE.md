# Pocket Agent Documentation Guide

## Overview

This guide provides navigation for the Pocket Agent documentation. The project is an Android mobile app that serves as a secure interface between users and Claude Code running on their development machines.

## Documentation Structure

```
documentation/
├── CLAUDE.md                    # This navigation guide
├── architecture.md              # System architecture overview
├── project.spec.md             # Project structure and guidelines
├── claude-code-sdk-messages.doc.md  # Claude Code SDK message format
├── design-summary.md           # Design overview
├── features/                   # Feature specifications (6 features)
│   ├── README.md              # Cross-reference mapping
│   ├── data-layer/           # Data persistence feature
│   ├── communication-layer/  # WebSocket communication
│   ├── background-services/  # Android services
│   ├── security-authentication/  # Auth features
│   ├── ui-navigation-foundation/  # Navigation
│   └── screen-design/        # UI screens
├── mockups/                   # UI prototypes
│   ├── claude-01/           # Claude prototype
│   ├── gemini-01/           # Gemini prototype v1
│   ├── gemini-02/           # Gemini prototype v2
│   └── inspiration/         # Design inspiration
└── old/                      # Legacy documentation
    └── mobile-app-spec/     # Previous documentation format
```

## Feature Documentation

Each feature in `/documentation/features/` contains exactly 5 files following the spec workflow:

1. **context.md** - Business context and user needs
2. **research.md** - Technical analysis and decisions
3. **requirements.md** - User stories with numbered acceptance criteria
4. **design.md** - Technical design and architecture
5. **tasks.md** - Implementation plan

### Available Features

- **data-layer/** - Data persistence and entity management
- **communication-layer/** - WebSocket communication
- **background-services/** - Android foreground services
- **security-authentication/** - Biometric and SSH authentication
- **ui-navigation-foundation/** - Navigation architecture
- **screen-design/** - Screen layouts and UI

## Key Documents

### System Overview
- **architecture.md** - Complete system architecture
- **project.spec.md** - Project structure and rules
- **design-summary.md** - High-level design overview

### Feature Mapping
- **features/README.md** - Cross-reference mapping showing:
  - Feature dependencies
  - Requirement numbering (282 total requirements)
  - Shared components
  - Integration points

### SDK Integration
- **claude-code-sdk-messages.doc.md** - Claude Code SDK message format for server integration

### UI/UX Resources
- **mockups/** - Interactive prototypes and design files
- **mockups/mockup-prompt.md** - Mockup generation instructions

## Requirement Format

All requirements use **X.Y numbering** where:
- X = Story number
- Y = Acceptance criteria number

Example: `2.3` = Story 2, Acceptance Criteria 3

## Spec Workflow Templates

The project uses spec workflow templates located in `/.spec/templates/`:

- **architecture-template.md** - System architecture template
- **design-template.md** - Technical design template
- **requirements-template.md** - Requirements template
- **research-template.md** - Research template
- **tasks-template.md** - Implementation tasks template

See `/.spec/WORKFLOW.md` for complete workflow documentation.

## Migration History

The documentation has been migrated from the legacy format (in `/old/mobile-app-spec/`) to the current spec workflow format. Related migration documents are located in `/spec-migration/`:

- `spec-migration-implementation-roadmap.md`
- `quality-review-report.md`
- `quality-improvements-needed.md`
- `quality-improvements-validation-report.md`

## Navigation Tips

1. **Start with** `architecture.md` for system understanding
2. **Check** `features/README.md` for feature dependencies
3. **Review** individual feature folders for detailed specifications
4. **Reference** `project.spec.md` for coding guidelines

## Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Database**: Room
- **DI**: Hilt
- **Navigation**: Compose Navigation
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)