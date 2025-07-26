package com.pocketagent.mobile.domain.usecase.server

import com.pocketagent.mobile.domain.repository.ServerProfileRepository
import kotlinx.coroutines.CoroutineDispatcher

class GetServerProfilesUseCase(
    private val repository: ServerProfileRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    // Implementation will be added in Task 2.2: Implement Server Profile CRUD Operations
}

class CreateServerProfileUseCase(
    private val repository: ServerProfileRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    // Implementation will be added in Task 2.2: Implement Server Profile CRUD Operations
}

class UpdateServerProfileUseCase(
    private val repository: ServerProfileRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    // Implementation will be added in Task 2.2: Implement Server Profile CRUD Operations
}

class DeleteServerProfileUseCase(
    private val repository: ServerProfileRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    // Implementation will be added in Task 2.2: Implement Server Profile CRUD Operations
}
