package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for project status transitions and workflow management.
 * 
 * This service provides:
 * - Project status transition validation and management
 * - Workflow state tracking and monitoring
 * - Status change notifications and observables
 * - Automatic status cleanup and timeout handling
 * - Status statistics and reporting
 * - Connection state management integration
 */
@Singleton
class ProjectStatusManager @Inject constructor(
    private val projectService: ProjectService
) {
    
    companion object {
        private const val TAG = "ProjectStatusManager"
        private const val CONNECTION_TIMEOUT_MS = 30000L
        private const val ACTIVE_SESSION_TIMEOUT_MS = 3600000L // 1 hour
        private const val STATUS_UPDATE_DEBOUNCE_MS = 500L
    }
    
    // Status tracking
    private val _statusMap = MutableStateFlow<Map<String, ProjectStatusInfo>>(emptyMap())
    val statusMap: StateFlow<Map<String, ProjectStatusInfo>> = _statusMap.asStateFlow()
    
    private val statusMutex = Mutex()
    
    /**
     * Extended project status information.
     */
    data class ProjectStatusInfo(
        val projectId: String,
        val status: ProjectStatus,
        val claudeSessionId: String? = null,
        val lastStatusChange: Long = System.currentTimeMillis(),
        val lastActivity: Long? = null,
        val connectionAttempts: Int = 0,
        val lastError: String? = null,
        val isTransitioning: Boolean = false,
        val expectedNextStatus: ProjectStatus? = null,
        val timeoutAt: Long? = null
    ) {
        /**
         * Check if the project is in an active state.
         */
        fun isActive(): Boolean = status == ProjectStatus.ACTIVE
        
        /**
         * Check if the project is connecting.
         */
        fun isConnecting(): Boolean = status == ProjectStatus.CONNECTING
        
        /**
         * Check if the project has an error.
         */
        fun hasError(): Boolean = status == ProjectStatus.ERROR
        
        /**
         * Check if the project is in a stable state.
         */
        fun isStable(): Boolean = !isTransitioning && status in listOf(
            ProjectStatus.INACTIVE, 
            ProjectStatus.ACTIVE, 
            ProjectStatus.ERROR
        )
        
        /**
         * Check if the status has timed out.
         */
        fun hasTimedOut(): Boolean = timeoutAt != null && System.currentTimeMillis() > timeoutAt
        
        /**
         * Get the duration since last status change.
         */
        fun getDurationSinceStatusChange(): Long = System.currentTimeMillis() - lastStatusChange
        
        /**
         * Get the duration since last activity.
         */
        fun getDurationSinceActivity(): Long? = lastActivity?.let { 
            System.currentTimeMillis() - it 
        }
    }
    
    /**
     * Status transition configuration.
     */
    data class StatusTransitionConfig(
        val allowAutomaticRetry: Boolean = true,
        val maxRetryAttempts: Int = 3,
        val retryDelayMs: Long = 5000L,
        val enableTimeout: Boolean = true,
        val timeoutMs: Long = CONNECTION_TIMEOUT_MS,
        val notifyOnChange: Boolean = true
    )
    
    /**
     * Status workflow definition.
     */
    enum class StatusWorkflow {
        PROJECT_ACTIVATION,
        PROJECT_DEACTIVATION,
        ERROR_RECOVERY,
        CONNECTION_RETRY,
        CLEANUP_INACTIVE
    }
    
    /**
     * Updates the status information for a project.
     */
    suspend fun updateProjectStatus(
        projectId: String,
        newStatus: ProjectStatus,
        claudeSessionId: String? = null,
        error: String? = null,
        config: StatusTransitionConfig = StatusTransitionConfig()
    ): ServiceResult<ProjectStatusInfo> = withContext(Dispatchers.Default) {
        Log.d(TAG, "Updating project status: $projectId -> $newStatus")
        
        return@withContext statusMutex.withLock {
            try {
                val currentInfo = _statusMap.value[projectId]
                val currentStatus = currentInfo?.status ?: ProjectStatus.INACTIVE
                
                // Validate transition
                if (!isValidTransition(currentStatus, newStatus)) {
                    return@withLock serviceFailure(
                        "Invalid status transition from $currentStatus to $newStatus"
                    )
                }
                
                // Calculate timeout if applicable
                val timeoutAt = if (config.enableTimeout && newStatus in listOf(
                    ProjectStatus.CONNECTING, ProjectStatus.DISCONNECTED
                )) {
                    System.currentTimeMillis() + config.timeoutMs
                } else null
                
                // Create new status info
                val newInfo = ProjectStatusInfo(
                    projectId = projectId,
                    status = newStatus,
                    claudeSessionId = claudeSessionId,
                    lastStatusChange = System.currentTimeMillis(),
                    lastActivity = if (newStatus == ProjectStatus.ACTIVE) System.currentTimeMillis() 
                                  else currentInfo?.lastActivity,
                    connectionAttempts = when (newStatus) {
                        ProjectStatus.CONNECTING -> (currentInfo?.connectionAttempts ?: 0) + 1
                        ProjectStatus.ACTIVE -> 0 // Reset on successful connection
                        else -> currentInfo?.connectionAttempts ?: 0
                    },
                    lastError = error ?: if (newStatus == ProjectStatus.ERROR) currentInfo?.lastError else null,
                    isTransitioning = newStatus in listOf(ProjectStatus.CONNECTING, ProjectStatus.DISCONNECTED),
                    expectedNextStatus = when (newStatus) {
                        ProjectStatus.CONNECTING -> ProjectStatus.ACTIVE
                        ProjectStatus.DISCONNECTED -> ProjectStatus.INACTIVE
                        else -> null
                    },
                    timeoutAt = timeoutAt
                )
                
                // Update status map
                val updatedMap = _statusMap.value.toMutableMap()
                updatedMap[projectId] = newInfo
                _statusMap.value = updatedMap
                
                // Update project in repository
                val projectResult = projectService.updateProjectStatus(
                    projectId, newStatus, claudeSessionId, error
                )
                
                if (projectResult.isFailure) {
                    // Revert status map on failure
                    val revertedMap = _statusMap.value.toMutableMap()
                    if (currentInfo != null) {
                        revertedMap[projectId] = currentInfo
                    } else {
                        revertedMap.remove(projectId)
                    }
                    _statusMap.value = revertedMap
                    
                    return@withLock serviceFailure(
                        "Failed to update project status: ${projectResult.getErrorMessage()}"
                    )
                }
                
                Log.d(TAG, "Project status updated successfully: $projectId -> $newStatus")
                serviceSuccess(newInfo)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update project status", e)
                serviceFailure("Failed to update project status: ${e.message}")
            }
        }
    }
    
    /**
     * Activates a project with the specified Claude session.
     */
    suspend fun activateProject(
        projectId: String,
        claudeSessionId: String,
        config: StatusTransitionConfig = StatusTransitionConfig()
    ): ServiceResult<ProjectStatusInfo> {
        Log.d(TAG, "Activating project: $projectId with session: $claudeSessionId")
        
        // First transition to CONNECTING
        val connectingResult = updateProjectStatus(
            projectId, ProjectStatus.CONNECTING, claudeSessionId, config = config
        )
        
        if (connectingResult.isFailure) {
            return connectingResult
        }
        
        // Simulate connection process (in real implementation, this would establish connection)
        return updateProjectStatus(
            projectId, ProjectStatus.ACTIVE, claudeSessionId, config = config
        )
    }
    
    /**
     * Deactivates a project.
     */
    suspend fun deactivateProject(
        projectId: String,
        config: StatusTransitionConfig = StatusTransitionConfig()
    ): ServiceResult<ProjectStatusInfo> {
        Log.d(TAG, "Deactivating project: $projectId")
        
        // First transition to DISCONNECTED
        val disconnectingResult = updateProjectStatus(
            projectId, ProjectStatus.DISCONNECTED, config = config
        )
        
        if (disconnectingResult.isFailure) {
            return disconnectingResult
        }
        
        // Then to INACTIVE
        return updateProjectStatus(
            projectId, ProjectStatus.INACTIVE, config = config
        )
    }
    
    /**
     * Marks a project as having an error.
     */
    suspend fun markProjectError(
        projectId: String,
        error: String,
        config: StatusTransitionConfig = StatusTransitionConfig()
    ): ServiceResult<ProjectStatusInfo> {
        Log.d(TAG, "Marking project as error: $projectId - $error")
        
        return updateProjectStatus(
            projectId, ProjectStatus.ERROR, error = error, config = config
        )
    }
    
    /**
     * Recovers a project from error state.
     */
    suspend fun recoverFromError(
        projectId: String,
        config: StatusTransitionConfig = StatusTransitionConfig()
    ): ServiceResult<ProjectStatusInfo> {
        Log.d(TAG, "Recovering project from error: $projectId")
        
        return updateProjectStatus(
            projectId, ProjectStatus.INACTIVE, config = config
        )
    }
    
    /**
     * Updates activity timestamp for an active project.
     */
    suspend fun updateActivity(projectId: String): ServiceResult<ProjectStatusInfo> =
        withContext(Dispatchers.Default) {
            statusMutex.withLock {
                val currentInfo = _statusMap.value[projectId]
                if (currentInfo != null && currentInfo.isActive()) {
                    val updatedInfo = currentInfo.copy(
                        lastActivity = System.currentTimeMillis()
                    )
                    
                    val updatedMap = _statusMap.value.toMutableMap()
                    updatedMap[projectId] = updatedInfo
                    _statusMap.value = updatedMap
                    
                    serviceSuccess(updatedInfo)
                } else {
                    serviceFailure("Project is not active or not found")
                }
            }
        }
    
    /**
     * Gets current status information for a project.
     */
    fun getProjectStatusInfo(projectId: String): ProjectStatusInfo? {
        return _statusMap.value[projectId]
    }
    
    /**
     * Gets all projects with a specific status.
     */
    fun getProjectsByStatus(status: ProjectStatus): List<ProjectStatusInfo> {
        return _statusMap.value.values.filter { it.status == status }
    }
    
    /**
     * Gets all active projects.
     */
    fun getActiveProjects(): List<ProjectStatusInfo> {
        return getProjectsByStatus(ProjectStatus.ACTIVE)
    }
    
    /**
     * Gets all projects with errors.
     */
    fun getProjectsWithErrors(): List<ProjectStatusInfo> {
        return getProjectsByStatus(ProjectStatus.ERROR)
    }
    
    /**
     * Gets all transitioning projects.
     */
    fun getTransitioningProjects(): List<ProjectStatusInfo> {
        return _statusMap.value.values.filter { it.isTransitioning }
    }
    
    /**
     * Observes status changes for a specific project.
     */
    fun observeProjectStatus(projectId: String): Flow<ProjectStatusInfo?> {
        return statusMap.map { it[projectId] }.distinctUntilChanged()
    }
    
    /**
     * Observes all projects with a specific status.
     */
    fun observeProjectsByStatus(status: ProjectStatus): Flow<List<ProjectStatusInfo>> {
        return statusMap.map { statusMap ->
            statusMap.values.filter { it.status == status }
        }.distinctUntilChanged()
    }
    
    /**
     * Observes active project count.
     */
    fun observeActiveProjectCount(): Flow<Int> {
        return statusMap.map { statusMap ->
            statusMap.values.count { it.isActive() }
        }.distinctUntilChanged()
    }
    
    /**
     * Observes status statistics.
     */
    fun observeStatusStatistics(): Flow<StatusStatistics> {
        return statusMap.map { statusMap ->
            val values = statusMap.values
            StatusStatistics(
                totalProjects = values.size,
                activeProjects = values.count { it.isActive() },
                connectingProjects = values.count { it.isConnecting() },
                errorProjects = values.count { it.hasError() },
                inactiveProjects = values.count { it.status == ProjectStatus.INACTIVE },
                transitioningProjects = values.count { it.isTransitioning },
                timedOutProjects = values.count { it.hasTimedOut() }
            )
        }.distinctUntilChanged()
    }
    
    /**
     * Validates if a status transition is allowed.
     */
    private fun isValidTransition(fromStatus: ProjectStatus, toStatus: ProjectStatus): Boolean {
        val validTransitions = mapOf(
            ProjectStatus.INACTIVE to setOf(
                ProjectStatus.CONNECTING,
                ProjectStatus.INACTIVE
            ),
            ProjectStatus.CONNECTING to setOf(
                ProjectStatus.ACTIVE,
                ProjectStatus.ERROR,
                ProjectStatus.DISCONNECTED,
                ProjectStatus.INACTIVE,
                ProjectStatus.CONNECTING
            ),
            ProjectStatus.ACTIVE to setOf(
                ProjectStatus.DISCONNECTED,
                ProjectStatus.ERROR,
                ProjectStatus.CONNECTING,
                ProjectStatus.ACTIVE
            ),
            ProjectStatus.DISCONNECTED to setOf(
                ProjectStatus.CONNECTING,
                ProjectStatus.INACTIVE,
                ProjectStatus.ERROR,
                ProjectStatus.DISCONNECTED
            ),
            ProjectStatus.ERROR to setOf(
                ProjectStatus.CONNECTING,
                ProjectStatus.INACTIVE,
                ProjectStatus.DISCONNECTED,
                ProjectStatus.ERROR
            )
        )
        
        return validTransitions[fromStatus]?.contains(toStatus) == true
    }
    
    /**
     * Performs automatic cleanup of stale status entries.
     */
    suspend fun performStatusCleanup(): ServiceResult<StatusCleanupResult> =
        withContext(Dispatchers.Default) {
            Log.d(TAG, "Performing status cleanup")
            
            statusMutex.withLock {
                try {
                    val currentTime = System.currentTimeMillis()
                    val currentMap = _statusMap.value.toMutableMap()
                    var cleanupCount = 0
                    var timeoutCount = 0
                    
                    val iterator = currentMap.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        val info = entry.value
                        
                        // Handle timeouts
                        if (info.hasTimedOut()) {
                            Log.d(TAG, "Status timeout for project: ${info.projectId}")
                            
                            val newStatus = when (info.status) {
                                ProjectStatus.CONNECTING -> ProjectStatus.ERROR
                                ProjectStatus.DISCONNECTED -> ProjectStatus.INACTIVE
                                else -> info.status
                            }
                            
                            if (newStatus != info.status) {
                                currentMap[entry.key] = info.copy(
                                    status = newStatus,
                                    lastStatusChange = currentTime,
                                    isTransitioning = false,
                                    expectedNextStatus = null,
                                    timeoutAt = null,
                                    lastError = if (newStatus == ProjectStatus.ERROR) "Connection timeout" else null
                                )
                                timeoutCount++
                            }
                        }
                        
                        // Clean up old inactive entries
                        if (info.status == ProjectStatus.INACTIVE && 
                            info.getDurationSinceStatusChange() > ACTIVE_SESSION_TIMEOUT_MS) {
                            iterator.remove()
                            cleanupCount++
                        }
                    }
                    
                    _statusMap.value = currentMap
                    
                    Log.d(TAG, "Status cleanup completed: $cleanupCount removed, $timeoutCount timed out")
                    serviceSuccess(StatusCleanupResult(cleanupCount, timeoutCount))
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Status cleanup failed", e)
                    serviceFailure("Status cleanup failed: ${e.message}")
                }
            }
        }
    
    /**
     * Initializes status tracking for a project.
     */
    suspend fun initializeProjectStatus(projectId: String, project: Project) {
        statusMutex.withLock {
            val currentMap = _statusMap.value.toMutableMap()
            currentMap[projectId] = ProjectStatusInfo(
                projectId = projectId,
                status = project.status,
                claudeSessionId = project.claudeSessionId,
                lastActivity = project.lastActiveAt
            )
            _statusMap.value = currentMap
        }
    }
    
    /**
     * Removes status tracking for a project.
     */
    suspend fun removeProjectStatus(projectId: String) {
        statusMutex.withLock {
            val currentMap = _statusMap.value.toMutableMap()
            currentMap.remove(projectId)
            _statusMap.value = currentMap
        }
    }
    
    /**
     * Status statistics data class.
     */
    data class StatusStatistics(
        val totalProjects: Int,
        val activeProjects: Int,
        val connectingProjects: Int,
        val errorProjects: Int,
        val inactiveProjects: Int,
        val transitioningProjects: Int,
        val timedOutProjects: Int
    ) {
        val healthyProjects: Int = activeProjects + inactiveProjects
        val problematicProjects: Int = errorProjects + timedOutProjects
        val healthPercentage: Float = if (totalProjects > 0) {
            (healthyProjects.toFloat() / totalProjects.toFloat()) * 100f
        } else 0f
    }
    
    /**
     * Status cleanup result.
     */
    data class StatusCleanupResult(
        val cleanedUpCount: Int,
        val timedOutCount: Int
    )
}