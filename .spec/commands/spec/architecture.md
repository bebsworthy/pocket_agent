# Architecture Analysis Command

Perform comprehensive codebase analysis to create or update architecture documentation.

## Usage
```
/spec:architecture [--new]                    # Global architecture
/spec:architecture --module <module> [--new]  # Module-specific architecture
```

## Options
- `--new`: Create new architecture.md, replacing any existing one (default: update existing)
- `--module <module>`: Create/update module-specific architecture

### Available Modules
- `server`: Backend server and API services
- `frontend-react`: React web application frontend
- `frontend-android`: Android mobile application

## Instructions

You are performing comprehensive analysis of the codebase to document its architecture. This documentation will be reused across all feature specifications to avoid redundant analysis.

### Step 1: Determine Analysis Scope

**Parse the command arguments:**
- If `--module <module>` is provided: Analyze **module-specific architecture**
- If no `--module` flag: Analyze **global architecture**

### Step 2: Check Existing Documentation

#### For Global Architecture
- Check if `documentation/architecture.md` exists
- Target file: `documentation/architecture.md`

#### For Module-Specific Architecture
- Check if `documentation/modules/{module}/architecture.md` exists
- Ensure `documentation/modules/{module}/` directory exists (create if needed)
- Target file: `documentation/modules/{module}/architecture.md`

**Update vs New Mode:**
- If exists and `--new` flag NOT provided: Update existing, preserve accurate information
- If doesn't exist OR `--new` flag provided: Complete analysis from scratch

### Step 3: Analysis Process

#### For Global Architecture
Focus on system-wide concerns:

1. **System Overview Discovery**
   - Identify architecture style by looking for:
     - Multiple service directories (services/, apps/, packages/)
     - Docker/docker-compose files indicating multi-container setup
     - Multiple build configuration files in subdirectories
     - Service mesh configurations (Istio, Linkerd)
     - API gateway configurations
   - Create high-level component inventory

2. **Module/Service Discovery**
   - For monolithic applications:
     - Treat as single module
     - Document complete tech stack in one section
   - For multi-module applications:
     - Scan each service/module directory separately
     - Look for build files in each: package.json, go.mod, pom.xml, etc.
     - Identify different tech stacks per module
     - Map module boundaries and responsibilities

4. **Per-Module Analysis**
   For each discovered module/service:
   - **Technology Stack**: Language, framework, build tools specific to this module
   - **Dependencies**: Module-specific dependencies
   - **Entry Points**: Main files for this module
   - **Structure**: Directory organization within the module
   - **Testing**: Test framework and location for this module
   - **APIs**: What this module exposes

5. **Inter-Service Communication Discovery**
   - Look for API definitions (OpenAPI, Proto files, GraphQL schemas)
   - Identify HTTP clients and service calls
   - Find message queue configurations
   - Detect service discovery patterns
   - Map data flow between services

6. **Shared Infrastructure Analysis**
   - Identify shared databases (which services use which DBs)
   - Find shared caching layers
   - Detect authentication/authorization services
   - Map API gateways or load balancers
   - Identify message brokers or event buses

7. **Data Architecture**
   - Map data storage systems and which services use them
   - Document data flow between services
   - Identify transaction boundaries
   - Find data consistency strategies

8. **Development & Testing**
   - Document how to run the full system locally
   - Find orchestration tools (docker-compose, k8s manifests)
   - Identify different test strategies:
     - Unit tests per service
     - Integration tests across services
     - End-to-end test suites
   - Map CI/CD pipelines for multi-service builds

9. **Deployment Architecture**
   - Identify deployment strategies per service
   - Find infrastructure as code
   - Document service discovery mechanisms
   - Map environment configurations

10. **Security & Performance**
    - Document security boundaries between services
    - Identify service-to-service authentication
    - Find rate limiting and throttling
    - Map caching strategies
    - Identify monitoring and tracing setup

11. **Technical Debt & Issues**
    - Search for TODO/FIXME/HACK comments in each module
    - Identify deprecated services or components
    - Note inconsistencies between services
    - Document known integration issues

