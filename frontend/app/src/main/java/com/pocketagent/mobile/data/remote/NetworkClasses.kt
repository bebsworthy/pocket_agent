package com.pocketagent.mobile.data.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder network classes for the data layer.
 * These will be properly implemented in later tasks.
 */

interface WebSocketClient {
    // Interface will be defined in Task 1.1: Setup OkHttp3 WebSocket Client
}

@Singleton
class WebSocketClientImpl @Inject constructor() : WebSocketClient {
    // Implementation will be added in Task 1.1: Setup OkHttp3 WebSocket Client
}

interface WebSocketConnectionManager {
    // Interface will be defined in Task 1.2: Implement Connection State Management
}

@Singleton
class WebSocketConnectionManagerImpl @Inject constructor() : WebSocketConnectionManager {
    // Implementation will be added in Task 1.2: Implement Connection State Management
}

interface MessageHandler {
    // Interface will be defined in Task 1.3: Create Message Protocol Framework
}

@Singleton
class MessageHandlerImpl @Inject constructor() : MessageHandler {
    // Implementation will be added in Task 1.3: Create Message Protocol Framework
}

interface AuthenticationHandler {
    // Interface will be defined in Task 2.1: Implement SSH Key Authentication
}

@Singleton
class AuthenticationHandlerImpl @Inject constructor() : AuthenticationHandler {
    // Implementation will be added in Task 2.1: Implement SSH Key Authentication
}

interface ConnectionHealthMonitor {
    // Interface will be defined in Task 1.4: Add Connection Health Monitoring
}

@Singleton
class ConnectionHealthMonitorImpl @Inject constructor() : ConnectionHealthMonitor {
    // Implementation will be added in Task 1.4: Add Connection Health Monitoring
}