/**
 * Data layer for the Pocket Agent mobile application.
 * 
 * This package contains the implementation of data access and storage mechanisms.
 * It implements the repository interfaces defined in the domain layer and handles
 * data persistence, network communication, and security operations.
 * 
 * Structure:
 * - storage: Local data storage implementations
 *   - json: Encrypted JSON file storage for app data
 *   - cache: In-memory caching for performance optimization
 *   - preferences: SharedPreferences for user settings
 * - remote: Remote data access implementations
 *   - websocket: WebSocket client for real-time Claude Code communication
 *   - api: REST API clients for external services
 *   - dto: Data transfer objects for API communication
 * - security: Security and encryption implementations
 *   - keystore: Android Keystore integration for secure key storage
 *   - biometric: Biometric authentication implementation
 *   - encryption: AES encryption for data protection
 * 
 * Key features:
 * - Encrypted JSON storage for sensitive data
 * - Real-time WebSocket communication with Claude Code
 * - Hardware-backed security with Android Keystore
 * - Biometric authentication for secure access
 * - Offline capability with local caching
 * 
 * @author Pocket Agent Team
 * @version 1.0.0
 */
package com.pocketagent.data;