package com.pocketagent.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketagent.domain.models.Result
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Base ViewModel class providing common functionality for all ViewModels.
 * 
 * This class handles loading states, error handling, and provides utility methods
 * for managing UI state in a consistent manner across the application.
 * 
 * @param S The type of the UI state
 */
abstract class BaseViewModel<S : UiState> : ViewModel() {
    
    private val _uiState = MutableStateFlow(getInitialState())
    val uiState: StateFlow<S> = _uiState.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Coroutine exception handler for handling uncaught exceptions.
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        handleError(exception)
    }
    
    /**
     * Enhanced viewModelScope with exception handling.
     */
    protected val safeViewModelScope = viewModelScope + exceptionHandler
    
    /**
     * Returns the initial state for the ViewModel.
     */
    protected abstract fun getInitialState(): S
    
    /**
     * Updates the UI state.
     * 
     * @param newState The new state to set
     */
    protected fun updateState(newState: S) {
        _uiState.value = newState
    }
    
    /**
     * Updates the UI state using a transformation function.
     * 
     * @param transform Function to transform the current state
     */
    protected fun updateState(transform: (S) -> S) {
        _uiState.value = transform(_uiState.value)
    }
    
    /**
     * Sets the loading state.
     * 
     * @param loading Whether the ViewModel is in loading state
     */
    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
    
    /**
     * Sets an error message.
     * 
     * @param error The error message to set
     */
    protected fun setError(error: String?) {
        _error.value = error
    }
    
    /**
     * Handles exceptions and updates the error state.
     * 
     * @param exception The exception to handle
     */
    protected fun handleError(exception: Throwable) {
        setLoading(false)
        setError(exception.message ?: "An unknown error occurred")
    }
    
    /**
     * Executes a suspending function with loading state management.
     * 
     * @param block The suspending function to execute
     */
    protected fun launchWithLoading(block: suspend () -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            setLoading(true)
            setError(null)
            try {
                block()
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Executes a suspending function that returns a Result and handles the result.
     * 
     * @param block The suspending function that returns a Result
     * @param onSuccess Called when the result is successful
     * @param onError Called when the result is an error
     */
    protected fun <T> launchWithResult(
        block: suspend () -> Result<T>,
        onSuccess: (T) -> Unit = {},
        onError: (String) -> Unit = { setError(it) }
    ) {
        viewModelScope.launch(exceptionHandler) {
            setLoading(true)
            setError(null)
            try {
                when (val result = block()) {
                    is Result.Success -> onSuccess(result.data)
                    is Result.Error -> onError(result.message ?: result.exception.message ?: "Unknown error")
                    is Result.Loading -> { /* Already handled by setLoading */ }
                }
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Clears the current error state.
     */
    fun clearError() {
        setError(null)
    }
}

/**
 * Base interface for UI state classes.
 * 
 * All UI state classes should implement this interface to ensure
 * consistency across the application.
 */
interface UiState