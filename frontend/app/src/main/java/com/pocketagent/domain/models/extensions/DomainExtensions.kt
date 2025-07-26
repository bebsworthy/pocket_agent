package com.pocketagent.domain.models.extensions

import com.pocketagent.common.constants.SizeConstants
import com.pocketagent.common.constants.TimeConstants
import com.pocketagent.domain.models.AppData
import com.pocketagent.domain.models.Result
import com.pocketagent.domain.models.entities.AttachmentType
import com.pocketagent.domain.models.entities.ConnectionStatus
import com.pocketagent.domain.models.entities.Message
import com.pocketagent.domain.models.entities.MessageAttachment
import com.pocketagent.domain.models.entities.MessageType
import com.pocketagent.domain.models.entities.Project
import com.pocketagent.domain.models.entities.ProjectEnvironment
import com.pocketagent.domain.models.entities.ProjectStatus
import com.pocketagent.domain.models.entities.ServerEnvironment
import com.pocketagent.domain.models.entities.ServerProfile
import com.pocketagent.domain.models.entities.SshIdentity
import com.pocketagent.domain.models.entities.SshKeyType
import com.pocketagent.domain.models.ui.ActivityItem
import com.pocketagent.domain.models.ui.ActivityType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Extension functions for domain models.
 */

// SshIdentity extensions
fun SshIdentity.getDisplayFingerprint(): String = publicKeyFingerprint.takeLast(TimeConstants.SSH_KEY_FINGERPRINT_DISPLAY_LENGTH)

fun SshIdentity.isExpiredOrExpiring(daysWarning: Int = TimeConstants.DEFAULT_SSH_KEY_EXPIRY_WARNING_DAYS): Boolean {
    val expiresAt = metadata.expiresAt ?: return false
    val warningTime = System.currentTimeMillis() + (daysWarning * TimeConstants.MILLIS_PER_DAY)
    return expiresAt <= warningTime
}

fun SshIdentity.getDaysUntilExpiry(): Int? {
    val expiresAt = metadata.expiresAt ?: return null
    val now = System.currentTimeMillis()
    return if (expiresAt > now) {
        ((expiresAt - now) / TimeConstants.MILLIS_PER_DAY).toInt()
    } else {
        0
    }
}

fun SshIdentity.incrementUsageCount(): SshIdentity =
    copy(
        metadata = metadata.copy(usageCount = metadata.usageCount + 1),
    )

fun SshIdentity.hasTag(tag: String): Boolean = metadata.tags.contains(tag)

fun SshIdentity.addTag(tag: String): SshIdentity =
    copy(
        metadata = metadata.copy(tags = metadata.tags + tag),
    )

fun SshIdentity.removeTag(tag: String): SshIdentity =
    copy(
        metadata = metadata.copy(tags = metadata.tags - tag),
    )

// ServerProfile extensions
fun ServerProfile.getStatusColor(): String =
    when (status) {
        ConnectionStatus.CONNECTED -> "#4CAF50"
        ConnectionStatus.CONNECTING -> "#FF9800"
        ConnectionStatus.ERROR -> "#F44336"
        ConnectionStatus.DISCONNECTED -> "#9E9E9E"
        ConnectionStatus.NEVER_CONNECTED -> "#757575"
        ConnectionStatus.DISCONNECTING -> "#FF9800"
    }

fun ServerProfile.getStatusIcon(): String =
    when (status) {
        ConnectionStatus.CONNECTED -> "check_circle"
        ConnectionStatus.CONNECTING -> "sync"
        ConnectionStatus.ERROR -> "error"
        ConnectionStatus.DISCONNECTED -> "radio_button_unchecked"
        ConnectionStatus.NEVER_CONNECTED -> "help_outline"
        ConnectionStatus.DISCONNECTING -> "sync"
    }

fun ServerProfile.getUptime(): Long? = lastConnectedAt?.let { System.currentTimeMillis() - it }

fun ServerProfile.hasCapability(capability: String): Boolean = metadata.capabilities.contains(capability)

fun ServerProfile.addCapability(capability: String): ServerProfile =
    copy(
        metadata = metadata.copy(capabilities = metadata.capabilities + capability),
    )

fun ServerProfile.removeCapability(capability: String): ServerProfile =
    copy(
        metadata = metadata.copy(capabilities = metadata.capabilities - capability),
    )

fun ServerProfile.hasTag(tag: String): Boolean = metadata.tags.contains(tag)

fun ServerProfile.addTag(tag: String): ServerProfile =
    copy(
        metadata = metadata.copy(tags = metadata.tags + tag),
    )

fun ServerProfile.removeTag(tag: String): ServerProfile =
    copy(
        metadata = metadata.copy(tags = metadata.tags - tag),
    )

