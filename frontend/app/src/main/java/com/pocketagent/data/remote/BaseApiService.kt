package com.pocketagent.data.remote

import com.pocketagent.domain.models.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import java.io.IOException

/**
 * Base class for API services.
 *
 * This abstract class provides common functionality for API operations
 * including error handling and response processing.
 */
abstract class BaseApiService {
    /**
     * Handles API calls and converts responses to Result.
     *
     * @param apiCall The API call to execute
     * @return Result wrapped API response
     */
    protected suspend fun <T> handleApiCall(apiCall: suspend () -> Response<T>): Result<T> =
        try {
            val response = apiCall()
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    Result.Success(body)
                } ?: Result.Error(Exception("Empty response body"))
            } else {
                Result.Error(
                    Exception("API call failed"),
                    "HTTP ${response.code()}: ${response.message()}",
                )
            }
        } catch (e: IOException) {
            Result.Error(e, "Network error: ${e.message}")
        } catch (e: Exception) {
            Result.Error(e, "API call failed: ${e.message}")
        }

    /**
     * Handles API calls and returns a Flow for reactive programming.
     *
     * @param apiCall The API call to execute
     * @return Flow emitting the API response
     */
    protected fun <T> handleApiCallAsFlow(apiCall: suspend () -> Response<T>): Flow<Result<T>> =
        flow {
            emit(Result.Loading)
            emit(handleApiCall(apiCall))
        }

    /**
     * Handles WebSocket connections and message processing.
     *
     * @param connectionCall The WebSocket connection call
     * @return Flow emitting WebSocket messages
     */
    protected fun <T> handleWebSocketConnection(connectionCall: suspend () -> Flow<T>): Flow<Result<T>> =
        flow {
            try {
                emit(Result.Loading)
                connectionCall().collect { message ->
                    emit(Result.Success(message))
                }
            } catch (e: Exception) {
                emit(Result.Error(e, "WebSocket connection failed: ${e.message}"))
            }
        }
}
