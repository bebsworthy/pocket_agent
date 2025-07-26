package com.pocketagent.mobile.domain.usecase.project

import com.pocketagent.mobile.domain.repository.ProjectRepository
import kotlinx.coroutines.CoroutineDispatcher

class GetProjectsUseCase(
    private val repository: ProjectRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    // Implementation will be added in Task 2.3: Implement Project CRUD Operations
}

class CreateProjectUseCase(
    private val repository: ProjectRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    // Implementation will be added in Task 2.3: Implement Project CRUD Operations
}

class UpdateProjectUseCase(
    private val repository: ProjectRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    // Implementation will be added in Task 2.3: Implement Project CRUD Operations
}

class DeleteProjectUseCase(
    private val repository: ProjectRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    // Implementation will be added in Task 2.3: Implement Project CRUD Operations
}
