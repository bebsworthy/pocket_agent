package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.validation.ValidationError
import com.pocketagent.data.validation.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for validating network configurations and connectivity.
 *
 * This class provides comprehensive network validation capabilities including:
 * - Hostname and IP address validation
 * - Port availability and conflicts checking
 * - DNS resolution testing
 * - Network reachability testing
 * - Configuration recommendations
 * - Common network issue detection
 *
 * The validator helps ensure server profiles have valid and functional
 * network configurations before attempting connections.
 */
@Singleton
class NetworkConfigurationValidator
    @Inject
    constructor() {
        companion object {
            private const val TAG = "NetworkConfigValidator"
            private const val DNS_TIMEOUT_MS = 5000L
            private const val PORT_TEST_TIMEOUT_MS = 3000L

            // Common port ranges and their purposes
            private val SYSTEM_PORTS = 1..1023
            private val REGISTERED_PORTS = 1024..49151
            private val DYNAMIC_PORTS = 49152..65535

            // Well-known ports that might conflict
            private val WELL_KNOWN_PORTS =
                mapOf(
                    22 to "SSH",
                    23 to "Telnet",
                    25 to "SMTP",
                    53 to "DNS",
                    80 to "HTTP",
                    110 to "POP3",
                    143 to "IMAP",
                    443 to "HTTPS",
                    993 to "IMAPS",
                    995 to "POP3S",
                    3389 to "RDP",
                    5432 to "PostgreSQL",
                    3306 to "MySQL",
                    1521 to "Oracle",
                    8080 to "HTTP Alternate",
                    8443 to "HTTPS Alternate",
                )

            // Regex patterns for validation
            private val HOSTNAME_PATTERN =
                Pattern.compile(
                    "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)$",
                )

            private val IP_V4_PATTERN =
                Pattern.compile(
                    "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$",
                )

            private val IP_V6_PATTERN =
                Pattern.compile(
                    "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::$",
                )
        }

        /**
         * Validates a complete network configuration.
         */
        suspend fun validateConfiguration(
            hostname: String,
            sshPort: Int,
            wrapperPort: Int,
        ): ValidationResult {
            Log.d(TAG, "Validating network configuration: $hostname:$sshPort/$wrapperPort")

            val errors = mutableListOf<ValidationError>()

            // Validate hostname
            val hostnameValidation = validateHostname(hostname)
            if (hostnameValidation.isFailure()) {
                errors.addAll((hostnameValidation as ValidationResult.Failure).errors)
            }

            // Validate ports
            val sshPortValidation = validatePort(sshPort, "SSH")
            if (sshPortValidation.isFailure()) {
                errors.addAll((sshPortValidation as ValidationResult.Failure).errors)
            }

            val wrapperPortValidation = validatePort(wrapperPort, "wrapper")
            if (wrapperPortValidation.isFailure()) {
                errors.addAll((wrapperPortValidation as ValidationResult.Failure).errors)
            }

            // Check port conflicts
            if (sshPort == wrapperPort) {
                errors.add(
                    ValidationError.businessRuleError(
                        "SSH port and wrapper port cannot be the same ($sshPort)",
                        "ports",
                        "PORT_CONFLICT",
                    ),
                )
            }

            // Validate port assignments
            val portAssignmentValidation = validatePortAssignments(sshPort, wrapperPort)
            if (portAssignmentValidation.isFailure()) {
                errors.addAll((portAssignmentValidation as ValidationResult.Failure).errors)
            }

            return if (errors.isEmpty()) {
                ValidationResult.Success
            } else {
                ValidationResult.Failure(errors)
            }
        }

        /**
         * Validates hostname format and structure.
         */
        fun validateHostname(hostname: String): ValidationResult {
            if (hostname.isBlank()) {
                return ValidationResult.Failure(
                    ValidationError.fieldError("Hostname cannot be empty", "hostname"),
                )
            }

            if (hostname.length > 253) {
                return ValidationResult.Failure(
                    ValidationError.fieldError("Hostname too long (max 253 characters)", "hostname"),
                )
            }

            // Check if it's an IP address
            if (isValidIpAddress(hostname)) {
                return validateIpAddress(hostname)
            }

            // Validate as hostname
            if (!HOSTNAME_PATTERN.matcher(hostname).matches()) {
                return ValidationResult.Failure(
                    ValidationError.fieldError(
                        "Invalid hostname format. Use only letters, numbers, hyphens, and dots",
                        "hostname",
                    ),
                )
            }

            // Additional hostname checks
            if (hostname.startsWith("-") || hostname.endsWith("-")) {
                return ValidationResult.Failure(
                    ValidationError.fieldError("Hostname cannot start or end with hyphen", "hostname"),
                )
            }

            if (hostname.startsWith(".") || hostname.endsWith(".")) {
                return ValidationResult.Failure(
                    ValidationError.fieldError("Hostname cannot start or end with dot", "hostname"),
                )
            }

            if (hostname.contains("..")) {
                return ValidationResult.Failure(
                    ValidationError.fieldError("Hostname cannot contain consecutive dots", "hostname"),
                )
            }

            // Check for localhost variations
            if (hostname.equals("localhost", ignoreCase = true) ||
                hostname.equals("127.0.0.1") ||
                hostname.equals("::1")
            ) {
                Log.i(TAG, "Localhost hostname detected: $hostname")
            }

            return ValidationResult.Success
        }

        /**
         * Validates IP address format.
         */
        private fun validateIpAddress(ipAddress: String): ValidationResult {
            if (IP_V4_PATTERN.matcher(ipAddress).matches()) {
                return validateIpV4Address(ipAddress)
            }

            if (IP_V6_PATTERN.matcher(ipAddress).matches()) {
                return validateIpV6Address(ipAddress)
            }

            return ValidationResult.Failure(
                ValidationError.fieldError("Invalid IP address format", "hostname"),
            )
        }

        /**
         * Validates IPv4 address.
         */
        private fun validateIpV4Address(ipAddress: String): ValidationResult {
            val parts = ipAddress.split(".")

            if (parts.size != 4) {
                return ValidationResult.Failure(
                    ValidationError.fieldError("Invalid IPv4 address format", "hostname"),
                )
            }

            for (part in parts) {
                val value = part.toIntOrNull()
                if (value == null || value < 0 || value > 255) {
                    return ValidationResult.Failure(
                        ValidationError.fieldError("Invalid IPv4 address octet: $part", "hostname"),
                    )
                }
            }

            // Check for reserved addresses
            val firstOctet = parts[0].toInt()
            val secondOctet = parts[1].toInt()

            when {
                ipAddress == "0.0.0.0" -> Log.w(TAG, "Using 0.0.0.0 - this represents all interfaces")
                ipAddress.startsWith("127.") -> Log.i(TAG, "Localhost IP address: $ipAddress")
                ipAddress.startsWith("192.168.") ||
                    ipAddress.startsWith("10.") ||
                    (firstOctet == 172 && secondOctet in 16..31) -> {
                    Log.i(TAG, "Private IP address: $ipAddress")
                }
                firstOctet in 224..239 -> {
                    return ValidationResult.Failure(
                        ValidationError.fieldError("Multicast IP addresses are not supported", "hostname"),
                    )
                }
                firstOctet >= 240 -> {
                    return ValidationResult.Failure(
                        ValidationError.fieldError("Reserved IP address range", "hostname"),
                    )
                }
            }

            return ValidationResult.Success
        }

        /**
         * Validates IPv6 address (basic validation).
         */
        private fun validateIpV6Address(ipAddress: String): ValidationResult {
            try {
                InetAddress.getByName(ipAddress)
                Log.i(TAG, "IPv6 address validated: $ipAddress")
                return ValidationResult.Success
            } catch (e: UnknownHostException) {
                return ValidationResult.Failure(
                    ValidationError.fieldError("Invalid IPv6 address", "hostname"),
                )
            }
        }

        /**
         * Validates port number.
         */
        fun validatePort(
            port: Int,
            portType: String,
        ): ValidationResult {
            if (port < 1 || port > 65535) {
                return ValidationResult.Failure(
                    ValidationError.fieldError(
                        "$portType port must be between 1 and 65535",
                        "port",
                    ),
                )
            }

            // Check for well-known port conflicts
            val knownService = WELL_KNOWN_PORTS[port]
            if (knownService != null && portType != "SSH" && port != 22) {
                Log.w(TAG, "$portType port $port is commonly used for $knownService")
            }

            // Warn about system ports for non-privileged services
            if (port in SYSTEM_PORTS && portType != "SSH") {
                Log.w(TAG, "$portType port $port is in system port range (1-1023)")
            }

            return ValidationResult.Success
        }

        /**
         * Validates port assignments for conflicts and best practices.
         */
        private fun validatePortAssignments(
            sshPort: Int,
            wrapperPort: Int,
        ): ValidationResult {
            // Check for common problematic combinations
            if (sshPort == 80 || sshPort == 443) {
                return ValidationResult.Failure(
                    ValidationError.businessRuleError(
                        "SSH port $sshPort conflicts with HTTP/HTTPS. Consider using port 22",
                        "sshPort",
                        "SSH_PORT_HTTP_CONFLICT",
                    ),
                )
            }

            if (wrapperPort == 22) {
                return ValidationResult.Failure(
                    ValidationError.businessRuleError(
                        "Wrapper port cannot use SSH port 22",
                        "wrapperPort",
                        "WRAPPER_PORT_SSH_CONFLICT",
                    ),
                )
            }

            // Recommend registered port range for wrapper service
            if (wrapperPort in SYSTEM_PORTS) {
                Log.w(TAG, "Wrapper port $wrapperPort is in system port range - may require elevated privileges")
            }

            return ValidationResult.Success
        }

        /**
         * Tests if hostname can be resolved via DNS.
         */
        suspend fun canResolveHostname(hostname: String): Boolean =
            try {
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(DNS_TIMEOUT_MS) {
                        InetAddress.getByName(hostname)
                        true
                    } ?: false
                }
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Cannot resolve hostname: $hostname")
                false
            } catch (e: Exception) {
                Log.e(TAG, "DNS resolution error for $hostname", e)
                false
            }

        /**
         * Tests if a port is reachable on the given hostname.
         */
        suspend fun isPortReachable(
            hostname: String,
            port: Int,
        ): Boolean =
            try {
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(PORT_TEST_TIMEOUT_MS) {
                        Socket().use { socket ->
                            socket.connect(
                                InetSocketAddress(hostname, port),
                                PORT_TEST_TIMEOUT_MS.toInt(),
                            )
                            true
                        }
                    } ?: false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Port $port not reachable on $hostname: ${e.message}")
                false
            }

        /**
         * Gets network configuration recommendations.
         */
        fun getRecommendations(
            hostname: String,
            sshPort: Int,
            wrapperPort: Int,
        ): List<String> {
            val recommendations = mutableListOf<String>()

            // Hostname recommendations
            if (isValidIpAddress(hostname)) {
                if (hostname.startsWith("192.168.") ||
                    hostname.startsWith("10.") ||
                    hostname.startsWith("172.")
                ) {
                    recommendations.add("Using private IP address - ensure network connectivity from client")
                }
            } else {
                recommendations.add("Using hostname - ensure DNS resolution works from client network")
            }

            // SSH port recommendations
            if (sshPort != 22) {
                recommendations.add("Using non-standard SSH port $sshPort - ensure firewall allows this port")
            }

            if (sshPort in SYSTEM_PORTS && sshPort != 22) {
                recommendations.add("SSH port $sshPort is in system range - may require elevated privileges")
            }

            // Wrapper port recommendations
            if (wrapperPort == 8080) {
                recommendations.add("Using common HTTP alternate port 8080 - check for conflicts with other services")
            }

            if (wrapperPort in SYSTEM_PORTS) {
                recommendations.add("Wrapper port $wrapperPort is in system range - may require elevated privileges")
            }

            if (Math.abs(sshPort - wrapperPort) == 1) {
                recommendations.add("SSH and wrapper ports are consecutive - ensure both are available")
            }

            // Security recommendations
            if (sshPort == 22) {
                recommendations.add("Using standard SSH port 22 - consider using a different port for security")
            }

            return recommendations
        }

        /**
         * Detects common network configuration issues.
         */
        fun detectCommonIssues(
            hostname: String,
            sshPort: Int,
            wrapperPort: Int,
        ): List<String> {
            val issues = mutableListOf<String>()

            // Check for localhost configurations
            if (hostname.equals("localhost", ignoreCase = true) || hostname == "127.0.0.1") {
                issues.add("Localhost hostname will only work from the same machine")
            }

            // Check for port conflicts
            if (sshPort == wrapperPort) {
                issues.add("SSH and wrapper ports are the same - this will cause conflicts")
            }

            // Check for common misconfigurations
            if (sshPort == 80 || sshPort == 443) {
                issues.add("SSH port conflicts with HTTP/HTTPS - this is likely incorrect")
            }

            if (wrapperPort == 22) {
                issues.add("Wrapper port conflicts with SSH - this is likely incorrect")
            }

            // Check for unusual port assignments
            if (sshPort > 50000) {
                issues.add("SSH port is in dynamic/ephemeral range - may not be persistent")
            }

            if (wrapperPort > 50000) {
                issues.add("Wrapper port is in dynamic/ephemeral range - may not be persistent")
            }

            return issues
        }

        /**
         * Checks if a string represents a valid IP address.
         */
        private fun isValidIpAddress(address: String): Boolean =
            IP_V4_PATTERN.matcher(address).matches() ||
                IP_V6_PATTERN.matcher(address).matches()
    }