### Example Discovery Patterns

Common multi-module structures to look for:

```
# Microservices
services/
├── auth-service/     (Go + gRPC)
├── api-gateway/      (Node.js + Express)
├── user-service/     (Java + Spring Boot)
└── frontend/         (React + TypeScript)

# Monorepo with packages
packages/
├── web-app/          (Next.js)
├── mobile-app/       (React Native)
├── shared-ui/        (Component library)
└── api-server/       (NestJS)

# Backend + Frontend
backend/              (Python + FastAPI)
frontend/             (Vue.js)
mobile/              (Flutter)

# Modular monolith
src/
├── modules/
│   ├── auth/
│   ├── billing/
│   └── reporting/
└── shared/
```

#### For Module-Specific Architecture
Focus on single module deep-dive:

1. **Module Boundary Analysis**
   - Analyze only the specified module directory (e.g., `server/`, `frontend-react/`)
   - Document module-specific responsibilities and scope
   - Identify what this module owns vs. what it depends on

2. **Technology Stack Deep-Dive**
   - Language and framework specifics for this module
   - Build tools, dependency management
   - Testing frameworks and tools
   - Development and debugging tools

3. **Internal Structure Analysis**
   - Directory organization within the module
   - Code organization patterns (layers, features, etc.)
   - Key entry points and main components
   - Configuration management

4. **Module Dependencies**
   - External dependencies (packages, libraries)
   - Internal dependencies (other modules in the project)
   - Database or storage dependencies
   - Third-party service integrations

5. **Module APIs and Interfaces**
   - What this module exposes to other modules
   - How other modules interact with this one
   - API contracts, schemas, types
   - Event publishing/consuming

6. **Module-Specific Patterns**
   - Architectural patterns used within this module
   - Code organization conventions
   - Error handling strategies
   - Logging and monitoring approaches

### Output

#### For Global Architecture
1. Use the architecture template from `.spec/templates/architecture-template.md`
2. Adapt sections based on what you discover:
   - Single module: Simplify to one module section
   - Multi-module: Fill out all module sections
   - No inter-service communication: Skip that section
3. Save to `documentation/architecture.md`
4. Log the analysis date and scope at the end of the file
5. Report findings summary: "Global architecture analysis complete. Found [X] modules/services with [list technologies]. Documentation saved to documentation/architecture.md"

#### For Module-Specific Architecture
1. Use appropriate module-specific template or adapt the standard template
2. Focus only on the specified module
3. Save to `documentation/modules/{module}/architecture.md`
4. Log the analysis date and module scope at the end of the file
5. Report findings summary: "{Module} architecture analysis complete. Technology stack: [list technologies]. Documentation saved to documentation/modules/{module}/architecture.md"

### Key Principles

- **Discover, Don't Assume**: Make no assumptions about technology choices
- **Document Reality**: Record what IS, not what SHOULD BE
- **Module Awareness**: Recognize that different parts may use different technologies
- **Communication Focus**: Pay special attention to how components interact
- **Be Comprehensive**: Document both the parts and the whole
- **Stay Neutral**: Don't judge architectural decisions, just document them
- **Focus on Patterns**: Identify recurring patterns within and across modules

### Update Mode (Default)

When updating existing architecture.md:
1. Load current documentation
2. Focus on what has changed:
   - New dependencies
   - New patterns discovered
   - Structural changes
   - New technical debt
3. Update relevant sections while preserving accurate existing content
4. Add update log entry with date and changes

### New Mode

When `--new` flag is used:
1. Perform complete analysis from scratch
2. Replace any existing architecture.md
3. Useful when:
   - Major refactoring has occurred
   - Starting fresh analysis
   - Existing documentation is outdated

### Example Usage

```
/spec:architecture
```
Updates existing architecture documentation (or creates if none exists).

```
/spec:architecture --new
```
Creates fresh architecture documentation from scratch.

## Next Steps

After architecture analysis is complete, features can use `/spec:2_research` to perform lightweight, feature-specific research that references this architecture documentation.