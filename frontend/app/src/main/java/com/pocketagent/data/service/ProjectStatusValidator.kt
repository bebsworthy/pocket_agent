package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates project status transitions and business rules.
 *
 * This class handles the validation logic for project status changes,
 * extracted from ProjectStatusManager to improve maintainability
 * and separation of concerns.
 */
@Singleton
class ProjectStatusValidator
    @Inject
    constructor() {
        companion object {
            private const val TAG = "ProjectStatusValidator"
        }

        /**
         * Validates if a status transition is allowed.
         *
         * @param fromStatus The current status
         * @param toStatus The target status
         * @return ServiceResult indicating if transition is valid
         */
        fun validateStatusTransition(
            fromStatus: ProjectStatus,
            toStatus: ProjectStatus,
        ): ServiceResult<Unit> {
            Log.d(TAG, "Validating status transition: $fromStatus -> $toStatus")

            // Same status is always valid (no-op)
            if (fromStatus == toStatus) {
                return serviceSuccess(Unit)
            }

            val isValid =
                when (fromStatus) {
                    ProjectStatus.INACTIVE -> {
                        // From INACTIVE, can go to CONNECTING or ERROR
                        toStatus in listOf(ProjectStatus.CONNECTING, ProjectStatus.ERROR)
                    }
                    ProjectStatus.CONNECTING -> {
                        // From CONNECTING, can go to ACTIVE, ERROR, or back to INACTIVE
                        toStatus in listOf(ProjectStatus.ACTIVE, ProjectStatus.ERROR, ProjectStatus.INACTIVE)
                    }
                    ProjectStatus.ACTIVE -> {
                        // From ACTIVE, can go to DISCONNECTED, ERROR, or directly to INACTIVE
                        toStatus in listOf(ProjectStatus.DISCONNECTED, ProjectStatus.ERROR, ProjectStatus.INACTIVE)
                    }
                    ProjectStatus.DISCONNECTED -> {
                        // From DISCONNECTED, can go to CONNECTING or INACTIVE
                        toStatus in listOf(ProjectStatus.CONNECTING, ProjectStatus.INACTIVE)
                    }
                    ProjectStatus.ERROR -> {
                        // From ERROR, can go to any state (recovery)
                        true
                    }
                }

            return if (isValid) {
                serviceSuccess(Unit)
            } else {
                serviceFailure("Invalid status transition from $fromStatus to $toStatus")
            }
        }

        /**
         * Validates if a project can change to a specific status.
         *
         * @param project The project to validate
         * @param targetStatus The target status
         * @param currentStatusInfo Current status information
         * @return ServiceResult indicating if the change is valid
         */
        fun validateProjectStatusChange(
            project: Project,
            targetStatus: ProjectStatus,
            currentStatusInfo: ProjectStatusTracker.ProjectStatusInfo?,
        ): ServiceResult<Unit> {
            Log.d(TAG, "Validating project status change: ${project.id} -> $targetStatus")

            // Check basic transition validity
            val currentStatus = currentStatusInfo?.status ?: ProjectStatus.INACTIVE
            val transitionResult = validateStatusTransition(currentStatus, targetStatus)
            if (transitionResult.isFailure) {
                return transitionResult
            }

            // Additional business rule validations
            when (targetStatus) {
                ProjectStatus.CONNECTING -> {
                    // Validate project has valid server profile
                    if (project.serverProfileId.isBlank()) {
                        return serviceFailure("Project must have a valid server profile to connect")
                    }
                }
                ProjectStatus.ACTIVE -> {
                    // Validate project has necessary configuration
                    if (project.projectPath.isBlank()) {
                        return serviceFailure("Project must have a valid project path to be active")
                    }
                    if (project.scriptsFolder.isBlank()) {
                        return serviceFailure("Project must have a valid scripts folder to be active")
                    }
                }
                ProjectStatus.INACTIVE -> {
                    // Can always go to inactive (shutdown)
                }
                ProjectStatus.DISCONNECTED -> {
                    // Can go to disconnected from various states
                }
                ProjectStatus.ERROR -> {
                    // Can always go to error state
                }
            }

            // Check if project is already transitioning
            if (currentStatusInfo?.isTransitioning == true) {
                return serviceFailure("Project is already transitioning to ${currentStatusInfo.expectedNextStatus}")
            }

            return serviceSuccess(Unit)
        }

        /**
         * Validates concurrent project limits.
         *
         * @param activeProjects List of currently active project IDs
         * @param maxConcurrentProjects Maximum allowed concurrent active projects
         * @return ServiceResult indicating if limits are respected
         */
        fun validateConcurrentProjectLimits(
            activeProjects: List<String>,
            maxConcurrentProjects: Int = 5,
        ): ServiceResult<Unit> =
            if (activeProjects.size >= maxConcurrentProjects) {
                serviceFailure("Maximum concurrent active projects ($maxConcurrentProjects) reached")
            } else {
                serviceSuccess(Unit)
            }

        /**
         * Validates connection timeout requirements.
         *
         * @param statusInfo The current status information
         * @param timeoutMs The timeout in milliseconds
         * @return ServiceResult indicating if timeout requirements are met
         */
        fun validateConnectionTimeout(
            statusInfo: ProjectStatusTracker.ProjectStatusInfo,
            timeoutMs: Long,
        ): ServiceResult<Unit> {
            val durationSinceChange = statusInfo.getDurationSinceStatusChange()

            return when (statusInfo.status) {
                ProjectStatus.CONNECTING -> {
                    if (durationSinceChange > timeoutMs) {
                        serviceFailure("Connection attempt has timed out")
                    } else {
                        serviceSuccess(Unit)
                    }
                }
                ProjectStatus.DISCONNECTED -> {
                    // DISCONNECTED is a final state, no timeout validation needed
                    serviceSuccess(Unit)
                }
                else -> serviceSuccess(Unit)
            }
        }

        /**
         * Validates session requirements for active projects.
         *
         * @param statusInfo The current status information
         * @param requireClaudeSession Whether Claude session is required
         * @return ServiceResult indicating if session requirements are met
         */
        fun validateSessionRequirements(
            statusInfo: ProjectStatusTracker.ProjectStatusInfo,
            requireClaudeSession: Boolean = true,
        ): ServiceResult<Unit> {
            if (statusInfo.status == ProjectStatus.ACTIVE && requireClaudeSession) {
                if (statusInfo.claudeSessionId.isNullOrBlank()) {
                    return serviceFailure("Active project must have a valid Claude session")
                }
            }

            return serviceSuccess(Unit)
        }

        /**
         * Validates project health based on various metrics.
         *
         * @param statusInfo The current status information
         * @param maxConnectionAttempts Maximum allowed connection attempts
         * @param maxIdleTimeMs Maximum idle time before considering unhealthy
         * @return ServiceResult indicating project health status
         */
        fun validateProjectHealth(
            statusInfo: ProjectStatusTracker.ProjectStatusInfo,
            maxConnectionAttempts: Int = 5,
            maxIdleTimeMs: Long = 3600000L, // 1 hour
        ): ServiceResult<Unit> {
            // Check excessive connection attempts
            if (statusInfo.connectionAttempts > maxConnectionAttempts) {
                return serviceFailure("Project has exceeded maximum connection attempts ($maxConnectionAttempts)")
            }

            // Check idle time for active projects
            if (statusInfo.status == ProjectStatus.ACTIVE) {
                val idleTime = statusInfo.getDurationSinceLastActivity()
                if (idleTime != null && idleTime > maxIdleTimeMs) {
                    return serviceFailure("Project has been idle for too long")
                }
            }

            // Check for stuck transitions
            if (statusInfo.isTransitioning && statusInfo.hasTimedOut()) {
                return serviceFailure("Project transition has timed out")
            }

            return serviceSuccess(Unit)
        }
    }
