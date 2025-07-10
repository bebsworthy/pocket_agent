package com.pocketagent.mobile.domain.usecase.message

import com.pocketagent.mobile.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineDispatcher

class GetMessagesUseCase(
    private val repository: MessageRepository,
    private val dispatcher: CoroutineDispatcher
) {
    // Implementation will be added in Task 2.4: Implement Message Operations
}

class SendMessageUseCase(
    private val repository: MessageRepository,
    private val dispatcher: CoroutineDispatcher
) {
    // Implementation will be added in Task 2.4: Implement Message Operations
}