// Project extensions
fun Project.getStatusColor(): String =
    when (status) {
        ProjectStatus.ACTIVE -> "#4CAF50"
        ProjectStatus.CONNECTING -> "#FF9800"
        ProjectStatus.ERROR -> "#F44336"
        ProjectStatus.INACTIVE -> "#9E9E9E"
        ProjectStatus.DISCONNECTED -> "#757575"
    }

fun Project.getStatusIcon(): String =
    when (status) {
        ProjectStatus.ACTIVE -> "play_circle_filled"
        ProjectStatus.CONNECTING -> "sync"
        ProjectStatus.ERROR -> "error"
        ProjectStatus.INACTIVE -> "pause_circle_filled"
        ProjectStatus.DISCONNECTED -> "stop_circle"
    }

fun Project.getSessionDuration(): Long? = lastActiveAt?.let { System.currentTimeMillis() - it }

fun Project.incrementSessionCount(): Project =
    copy(
        metadata =
            metadata.copy(
                statistics =
                    metadata.statistics.copy(
                        totalSessions = metadata.statistics.totalSessions + 1,
                    ),
            ),
    )

fun Project.incrementMessageCount(): Project =
    copy(
        metadata =
            metadata.copy(
                statistics =
                    metadata.statistics.copy(
                        totalMessages = metadata.statistics.totalMessages + 1,
                    ),
            ),
    )

fun Project.incrementScriptCount(): Project =
    copy(
        metadata =
            metadata.copy(
                statistics =
                    metadata.statistics.copy(
                        scriptsExecuted = metadata.statistics.scriptsExecuted + 1,
                    ),
            ),
    )

fun Project.incrementErrorCount(): Project =
    copy(
        metadata =
            metadata.copy(
                statistics =
                    metadata.statistics.copy(
                        errorsOccurred = metadata.statistics.errorsOccurred + 1,
                    ),
            ),
    )

fun Project.hasTag(tag: String): Boolean = metadata.tags.contains(tag)

fun Project.addTag(tag: String): Project =
    copy(
        metadata = metadata.copy(tags = metadata.tags + tag),
    )

fun Project.removeTag(tag: String): Project =
    copy(
        metadata = metadata.copy(tags = metadata.tags - tag),
    )

fun Project.addCollaborator(email: String): Project =
    copy(
        metadata = metadata.copy(collaborators = metadata.collaborators + email),
    )

fun Project.removeCollaborator(email: String): Project =
    copy(
        metadata = metadata.copy(collaborators = metadata.collaborators - email),
    )

fun Project.updateLanguage(language: String): Project =
    copy(
        metadata = metadata.copy(language = language),
    )

fun Project.updateFramework(framework: String): Project =
    copy(
        metadata = metadata.copy(framework = framework),
    )

// Message extensions
fun Message.getFormattedTimestamp(): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun Message.getTimeAgo(): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeConstants.TIME_AGO_JUST_NOW_THRESHOLD -> "Just now"
        diff < TimeConstants.TIME_AGO_MINUTES_THRESHOLD -> "${diff / TimeConstants.MILLIS_PER_MINUTE}m ago"
        diff < TimeConstants.TIME_AGO_HOURS_THRESHOLD -> "${diff / TimeConstants.MILLIS_PER_HOUR}h ago"
        diff < TimeConstants.TIME_AGO_DAYS_THRESHOLD -> "${diff / TimeConstants.MILLIS_PER_DAY}d ago"
        else -> getFormattedTimestamp()
    }
}

fun Message.hasAttachments(): Boolean = attachments.isNotEmpty()

fun Message.getAttachmentCount(): Int = attachments.size

fun Message.getAttachmentsOfType(type: AttachmentType): List<MessageAttachment> = attachments.filter { it.type == type }

fun Message.getCodeAttachments(): List<MessageAttachment> = attachments.filter { it.isCodeFile() }

fun Message.getImageAttachments(): List<MessageAttachment> = attachments.filter { it.isImage() }

fun Message.getWordCount(): Int = content.split("\\s+".toRegex()).size

fun Message.getCharacterCount(): Int = content.length

fun Message.containsKeyword(keyword: String): Boolean = content.contains(keyword, ignoreCase = true)

fun Message.hasMetadata(key: String): Boolean = metadata.containsKey(key)

fun Message.getMetadataValue(key: String): String? = metadata[key]

// MessageAttachment extensions
fun MessageAttachment.getDisplaySize(): String {
    val bytes = size
    return when {
        bytes < SizeConstants.SIZE_DISPLAY_KB_THRESHOLD -> "${bytes}B"
        bytes < SizeConstants.SIZE_DISPLAY_MB_THRESHOLD -> "${bytes / SizeConstants.BYTES_PER_KB}KB"
        bytes < SizeConstants.SIZE_DISPLAY_GB_THRESHOLD -> "${bytes / SizeConstants.BYTES_PER_MB}MB"
        else -> "${bytes / SizeConstants.BYTES_PER_GB}GB"
    }
}

