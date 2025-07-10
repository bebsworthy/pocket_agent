/**
 * Pocket Agent - A remote coding agent mobile interface.
 * 
 * This package contains the main application code for the Pocket Agent mobile app,
 * which enables developers to remotely control Claude Code instances running on
 * development servers through their Android devices.
 * 
 * The application follows Clean Architecture principles with clear separation of concerns:
 * - presentation: UI layer with Jetpack Compose, ViewModels, and Navigation
 * - domain: Business logic layer with Use Cases, Repository interfaces, and Models
 * - data: Data layer with Storage, Remote APIs, and Security implementations
 * - di: Dependency injection configuration with Hilt
 * 
 * Key features include:
 * - Multi-server Claude Code session management
 * - Real-time chat interface with Claude
 * - Secure SSH key storage with biometric authentication
 * - Background monitoring with intelligent notifications
 * - File browsing and git status monitoring
 * - Quick action automation with project script integration
 * 
 * @author Pocket Agent Team
 * @version 1.0.0
 * @since 2024-01-01
 */
package com.pocketagent;