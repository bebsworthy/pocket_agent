package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.ProjectStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks project status information and state transitions.
 *
 * This class is responsible for maintaining the current status state of all projects
 * and providing observable flows for status changes. It's extracted from ProjectStatusManager
 * to reduce complexity and improve maintainability.
 */
@Singleton
class ProjectStatusTracker
    @Inject
    constructor() {
        companion object {
            private const val TAG = "ProjectStatusTracker"
        }

        // Status tracking
        private val _statusMap = MutableStateFlow<Map<String, ProjectStatusInfo>>(emptyMap())
        val statusMap: StateFlow<Map<String, ProjectStatusInfo>> = _statusMap.asStateFlow()

        private val statusMutex = Mutex()

        /**
         * Updates the status information for a project.
         *
         * @param projectId The project ID
         * @param statusInfo The new status information
         */
        suspend fun updateStatusInfo(
            projectId: String,
            statusInfo: ProjectStatusInfo,
        ) {
            Log.d(TAG, "Updating status info for project $projectId: ${statusInfo.status}")

            statusMutex.withLock {
                val currentMap = _statusMap.value
                _statusMap.value = currentMap + (projectId to statusInfo)
            }
        }

        /**
         * Gets the current status information for a project.
         *
         * @param projectId The project ID
         * @return The status information or null if not found
         */
        suspend fun getStatusInfo(projectId: String): ProjectStatusInfo? =
            statusMutex.withLock {
                _statusMap.value[projectId]
            }

        /**
         * Removes status information for a project.
         *
         * @param projectId The project ID
         */
        suspend fun removeStatusInfo(projectId: String) {
            Log.d(TAG, "Removing status info for project $projectId")

            statusMutex.withLock {
                val currentMap = _statusMap.value
                _statusMap.value = currentMap - projectId
            }
        }

        /**
         * Observes the status of a specific project.
         *
         * @param projectId The project ID
         * @return Flow of project status
         */
        fun observeProjectStatus(projectId: String): Flow<ProjectStatus?> =
            statusMap
                .map { it[projectId]?.status }
                .distinctUntilChanged()

        /**
         * Observes the status information of a specific project.
         *
         * @param projectId The project ID
         * @return Flow of project status information
         */
        fun observeProjectStatusInfo(projectId: String): Flow<ProjectStatusInfo?> =
            statusMap
                .map { it[projectId] }
                .distinctUntilChanged()

        /**
         * Gets all projects with a specific status.
         *
         * @param status The status to filter by
         * @return List of project IDs with the specified status
         */
        suspend fun getProjectsWithStatus(status: ProjectStatus): List<String> =
            statusMutex.withLock {
                _statusMap.value
                    .filter { it.value.status == status }
                    .keys
                    .toList()
            }

        /**
         * Gets all active projects.
         *
         * @return List of active project IDs
         */
        suspend fun getActiveProjects(): List<String> = getProjectsWithStatus(ProjectStatus.ACTIVE)

        /**
         * Gets all projects that have timed out.
         *
         * @return List of timed out project status information
         */
        suspend fun getTimedOutProjects(): List<ProjectStatusInfo> =
            statusMutex.withLock {
                _statusMap.value.values.filter { it.hasTimedOut() }
            }

        /**
         * Gets all projects that are currently transitioning.
         *
         * @return List of transitioning project status information
         */
        suspend fun getTransitioningProjects(): List<ProjectStatusInfo> =
            statusMutex.withLock {
                _statusMap.value.values.filter { it.isTransitioning }
            }

        /**
         * Clears all status information.
         */
        suspend fun clearAllStatus() {
            Log.d(TAG, "Clearing all status information")

            statusMutex.withLock {
                _statusMap.value = emptyMap()
            }
        }

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
            val timeoutAt: Long? = null,
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
            fun isStable(): Boolean =
                !isTransitioning &&
                    status in
                    listOf(
                        ProjectStatus.INACTIVE,
                        ProjectStatus.ACTIVE,
                        ProjectStatus.ERROR,
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
            fun getDurationSinceLastActivity(): Long? = lastActivity?.let { System.currentTimeMillis() - it }

            /**
             * Create a copy with updated status.
             */
            fun withStatus(newStatus: ProjectStatus): ProjectStatusInfo =
                copy(
                    status = newStatus,
                    lastStatusChange = System.currentTimeMillis(),
                    isTransitioning = false,
                    expectedNextStatus = null,
                )

            /**
             * Create a copy marking as transitioning.
             */
            fun withTransition(
                expectedStatus: ProjectStatus,
                timeoutMs: Long? = null,
            ): ProjectStatusInfo =
                copy(
                    isTransitioning = true,
                    expectedNextStatus = expectedStatus,
                    timeoutAt = timeoutMs?.let { System.currentTimeMillis() + it },
                )

            /**
             * Create a copy with updated activity.
             */
            fun withActivity(): ProjectStatusInfo = copy(lastActivity = System.currentTimeMillis())

            /**
             * Create a copy with updated error.
             */
            fun withError(error: String): ProjectStatusInfo =
                copy(
                    status = ProjectStatus.ERROR,
                    lastError = error,
                    lastStatusChange = System.currentTimeMillis(),
                    isTransitioning = false,
                    expectedNextStatus = null,
                )

            /**
             * Create a copy with incremented connection attempts.
             */
            fun withConnectionAttempt(): ProjectStatusInfo = copy(connectionAttempts = connectionAttempts + 1)
        }
    }