fun MessageAttachment.getPreview(maxLength: Int = TimeConstants.DEFAULT_PREVIEW_MAX_LENGTH): String =
    if (content.length <= maxLength) content else "${content.take(maxLength)}..."

fun MessageAttachment.isExecutable(): Boolean = getFileExtension() in listOf("sh", "bat", "cmd", "exe", "py", "js", "ts")

fun MessageAttachment.isArchive(): Boolean = getFileExtension() in listOf("zip", "tar", "gz", "rar", "7z")

fun MessageAttachment.isDocument(): Boolean = getFileExtension() in listOf("pdf", "doc", "docx", "txt", "md", "rtf")

// AppData extensions
fun AppData.getStatistics(): AppStatistics {
    val activeProjects = projects.count { it.isActive }
    val connectedProjects = projects.count { it.isConnected() }
    val totalMessages = messages.values.sumOf { it.size }
    val totalSessions = projects.sumOf { it.metadata.statistics.totalSessions }
    val totalScripts = projects.sumOf { it.metadata.statistics.scriptsExecuted }
    val totalErrors = projects.sumOf { it.metadata.statistics.errorsOccurred }

    return AppStatistics(
        totalProjects = projects.size,
        activeProjects = activeProjects,
        connectedProjects = connectedProjects,
        totalMessages = totalMessages,
        totalSessions = totalSessions,
        totalScripts = totalScripts,
        totalErrors = totalErrors,
        totalIdentities = sshIdentities.size,
        totalServers = serverProfiles.size,
    )
}

fun AppData.getRecentActivity(limit: Int = SizeConstants.DEFAULT_RECENT_ACTIVITY_LIMIT): List<ActivityItem> {
    val activities = mutableListOf<ActivityItem>()

    // Add recent projects
    projects
        .sortedByDescending { it.lastActiveAt ?: it.createdAt }
        .take(limit)
        .forEach { project ->
            activities.add(
                ActivityItem(
                    id = "project_${project.id}",
                    type = ActivityType.PROJECT_CONNECTED,
                    title = "Project ${project.name}",
                    description = "Last active",
                    timestamp = project.lastActiveAt ?: project.createdAt,
                    projectId = project.id,
                ),
            )
        }

    // Add recent messages
    messages
        .flatMap { (projectId, msgs) ->
            msgs.takeLast(limit).map { msg ->
                ActivityItem(
                    id = "message_${msg.id}",
                    type = if (msg.isFromUser()) ActivityType.MESSAGE_SENT else ActivityType.MESSAGE_RECEIVED,
                    title = if (msg.isFromUser()) "You" else "Claude",
                    description = msg.getPreview(),
                    timestamp = msg.timestamp,
                    projectId = projectId,
                )
            }
        }.sortedByDescending { it.timestamp }
        .take(limit)
        .forEach { activities.add(it) }

    return activities.sortedByDescending { it.timestamp }.take(limit)
}

fun AppData.findProjectByName(name: String): Project? = projects.find { it.name.equals(name, ignoreCase = true) }

fun AppData.findServerProfileByName(name: String): ServerProfile? = serverProfiles.find { it.name.equals(name, ignoreCase = true) }

fun AppData.findSshIdentityByName(name: String): SshIdentity? = sshIdentities.find { it.name.equals(name, ignoreCase = true) }

fun AppData.searchProjects(query: String): List<Project> =
    projects.filter { project ->
        project.name.contains(query, ignoreCase = true) ||
            project.description?.contains(query, ignoreCase = true) == true ||
            project.projectPath.contains(query, ignoreCase = true) ||
            project.metadata.tags.any { it.contains(query, ignoreCase = true) }
    }

fun AppData.searchMessages(
    query: String,
    projectId: String? = null,
): List<Message> {
    val messagesToSearch =
        if (projectId != null) {
            messages[projectId] ?: emptyList()
        } else {
            messages.values.flatten()
        }

    return messagesToSearch.filter { message ->
        message.content.contains(query, ignoreCase = true) ||
            message.attachments.any { it.content.contains(query, ignoreCase = true) }
    }
}

fun AppData.getProjectsWithErrors(): List<Project> = projects.filter { it.hasError() }

fun AppData.getExpiredSshIdentities(): List<SshIdentity> = sshIdentities.filter { it.isExpired() }

fun AppData.getUnusedSshIdentities(): List<SshIdentity> =
    sshIdentities.filter { identity ->
        !serverProfiles.any { it.sshIdentityId == identity.id }
    }

fun AppData.getUnusedServerProfiles(): List<ServerProfile> =
    serverProfiles.filter { server ->
        !projects.any { it.serverProfileId == server.id }
    }

