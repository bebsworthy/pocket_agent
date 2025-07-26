package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.repository.DataException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException

/**
 * Utility for handling service layer exceptions with specific mappings.
 * 
 * This provides a centralized way to convert low-level exceptions into
 * appropriate service-level exceptions with proper logging and error handling.
 */
object ServiceExceptionHandler {
    
    /**
     * Executes a service operation with comprehensive exception handling.
     * 
     * @param operation The operation to execute
     * @param operationName Descriptive name for logging
     * @param tag Logging tag
     * @return ServiceResult with success or appropriate failure
     */
    suspend fun <T> handleServiceOperation(
        operation: suspend () -> T,
        operationName: String,
        tag: String = "ServiceOperation"
    ): ServiceResult<T> =
        try {
            val result = operation()
            ServiceResult.Success(result)
        } catch (e: ServiceException) {
            Log.e(tag, "$operationName failed - service error", e)
            ServiceResult.Failure(e.message ?: "Service operation failed")
        } catch (e: DataException) {
            Log.e(tag, "$operationName failed - data error", e)
            ServiceResult.Failure("Data operation failed: ${e.message}")
        } catch (e: SecurityManagerException) {
            Log.e(tag, "$operationName failed - security error", e)
            ServiceResult.Failure("Security operation failed: ${e.message}")
        } catch (e: TimeoutCancellationException) {
            Log.w(tag, "$operationName timed out", e)
            ServiceResult.Failure("Operation timed out")
        } catch (e: UnknownHostException) {
            Log.w(tag, "$operationName failed - unknown host", e)
            ServiceResult.Failure("Network error: Unknown host ${e.message}")
        } catch (e: IOException) {
            Log.e(tag, "$operationName failed - IO error", e)
            ServiceResult.Failure("IO error: ${e.message}")
        } catch (e: GeneralSecurityException) {
            Log.e(tag, "$operationName failed - security error", e)
            ServiceResult.Failure("Security error: ${e.message}")
        } catch (e: BadPaddingException) {
            Log.e(tag, "$operationName failed - decryption error", e)
            ServiceResult.Failure("Decryption failed - invalid data or key")
        } catch (e: IllegalBlockSizeException) {
            Log.e(tag, "$operationName failed - encryption block size error", e)
            ServiceResult.Failure("Encryption failed - invalid block size")
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "$operationName failed - invalid arguments", e)
            ServiceResult.Failure("Invalid arguments: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(tag, "$operationName failed - invalid state", e)
            ServiceResult.Failure("Invalid state: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "$operationName failed - unexpected error", e)
            ServiceResult.Failure("Unexpected error: ${e.message}")
        }
    
    /**
     * Maps common exceptions to specific service exceptions.
     * 
     * @param exception The exception to map
     * @param context Additional context for the mapping
     * @return Mapped ServiceException
     */
    fun mapException(exception: Throwable, context: String = ""): ServiceException {
        if (exception is ServiceException) return exception
        
        return when {
            isSecurityException(exception) -> mapSecurityException(exception, context)
            isNetworkException(exception) -> mapNetworkException(exception, context)
            isStateException(exception) -> mapStateException(exception, context)
            else -> mapGenericException(exception, context)
        }
    }

    private fun isSecurityException(exception: Throwable): Boolean =
        exception is SecurityManagerException || exception is GeneralSecurityException

    private fun isNetworkException(exception: Throwable): Boolean =
        exception is UnknownHostException || exception is IOException

    private fun isStateException(exception: Throwable): Boolean =
        exception is IllegalArgumentException || exception is IllegalStateException || 
        exception is TimeoutCancellationException

    private fun mapSecurityException(exception: Throwable, context: String): ServiceException =
        ServiceException.CryptographyException(
            "Security operation failed${formatContext(context)}", 
            exception
        )

    private fun mapNetworkException(exception: Throwable, context: String): ServiceException =
        when (exception) {
            is UnknownHostException -> ServiceException.NetworkConfigurationException(
                "Network host not found${formatContext(context)}", exception
            )
            else -> ServiceException.ExternalDependencyException(
                "IO operation failed${formatContext(context)}", exception
            )
        }

    private fun mapStateException(exception: Throwable, context: String): ServiceException =
        when (exception) {
            is TimeoutCancellationException -> ServiceException.TimeoutException(
                "Operation timed out${formatContext(context)}", exception
            )
            is IllegalArgumentException -> ServiceException.ConfigurationException(
                "Invalid configuration${formatContext(context)}", exception
            )
            is IllegalStateException -> ServiceException.InvalidStateException(
                "Invalid service state${formatContext(context)}", exception
            )
            else -> ServiceException.ExternalDependencyException(
                "State error${formatContext(context)}", exception
            )
        }

    private fun mapGenericException(exception: Throwable, context: String): ServiceException =
        ServiceException.ExternalDependencyException(
            "Unexpected error${formatContext(context)}: ${exception.message}", 
            exception
        )

    private fun formatContext(context: String): String =
        if (context.isNotEmpty()) " in $context" else ""
    
    /**
     * Creates a service exception with proper context.
     * 
     * @param message Base error message
     * @param cause Original exception
     * @param operationType Type of operation that failed
     * @return Appropriate ServiceException subtype
     */
    fun createContextualException(
        message: String, 
        cause: Throwable?, 
        operationType: ServiceOperationType
    ): ServiceException =
        when (operationType) {
            ServiceOperationType.SSH_OPERATION -> ServiceException.SshOperationException(message, cause)
            ServiceOperationType.SERVER_CONNECTION -> ServiceException.ServerConnectionException(message, cause)
            ServiceOperationType.PROJECT_OPERATION -> ServiceException.ProjectOperationException(message, cause)
            ServiceOperationType.NETWORK_VALIDATION -> ServiceException.NetworkConfigurationException(message, cause)
            ServiceOperationType.AUTHENTICATION -> ServiceException.AuthenticationException(message, cause)
            ServiceOperationType.CRYPTOGRAPHY -> ServiceException.CryptographyException(message, cause)
            ServiceOperationType.PARSING -> ServiceException.ParseException(message, cause)
            ServiceOperationType.CONFIGURATION -> ServiceException.ConfigurationException(message, cause)
            ServiceOperationType.EXTERNAL_DEPENDENCY -> ServiceException.ExternalDependencyException(message, cause)
        }
    
    /**
     * Validates that an exception should be handled as a service exception.
     * 
     * @param exception The exception to validate
     * @return true if it should be handled by service layer
     */
    fun shouldHandleAsServiceException(exception: Throwable): Boolean =
        when (exception) {
            is ServiceException,
            is DataException,
            is SecurityManagerException,
            is TimeoutCancellationException,
            is UnknownHostException,
            is IOException,
            is GeneralSecurityException,
            is IllegalArgumentException,
            is IllegalStateException -> true
            else -> false
        }
}

/**
 * Enum defining different types of service operations for contextual exception handling.
 */
enum class ServiceOperationType {
    SSH_OPERATION,
    SERVER_CONNECTION,
    PROJECT_OPERATION,
    NETWORK_VALIDATION,
    AUTHENTICATION,
    CRYPTOGRAPHY,
    PARSING,
    CONFIGURATION,
    EXTERNAL_DEPENDENCY
}

/**
 * Extension function to simplify service operation handling.
 */
suspend fun <T> executeServiceOperation(
    operationName: String,
    tag: String = "ServiceOperation",
    operation: suspend () -> T
): ServiceResult<T> = ServiceExceptionHandler.handleServiceOperation(operation, operationName, tag)