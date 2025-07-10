/**
 * Domain layer for the Pocket Agent mobile application.
 * 
 * This package contains the business logic and core entities of the application.
 * It is independent of Android framework dependencies and follows Clean Architecture
 * principles to ensure testability and maintainability.
 * 
 * Structure:
 * - usecases: Business logic operations organized by feature
 *   - auth: Authentication and security use cases
 *   - projects: Project management use cases
 *   - communication: Claude Code communication use cases
 *   - monitoring: Background monitoring use cases
 * - repositories: Interfaces defining data access contracts
 * - models: Core business entities and data transfer objects
 *   - entities: Core domain entities (SshIdentity, ServerProfile, Project, Message)
 *   - responses: API response models
 *   - requests: API request models
 * 
 * Key principles:
 * - Framework independence: No Android dependencies
 * - Testability: Pure Kotlin/Java with easy mocking
 * - Single Responsibility: Each use case has one clear purpose
 * - Dependency Inversion: Depends on abstractions, not implementations
 * 
 * @author Pocket Agent Team
 * @version 1.0.0
 */
package com.pocketagent.domain;