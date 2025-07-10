package com.pocketagent.mobile.domain.usecase.ssh

import com.pocketagent.mobile.domain.repository.SshIdentityRepository
import kotlinx.coroutines.CoroutineDispatcher

class GetSshIdentitiesUseCase(
    private val repository: SshIdentityRepository,
    private val dispatcher: CoroutineDispatcher
) {
    // Implementation will be added in Task 2.1: Implement SSH Identity CRUD Operations
}

class CreateSshIdentityUseCase(
    private val repository: SshIdentityRepository,
    private val dispatcher: CoroutineDispatcher
) {
    // Implementation will be added in Task 2.1: Implement SSH Identity CRUD Operations
}

class DeleteSshIdentityUseCase(
    private val repository: SshIdentityRepository,
    private val dispatcher: CoroutineDispatcher
) {
    // Implementation will be added in Task 2.1: Implement SSH Identity CRUD Operations
}

class ValidateSshKeyUseCase(private val dispatcher: CoroutineDispatcher) {
    // Implementation will be added in Task 2.3: Implement SSH Key Validation
}