// Collection extensions
fun List<Project>.filterByStatus(status: ProjectStatus): List<Project> = filter { it.status == status }

fun List<Project>.filterByEnvironment(environment: ProjectEnvironment): List<Project> = filter { it.metadata.environment == environment }

fun List<Project>.filterByLanguage(language: String): List<Project> = filter { it.metadata.language.equals(language, ignoreCase = true) }

@JvmName("filterProjectsByTag")
fun List<Project>.filterByTag(tag: String): List<Project> = filter { it.hasTag(tag) }

fun List<ServerProfile>.filterByStatus(status: ConnectionStatus): List<ServerProfile> = filter { it.status == status }

fun List<ServerProfile>.filterByEnvironment(environment: ServerEnvironment): List<ServerProfile> =
    filter { it.metadata.environment == environment }

@JvmName("filterServerProfilesByTag")
fun List<ServerProfile>.filterByTag(tag: String): List<ServerProfile> = filter { it.hasTag(tag) }

fun List<SshIdentity>.filterByKeyType(keyType: SshKeyType): List<SshIdentity> = filter { it.keyType == keyType }

@JvmName("filterSshIdentitiesByTag")
fun List<SshIdentity>.filterByTag(tag: String): List<SshIdentity> = filter { it.hasTag(tag) }

fun List<SshIdentity>.filterExpired(): List<SshIdentity> = filter { it.isExpired() }

fun List<SshIdentity>.filterExpiring(daysWarning: Int = 30): List<SshIdentity> = filter { it.isExpiredOrExpiring(daysWarning) }

fun List<Message>.filterByType(type: MessageType): List<Message> = filter { it.type == type }

fun List<Message>.filterByTimeRange(
    startTime: Long,
    endTime: Long,
): List<Message> = filter { it.timestamp in startTime..endTime }

fun List<Message>.filterWithAttachments(): List<Message> = filter { it.hasAttachments() }

fun List<Message>.filterByKeyword(keyword: String): List<Message> = filter { it.containsKeyword(keyword) }

// Result extensions
fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> =
    apply {
        if (this is Result.Success) action(data)
    }

fun <T> Result<T>.onError(action: (Throwable) -> Unit): Result<T> =
    apply {
        if (this is Result.Error) action(exception)
    }

fun <T> Result<T>.onLoading(action: () -> Unit): Result<T> =
    apply {
        if (this is Result.Loading) action()
    }

fun <T> Result<T>.isSuccessful(): Boolean = this is Result.Success

fun <T> Result<T>.isFailure(): Boolean = this is Result.Error

fun <T> Result<T>.isInProgress(): Boolean = this is Result.Loading

// Utility data classes
data class AppStatistics(
    val totalProjects: Int,
    val activeProjects: Int,
    val connectedProjects: Int,
    val totalMessages: Int,
    val totalSessions: Int,
    val totalScripts: Int,
    val totalErrors: Int,
    val totalIdentities: Int,
    val totalServers: Int,
)

// Formatting utilities
object DomainFormatUtils {
    fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat(TimeConstants.TIMESTAMP_FORMAT_PATTERN, Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / TimeConstants.MILLIS_PER_SECOND
        val minutes = seconds / TimeConstants.SECONDS_PER_MINUTE
        val hours = minutes / TimeConstants.MINUTES_PER_HOUR
        val days = hours / TimeConstants.HOURS_PER_DAY

        return when {
            days > 0 -> "${days}d ${hours % TimeConstants.HOURS_PER_DAY}h"
            hours > 0 -> "${hours}h ${minutes % TimeConstants.MINUTES_PER_HOUR}m"
            minutes > 0 -> "${minutes}m ${seconds % TimeConstants.SECONDS_PER_MINUTE}s"
            else -> "${seconds}s"
        }
    }

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / SizeConstants.BYTES_PER_KB
        val mb = kb / SizeConstants.BYTES_PER_KB
        val gb = mb / SizeConstants.BYTES_PER_KB

        return when {
            gb > 0 -> "${gb}GB"
            mb > 0 -> "${mb}MB"
            kb > 0 -> "${kb}KB"
            else -> "${bytes}B"
        }
    }

    fun formatPercentage(value: Double): String = "${(value * 100).toInt()}%"

    fun formatNumber(value: Long): String =
        when {
            value >= SizeConstants.NUMBER_FORMAT_MILLION_THRESHOLD -> "${value / SizeConstants.NUMBER_FORMAT_MILLION_THRESHOLD}M"
            value >= SizeConstants.NUMBER_FORMAT_THOUSAND_THRESHOLD -> "${value / SizeConstants.NUMBER_FORMAT_THOUSAND_THRESHOLD}K"
            else -> value.toString()
        }
}
