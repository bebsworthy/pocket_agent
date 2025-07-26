package com.pocketagent.mobile.data.repository

import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import com.pocketagent.mobile.domain.repository.DataRepository
import com.pocketagent.mobile.domain.repository.MessageRepository
import com.pocketagent.mobile.domain.repository.ProjectRepository
import com.pocketagent.mobile.domain.repository.ServerProfileRepository
import com.pocketagent.mobile.domain.repository.SshIdentityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder implementations for repository interfaces.
 * These will be properly implemented in later tasks.
 */

@Singleton
class DataRepositoryImpl
    @Inject
    constructor(
        private val storage: EncryptedJsonStorage,
    ) : DataRepository {
        // Implementation will be added in Task 1.3: Create SecureDataRepository
    }

@Singleton
class SshIdentityRepositoryImpl
    @Inject
    constructor(
        private val storage: EncryptedJsonStorage,
    ) : SshIdentityRepository {
        // Implementation will be added in Task 2.1: Implement SSH Identity CRUD Operations
    }

@Singleton
class ServerProfileRepositoryImpl
    @Inject
    constructor(
        private val storage: EncryptedJsonStorage,
    ) : ServerProfileRepository {
        // Implementation will be added in Task 2.2: Implement Server Profile CRUD Operations
    }

@Singleton
class ProjectRepositoryImpl
    @Inject
    constructor(
        private val storage: EncryptedJsonStorage,
    ) : ProjectRepository {
        // Implementation will be added in Task 2.3: Implement Project CRUD Operations
    }

@Singleton
class MessageRepositoryImpl
    @Inject
    constructor(
        private val storage: EncryptedJsonStorage,
    ) : MessageRepository {
        // Implementation will be added in Task 2.4: Implement Message Operations
    }
