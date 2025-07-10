package com.pocketagent.mobile.data.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder security classes for the data layer.
 * These will be properly implemented in later tasks.
 */

interface KeystoreManager {
    // Interface will be defined in Task 1.1: Setup Android Keystore Integration
}

@Singleton
class KeystoreManagerImpl @Inject constructor() : KeystoreManager {
    // Implementation will be added in Task 1.1: Setup Android Keystore Integration
}

interface EncryptionService {
    // Interface will be defined in Task 1.3: Create Encryption Service
}

@Singleton
class EncryptionServiceImpl @Inject constructor() : EncryptionService {
    // Implementation will be added in Task 1.3: Create Encryption Service
}

interface BiometricAuthenticationManager {
    // Interface will be defined in Task 1.2: Implement Biometric Authentication Manager
}

@Singleton
class BiometricAuthenticationManagerImpl @Inject constructor() : BiometricAuthenticationManager {
    // Implementation will be added in Task 1.2: Implement Biometric Authentication Manager
}

interface SshKeyManager {
    // Interface will be defined in Task 2.1: Implement SSH Key Import Manager
}

@Singleton
class SshKeyManagerImpl @Inject constructor() : SshKeyManager {
    // Implementation will be added in Task 2.1: Implement SSH Key Import Manager
}

interface SecurityManager {
    // Interface will be defined in Task 1.5: Implement Security Manager
}

@Singleton
class SecurityManagerImpl @Inject constructor() : SecurityManager {
    // Implementation will be added in Task 1.5: Implement Security Manager
}