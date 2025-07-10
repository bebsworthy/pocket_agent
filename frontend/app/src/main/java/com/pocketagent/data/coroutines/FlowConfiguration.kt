package com.pocketagent.data.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration and utilities for Flow-based reactive programming in Pocket Agent.
 * 
 * This class provides standardized configurations for StateFlow, SharedFlow, and Channels
 * to ensure consistent behavior across the application.
 */
object FlowConfiguration {
    
    /**
     * Default replay count for SharedFlow instances.
     * Allows new subscribers to receive the last N emissions.
     */
    const val DEFAULT_REPLAY = 1
    
    /**
     * Default buffer size for buffered flows.
     * Prevents backpressure issues in high-throughput scenarios.
     */
    const val DEFAULT_BUFFER_SIZE = 64
    
    /**
     * Default timeout for flow operations.
     */
    val DEFAULT_TIMEOUT: Duration = 30.seconds
    
    /**
     * Default retry attempts for flow operations.
     */
    const val DEFAULT_RETRY_ATTEMPTS = 3
    
    /**
     * Creates a configured MutableStateFlow with the given initial value.
     * 
     * @param initialValue The initial value for the StateFlow
     * @return A configured MutableStateFlow
     */
    fun <T> createStateFlow(initialValue: T): MutableStateFlow<T> {
        return MutableStateFlow(initialValue)
    }
    
    /**
     * Creates a configured MutableSharedFlow for events.
     * 
     * @param replay Number of values to replay to new subscribers
     * @param extraBufferCapacity Additional buffer capacity beyond replay
     * @return A configured MutableSharedFlow
     */
    fun <T> createSharedFlow(
        replay: Int = DEFAULT_REPLAY,
        extraBufferCapacity: Int = DEFAULT_BUFFER_SIZE
    ): MutableSharedFlow<T> {
        return MutableSharedFlow(
            replay = replay,
            extraBufferCapacity = extraBufferCapacity
        )
    }
    
    /**
     * Creates a configured Channel for message passing.
     * 
     * @param capacity The capacity of the channel
     * @return A configured Channel
     */
    fun <T> createChannel(capacity: Int = DEFAULT_BUFFER_SIZE): Channel<T> {
        return Channel(capacity)
    }
    
    /**
     * Creates a hot StateFlow that survives configuration changes.
     * 
     * @param scope The coroutine scope for the StateFlow
     * @param initialValue The initial value
     * @return A hot StateFlow
     */
    fun <T> Flow<T>.asHotStateFlow(
        scope: CoroutineScope,
        initialValue: T
    ): StateFlow<T> {
        return this.stateIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = initialValue
        )
    }
    
    /**
     * Creates a hot SharedFlow that survives configuration changes.
     * 
     * @param scope The coroutine scope for the SharedFlow
     * @param replay Number of values to replay to new subscribers
     * @return A hot SharedFlow
     */
    fun <T> Flow<T>.asHotSharedFlow(
        scope: CoroutineScope,
        replay: Int = DEFAULT_REPLAY
    ): SharedFlow<T> {
        return this.shareIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            replay = replay
        )
    }
    
    /**
     * Applies standard error handling to a flow.
     * 
     * @param onError Callback for handling errors
     * @return Flow with error handling applied
     */
    fun <T> Flow<T>.withErrorHandling(
        onError: (Throwable) -> Unit = { it.printStackTrace() }
    ): Flow<T> {
        return this.catch { throwable ->
            onError(throwable)
            throw throwable
        }
    }
    
    /**
     * Applies standard retry logic to a flow.
     * 
     * @param retries Number of retry attempts
     * @param predicate Predicate to determine if retry should occur
     * @return Flow with retry logic applied
     */
    fun <T> Flow<T>.withRetry(
        retries: Long = DEFAULT_RETRY_ATTEMPTS.toLong(),
        predicate: (Throwable) -> Boolean = { true }
    ): Flow<T> {
        return this.retry(retries) { throwable ->
            predicate(throwable)
        }
    }
    
    /**
     * Applies standard performance optimizations to a flow.
     * 
     * @param dispatcher The dispatcher to use for flow operations
     * @return Optimized flow
     */
    fun <T> Flow<T>.withPerformanceOptimizations(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): Flow<T> {
        return this
            .distinctUntilChanged()
            .buffer(DEFAULT_BUFFER_SIZE)
            .flowOn(dispatcher)
    }
    
    /**
     * Applies conflation to prevent backpressure in high-frequency updates.
     * 
     * @return Conflated flow
     */
    fun <T> Flow<T>.withConflation(): Flow<T> {
        return this.conflate()
    }
    
    /**
     * Adds a loading state to the beginning of a flow.
     * 
     * @param loadingValue The value to emit while loading
     * @return Flow with loading state
     */
    fun <T> Flow<T>.withLoadingState(loadingValue: T): Flow<T> {
        return this.onStart { emit(loadingValue) }
    }
}

/**
 * Wrapper class for managing StateFlow instances with common patterns.
 */
class StateFlowManager<T>(initialValue: T) {
    private val _state = MutableStateFlow(initialValue)
    val state: StateFlow<T> = _state.asStateFlow()
    
    /**
     * Updates the state value.
     */
    fun updateState(value: T) {
        _state.value = value
    }
    
    /**
     * Updates the state using a transform function.
     */
    fun updateState(transform: (T) -> T) {
        _state.value = transform(_state.value)
    }
    
    /**
     * Gets the current state value.
     */
    fun getCurrentState(): T = _state.value
    
    /**
     * Resets the state to a new value.
     */
    fun resetState(newValue: T) {
        _state.value = newValue
    }
}

/**
 * Wrapper class for managing SharedFlow instances with common patterns.
 */
class SharedFlowManager<T>(
    replay: Int = FlowConfiguration.DEFAULT_REPLAY,
    extraBufferCapacity: Int = FlowConfiguration.DEFAULT_BUFFER_SIZE
) {
    private val _events = MutableSharedFlow<T>(
        replay = replay,
        extraBufferCapacity = extraBufferCapacity
    )
    val events: SharedFlow<T> = _events.asSharedFlow()
    
    /**
     * Emits a new event.
     */
    suspend fun emitEvent(event: T) {
        _events.emit(event)
    }
    
    /**
     * Tries to emit an event without suspending.
     */
    fun tryEmitEvent(event: T): Boolean {
        return _events.tryEmit(event)
    }
    
    /**
     * Gets the number of subscribers.
     */
    val subscriptionCount: StateFlow<Int> = _events.subscriptionCount
    
    /**
     * Resets the replay cache.
     */
    fun resetReplayCache() {
        _events.resetReplayCache()
    }
}