/**
 * Dependency injection configuration for the Pocket Agent mobile application.
 * 
 * This package contains the Dagger Hilt configuration for dependency injection
 * throughout the application. It provides centralized dependency management
 * and ensures proper scoping of objects.
 * 
 * Structure:
 * - modules: Hilt modules providing dependencies
 *   - DatabaseModule: Database and storage dependencies
 *   - NetworkModule: Network client dependencies
 *   - RepositoryModule: Repository implementation bindings
 *   - SecurityModule: Security service dependencies
 * - qualifiers: Custom qualifiers for distinguishing similar dependencies
 * 
 * Key features:
 * - Singleton scoping for shared services
 * - Proper lifecycle management
 * - Easy testing with mock implementations
 * - Type-safe dependency injection
 * - Compile-time dependency verification
 * 
 * @author Pocket Agent Team
 * @version 1.0.0
 */
package com.pocketagent.di;