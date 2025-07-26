package com.pocketagent.data.storage

import android.content.Context
import com.pocketagent.domain.models.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validator for data integrity and storage validation operations.
 *
 * This class provides validation functionality for encrypted storage data,
 * including integrity checks, format validation, and data consistency verification.
 */
@Singleton
class StorageValidator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val fileStorageManager: FileStorageManager,
        private val storageEncryption: StorageEncryption,
    ) {
        companion object {
            private const val MAX_JSON_DEPTH = 10
            private const val MAX_JSON_SIZE = 10 * 1024 * 1024 // 10 MB
            private const val MIN_JSON_SIZE = 2 // "{}"
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        /**
         * Enum representing validation severity levels.
         */
        enum class ValidationSeverity {
            INFO,
            WARNING,
            ERROR,
            CRITICAL,
        }

        /**
         * Data class representing a validation result.
         */
        data class ValidationResult(
            val isValid: Boolean,
            val severity: ValidationSeverity,
            val message: String,
            val details: String? = null,
        )

        /**
         * Data class representing comprehensive validation report.
         */
        data class ValidationReport(
            val isValid: Boolean,
            val results: List<ValidationResult>,
            val checkedFiles: Int,
            val totalSize: Long,
            val timestamp: Long = System.currentTimeMillis(),
        ) {
            val hasErrors: Boolean get() =
                results.any {
                    it.severity == ValidationSeverity.ERROR ||
                        it.severity == ValidationSeverity.CRITICAL
                }
            val hasWarnings: Boolean get() = results.any { it.severity == ValidationSeverity.WARNING }
            val criticalIssues: List<ValidationResult> get() = results.filter { it.severity == ValidationSeverity.CRITICAL }
            val errors: List<ValidationResult> get() = results.filter { it.severity == ValidationSeverity.ERROR }
            val warnings: List<ValidationResult> get() = results.filter { it.severity == ValidationSeverity.WARNING }
        }

        /**
         * Validates a JSON string for format and structure.
         *
         * @param jsonData The JSON data to validate
         * @return Validation result
         */
        suspend fun validateJsonData(jsonData: String): Result<ValidationResult> {
            return withContext(Dispatchers.Default) {
                try {
                    val sizeValidation = validateJsonSize(jsonData)
                    if (!sizeValidation.isValid) {
                        return@withContext Result.Success(sizeValidation)
                    }

                    val jsonElement = parseJsonSafely(jsonData)
                        ?: return@withContext Result.Success(createInvalidJsonResult())

                    val depthValidation = validateJsonDepth(jsonElement)
                    if (!depthValidation.isValid) {
                        return@withContext Result.Success(depthValidation)
                    }

                    val structureResult = validateJsonStructure(jsonElement)
                    if (!structureResult.isValid) {
                        return@withContext Result.Success(structureResult)
                    }

                    Result.Success(createValidJsonResult(jsonData, calculateJsonDepth(jsonElement)))
                } catch (e: Exception) {
                    Result.Error(e, "JSON validation failed: ${e.message}")
                }
            }
        }

        /**
         * Validates JSON data size limits.
         */
        private fun validateJsonSize(jsonData: String): ValidationResult {
            if (jsonData.length < MIN_JSON_SIZE) {
                return ValidationResult(
                    isValid = false,
                    severity = ValidationSeverity.ERROR,
                    message = "JSON data too small",
                    details = "JSON data must be at least $MIN_JSON_SIZE bytes",
                )
            }

            if (jsonData.length > MAX_JSON_SIZE) {
                return ValidationResult(
                    isValid = false,
                    severity = ValidationSeverity.ERROR,
                    message = "JSON data too large",
                    details = "JSON data exceeds maximum size of ${MAX_JSON_SIZE / 1024 / 1024} MB",
                )
            }

            return ValidationResult(
                isValid = true,
                severity = ValidationSeverity.INFO,
                message = "JSON size is valid",
                details = "Size: ${jsonData.length} bytes",
            )
        }

        /**
         * Parses JSON safely, returning null on failure.
         */
        private fun parseJsonSafely(jsonData: String): JsonElement? {
            return try {
                json.parseToJsonElement(jsonData)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Creates result for invalid JSON format.
         */
        private fun createInvalidJsonResult(): ValidationResult {
            return ValidationResult(
                isValid = false,
                severity = ValidationSeverity.ERROR,
                message = "Invalid JSON format",
                details = "JSON parsing failed",
            )
        }

        /**
         * Validates JSON depth limits.
         */
        private fun validateJsonDepth(jsonElement: JsonElement): ValidationResult {
            val depth = calculateJsonDepth(jsonElement)
            if (depth > MAX_JSON_DEPTH) {
                return ValidationResult(
                    isValid = false,
                    severity = ValidationSeverity.WARNING,
                    message = "JSON depth exceeds recommended limit",
                    details = "JSON depth is $depth, recommended maximum is $MAX_JSON_DEPTH",
                )
            }

            return ValidationResult(
                isValid = true,
                severity = ValidationSeverity.INFO,
                message = "JSON depth is valid",
                details = "Depth: $depth",
            )
        }

        /**
         * Creates result for valid JSON data.
         */
        private fun createValidJsonResult(
            jsonData: String,
            depth: Int,
        ): ValidationResult {
            return ValidationResult(
                isValid = true,
                severity = ValidationSeverity.INFO,
                message = "JSON data is valid",
                details = "Size: ${jsonData.length} bytes, Depth: $depth",
            )
        }

        /**
         * Validates encrypted data integrity.
         *
         * @param encryptedData The encrypted data to validate
         * @return Validation result
         */
        suspend fun validateEncryptedData(encryptedData: StorageEncryption.EncryptedData): Result<ValidationResult> {
            return withContext(Dispatchers.Default) {
                try {
                    // Validate IV size
                    if (encryptedData.iv.size != 12) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "Invalid IV size",
                                details = "IV size is ${encryptedData.iv.size}, expected 12 bytes",
                            ),
                        )
                    }

                    // Validate ciphertext size
                    if (encryptedData.ciphertext.isEmpty()) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "Empty ciphertext",
                                details = "Ciphertext cannot be empty",
                            ),
                        )
                    }

                    // Validate minimum ciphertext size (GCM tag + data)
                    if (encryptedData.ciphertext.size < 16) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "Ciphertext too small",
                                details = "Ciphertext must be at least 16 bytes (GCM tag size)",
                            ),
                        )
                    }

                    // Try to decrypt to verify integrity
                    val decryptResult = storageEncryption.decrypt(encryptedData)
                    if (!decryptResult.isSuccess) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "Decryption failed",
                                details = "Cannot decrypt data: ${(decryptResult as Result.Error).message}",
                            ),
                        )
                    }

                    Result.Success(
                        ValidationResult(
                            isValid = true,
                            severity = ValidationSeverity.INFO,
                            message = "Encrypted data is valid",
                            details = "IV: ${encryptedData.iv.size} bytes, Ciphertext: ${encryptedData.ciphertext.size} bytes",
                        ),
                    )
                } catch (e: Exception) {
                    Result.Error(e, "Encrypted data validation failed: ${e.message}")
                }
            }
        }

        /**
         * Validates a storage file.
         *
         * @param filename The file to validate
         * @return Validation result
         */
        suspend fun validateStorageFile(filename: String): Result<ValidationResult> {
            return withContext(Dispatchers.IO) {
                try {
                    // Check if file exists
                    val existsResult = fileStorageManager.fileExists(filename)
                    if (!existsResult.isSuccess || !existsResult.getOrThrow()) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "File not found",
                                details = "Storage file does not exist: $filename",
                            ),
                        )
                    }

                    // Read file data
                    val fileData = fileStorageManager.readFile(filename, verifyChecksum = true)
                    if (!fileData.isSuccess) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "Cannot read file",
                                details = "Failed to read file: ${(fileData as Result.Error).message}",
                            ),
                        )
                    }

                    // Validate file is not empty
                    val data = fileData.getOrThrow()
                    if (data.isEmpty()) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "Empty file",
                                details = "Storage file is empty: $filename",
                            ),
                        )
                    }

                    // Try to parse as encrypted data
                    val encryptedDataResult =
                        kotlin.runCatching {
                            StorageEncryption.EncryptedData.fromByteArray(data)
                        }

                    if (encryptedDataResult.isFailure) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "Invalid encrypted data format",
                                details = "Cannot parse encrypted data: ${encryptedDataResult.exceptionOrNull()?.message}",
                            ),
                        )
                    }

                    // Validate encrypted data
                    val encryptedData = encryptedDataResult.getOrThrow()
                    val encryptedValidation = validateEncryptedData(encryptedData)
                    if (!encryptedValidation.isSuccess) {
                        return@withContext encryptedValidation
                    }

                    val encryptedResult = encryptedValidation.getOrThrow()
                    if (!encryptedResult.isValid) {
                        return@withContext Result.Success(encryptedResult)
                    }

                    // Decrypt and validate JSON
                    val decryptResult = storageEncryption.decrypt(encryptedData)
                    if (!decryptResult.isSuccess) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.ERROR,
                                message = "Decryption failed",
                                details = "Cannot decrypt file data: ${(decryptResult as Result.Error).message}",
                            ),
                        )
                    }

                    val jsonData = decryptResult.getOrThrow()
                    val jsonValidation = validateJsonData(jsonData)
                    if (!jsonValidation.isSuccess) {
                        return@withContext jsonValidation
                    }

                    val jsonResult = jsonValidation.getOrThrow()
                    if (!jsonResult.isValid) {
                        return@withContext Result.Success(jsonResult)
                    }

                    Result.Success(
                        ValidationResult(
                            isValid = true,
                            severity = ValidationSeverity.INFO,
                            message = "Storage file is valid",
                            details = "File: $filename, Size: ${data.size} bytes",
                        ),
                    )
                } catch (e: Exception) {
                    Result.Error(e, "Storage file validation failed: ${e.message}")
                }
            }
        }

        /**
         * Performs comprehensive validation of all storage files.
         *
         * @return Validation report
         */
        suspend fun validateAllStorageFiles(): Result<ValidationReport> =
            withContext(Dispatchers.IO) {
                try {
                    val files = fileStorageManager.listFiles().getOrThrow()
                    val results = mutableListOf<ValidationResult>()
                    var totalSize = 0L

                    for (filename in files) {
                        val fileValidation = validateStorageFile(filename)
                        if (fileValidation.isSuccess) {
                            val result = fileValidation.getOrThrow()
                            results.add(result)

                            // Add file size to total
                            val metadata = fileStorageManager.getFileMetadata(filename)
                            if (metadata.isSuccess) {
                                totalSize += metadata.getOrThrow().size
                            }
                        } else {
                            results.add(
                                ValidationResult(
                                    isValid = false,
                                    severity = ValidationSeverity.ERROR,
                                    message = "Validation failed",
                                    details = "Cannot validate file $filename: ${(fileValidation as Result.Error).message}",
                                ),
                            )
                        }
                    }

                    val isValid = results.all { it.isValid }

                    Result.Success(
                        ValidationReport(
                            isValid = isValid,
                            results = results,
                            checkedFiles = files.size,
                            totalSize = totalSize,
                        ),
                    )
                } catch (e: Exception) {
                    Result.Error(e, "Comprehensive validation failed: ${e.message}")
                }
            }

        /**
         * Validates storage consistency across all files.
         *
         * @return Validation result
         */
        suspend fun validateStorageConsistency(): Result<ValidationResult> {
            return withContext(Dispatchers.IO) {
                try {
                    val files = fileStorageManager.listFiles().getOrThrow()
                    val duplicateChecksums = mutableMapOf<String, MutableList<String>>()

                    // Check for duplicate content
                    for (filename in files) {
                        val metadata = fileStorageManager.getFileMetadata(filename)
                        if (metadata.isSuccess) {
                            val checksum = metadata.getOrThrow().checksum
                            if (checksum.isNotEmpty()) {
                                duplicateChecksums.getOrPut(checksum) { mutableListOf() }.add(filename)
                            }
                        }
                    }

                    val duplicates = duplicateChecksums.filter { it.value.size > 1 }
                    if (duplicates.isNotEmpty()) {
                        val duplicateDetails =
                            duplicates
                                .map { (checksum, files) ->
                                    "Checksum $checksum: ${files.joinToString(", ")}"
                                }.joinToString("\n")

                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.WARNING,
                                message = "Duplicate content detected",
                                details = "Files with identical content:\n$duplicateDetails",
                            ),
                        )
                    }

                    // Check for orphaned files (files that don't follow naming convention)
                    val orphanedFiles =
                        files.filter { filename ->
                            !filename.matches(Regex("^[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+$"))
                        }

                    if (orphanedFiles.isNotEmpty()) {
                        return@withContext Result.Success(
                            ValidationResult(
                                isValid = false,
                                severity = ValidationSeverity.WARNING,
                                message = "Orphaned files detected",
                                details = "Files with invalid naming: ${orphanedFiles.joinToString(", ")}",
                            ),
                        )
                    }

                    Result.Success(
                        ValidationResult(
                            isValid = true,
                            severity = ValidationSeverity.INFO,
                            message = "Storage consistency is valid",
                            details = "Checked ${files.size} files, no inconsistencies found",
                        ),
                    )
                } catch (e: Exception) {
                    Result.Error(e, "Storage consistency validation failed: ${e.message}")
                }
            }
        }

        /**
         * Calculates the depth of a JSON element.
         *
         * @param element The JSON element
         * @return The depth of the element
         */
        private fun calculateJsonDepth(element: JsonElement): Int =
            when {
                element.toString().startsWith("{") -> {
                    val obj = element.jsonObject
                    if (obj.isEmpty()) 1 else 1 + (obj.values.maxOfOrNull { calculateJsonDepth(it) } ?: 0)
                }
                element.toString().startsWith("[") -> {
                    val array = element.toString()
                    if (array == "[]") {
                        1
                    } else {
                        1 + (
                            json.parseToJsonElement(array).let { arr ->
                                // This is a simplified approach - in a real implementation,
                                // you'd properly parse the array and calculate depth
                                0
                            }
                        )
                    }
                }
                else -> 1
            }

        /**
         * Validates JSON structure for common issues.
         *
         * @param element The JSON element to validate
         * @return Validation result
         */
        private fun validateJsonStructure(element: JsonElement): ValidationResult {
            return try {
                // Check for circular references (simplified check)
                val jsonString = element.toString()
                if (jsonString.contains("\"ref\":\"#/")) {
                    return ValidationResult(
                        isValid = false,
                        severity = ValidationSeverity.ERROR,
                        message = "Circular reference detected",
                        details = "JSON contains circular references",
                    )
                }

                // Check for extremely long strings
                if (jsonString.length > 1000000) { // 1MB
                    return ValidationResult(
                        isValid = false,
                        severity = ValidationSeverity.WARNING,
                        message = "Very large JSON string",
                        details = "JSON string is extremely large (${jsonString.length} characters)",
                    )
                }

                ValidationResult(
                    isValid = true,
                    severity = ValidationSeverity.INFO,
                    message = "JSON structure is valid",
                    details = "No structural issues found",
                )
            } catch (e: Exception) {
                ValidationResult(
                    isValid = false,
                    severity = ValidationSeverity.ERROR,
                    message = "JSON structure validation failed",
                    details = "Error: ${e.message}",
                )
            }
        }

        /**
         * Generates a validation report summary.
         *
         * @param report The validation report
         * @return Summary string
         */
        fun generateReportSummary(report: ValidationReport): String =
            buildString {
                appendLine("Storage Validation Report")
                appendLine("=" * 40)
                appendLine("Overall Status: ${if (report.isValid) "VALID" else "INVALID"}")
                appendLine("Files Checked: ${report.checkedFiles}")
                appendLine("Total Size: ${formatBytes(report.totalSize)}")
                appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(report.timestamp))}")
                appendLine()

                if (report.criticalIssues.isNotEmpty()) {
                    appendLine("Critical Issues (${report.criticalIssues.size}):")
                    report.criticalIssues.forEach { issue ->
                        appendLine("  - ${issue.message}")
                    }
                    appendLine()
                }

                if (report.errors.isNotEmpty()) {
                    appendLine("Errors (${report.errors.size}):")
                    report.errors.forEach { error ->
                        appendLine("  - ${error.message}")
                    }
                    appendLine()
                }

                if (report.warnings.isNotEmpty()) {
                    appendLine("Warnings (${report.warnings.size}):")
                    report.warnings.forEach { warning ->
                        appendLine("  - ${warning.message}")
                    }
                    appendLine()
                }

                if (report.isValid) {
                    appendLine("✓ All validations passed successfully")
                } else {
                    appendLine("✗ Validation failed - please review issues above")
                }
            }

        /**
         * Formats bytes to human-readable format.
         *
         * @param bytes The number of bytes
         * @return Formatted string
         */
        private fun formatBytes(bytes: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0

            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }

            return "%.2f %s".format(size, units[unitIndex])
        }

        /**
         * String repeat extension for Kotlin.
         */
        private operator fun String.times(count: Int): String = this.repeat(count)
    }
