package com.pocketagent.data.migration

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for managing available data migrations.
 *
 * This class maintains a registry of all available data migrations and provides
 * methods to find and execute the appropriate migrations based on version requirements.
 */
@Singleton
class MigrationRegistry
    @Inject
    constructor() {
        private val migrations = mutableMapOf<Pair<Int, Int>, DataMigration>()
        private val registryMutex = Mutex()

        /**
         * Registers a migration in the registry.
         *
         * @param migration The migration to register
         * @throws IllegalArgumentException if a migration for the same version pair already exists
         */
        suspend fun registerMigration(migration: DataMigration) {
            registryMutex.withLock {
                val key = Pair(migration.fromVersion, migration.toVersion)

                if (migrations.containsKey(key)) {
                    throw IllegalArgumentException(
                        "Migration from version ${migration.fromVersion} to ${migration.toVersion} already registered",
                    )
                }

                migrations[key] = migration
            }
        }

        /**
         * Finds a specific migration by version pair.
         *
         * @param fromVersion The version to migrate from
         * @param toVersion The version to migrate to
         * @return The migration if found, null otherwise
         */
        suspend fun findMigration(
            fromVersion: Int,
            toVersion: Int,
        ): DataMigration? {
            return registryMutex.withLock {
                migrations[Pair(fromVersion, toVersion)]
            }
        }

        /**
         * Finds the migration path from one version to another.
         *
         * This method can find multi-step migration paths if direct migrations
         * are not available (e.g., v1 -> v2 -> v3 to go from v1 to v3).
         *
         * @param fromVersion The starting version
         * @param toVersion The target version
         * @return List of migrations to execute in order, or empty list if no path exists
         */
        suspend fun findMigrationPath(
            fromVersion: Int,
            toVersion: Int,
        ): List<DataMigration> {
            if (fromVersion == toVersion) {
                return emptyList()
            }

            return registryMutex.withLock {
                if (fromVersion < toVersion) {
                    findUpgradePath(fromVersion, toVersion)
                } else {
                    findDowngradePath(fromVersion, toVersion)
                }
            }
        }

        /**
         * Gets all registered migrations.
         *
         * @return List of all registered migrations sorted by version
         */
        suspend fun getAllMigrations(): List<DataMigration> {
            return registryMutex.withLock {
                migrations.values.sortedBy { it.fromVersion }
            }
        }

        /**
         * Gets migrations that can be applied from a specific version.
         *
         * @param fromVersion The version to find migrations from
         * @return List of migrations that can be applied from the specified version
         */
        suspend fun getMigrationsFromVersion(fromVersion: Int): List<DataMigration> {
            return registryMutex.withLock {
                migrations.values.filter { it.fromVersion == fromVersion }
                    .sortedBy { it.toVersion }
            }
        }

        /**
         * Gets migrations that can be applied to a specific version.
         *
         * @param toVersion The target version
         * @return List of migrations that result in the specified version
         */
        suspend fun getMigrationsToVersion(toVersion: Int): List<DataMigration> {
            return registryMutex.withLock {
                migrations.values.filter { it.toVersion == toVersion }
                    .sortedBy { it.fromVersion }
            }
        }

        /**
         * Checks if a migration path exists between two versions.
         *
         * @param fromVersion The starting version
         * @param toVersion The target version
         * @return true if a migration path exists, false otherwise
         */
        suspend fun hasMigrationPath(
            fromVersion: Int,
            toVersion: Int,
        ): Boolean {
            return findMigrationPath(fromVersion, toVersion).isNotEmpty() || fromVersion == toVersion
        }

        /**
         * Gets the highest available version that can be migrated to.
         *
         * @return The highest available version, or 1 if no migrations are registered
         */
        suspend fun getHighestVersion(): Int {
            return registryMutex.withLock {
                migrations.values.maxOfOrNull { it.toVersion } ?: 1
            }
        }

        /**
         * Gets the lowest available version that can be migrated from.
         *
         * @return The lowest available version, or 1 if no migrations are registered
         */
        suspend fun getLowestVersion(): Int {
            return registryMutex.withLock {
                migrations.values.minOfOrNull { it.fromVersion } ?: 1
            }
        }

        /**
         * Validates that all registered migrations form a valid migration chain.
         *
         * @return ValidationResult indicating whether the migration chain is valid
         */
        suspend fun validateMigrationChain(): ValidationResult {
            return registryMutex.withLock {
                val errors = mutableListOf<String>()

                // Check for gaps in migration chain
                val versions = migrations.values.flatMap { listOf(it.fromVersion, it.toVersion) }.toSet().sorted()

                for (i in 0 until versions.size - 1) {
                    val currentVersion = versions[i]
                    val nextVersion = versions[i + 1]

                    // Check if there's a way to go from current to next version
                    if (!hasDirectOrIndirectPath(currentVersion, nextVersion)) {
                        errors.add("No migration path from version $currentVersion to $nextVersion")
                    }
                }

                // Check for circular dependencies
                val visitedPaths = mutableSetOf<Pair<Int, Int>>()
                migrations.keys.forEach { (from, to) ->
                    if (hasCircularDependency(from, to, visitedPaths)) {
                        errors.add("Circular dependency detected in migration chain")
                    }
                }

                ValidationResult(
                    isValid = errors.isEmpty(),
                    errors = errors,
                )
            }
        }

        /**
         * Clears all registered migrations.
         * This is primarily used for testing.
         */
        suspend fun clearMigrations() {
            registryMutex.withLock {
                migrations.clear()
            }
        }

        /**
         * Finds an upgrade path from a lower version to a higher version.
         */
        private fun findUpgradePath(
            fromVersion: Int,
            toVersion: Int,
        ): List<DataMigration> {
            val path = mutableListOf<DataMigration>()
            var currentVersion = fromVersion

            while (currentVersion < toVersion) {
                // Find the next available migration
                val nextMigration =
                    migrations.values
                        .filter { it.fromVersion == currentVersion && it.toVersion <= toVersion }
                        .maxByOrNull { it.toVersion }

                if (nextMigration == null) {
                    // No direct path found, try to find any migration from current version
                    val anyMigration =
                        migrations.values
                            .find { it.fromVersion == currentVersion }

                    if (anyMigration == null) {
                        // No migration available from current version
                        return emptyList()
                    }

                    path.add(anyMigration)
                    currentVersion = anyMigration.toVersion
                } else {
                    path.add(nextMigration)
                    currentVersion = nextMigration.toVersion
                }

                // Prevent infinite loops
                if (path.size > 10) {
                    return emptyList()
                }
            }

            return path
        }

        /**
         * Finds a downgrade path from a higher version to a lower version.
         */
        private fun findDowngradePath(
            fromVersion: Int,
            toVersion: Int,
        ): List<DataMigration> {
            val path = mutableListOf<DataMigration>()
            var currentVersion = fromVersion

            while (currentVersion > toVersion) {
                // Find a reversible migration that can downgrade from current version
                val nextMigration =
                    migrations.values
                        .filter { it.toVersion == currentVersion && it.fromVersion >= toVersion && it.isReversible }
                        .minByOrNull { it.fromVersion }

                if (nextMigration == null) {
                    // No reversible migration available
                    return emptyList()
                }

                path.add(nextMigration)
                currentVersion = nextMigration.fromVersion

                // Prevent infinite loops
                if (path.size > 10) {
                    return emptyList()
                }
            }

            return path
        }

        /**
         * Checks if there's a direct or indirect path between two versions.
         */
        private fun hasDirectOrIndirectPath(
            fromVersion: Int,
            toVersion: Int,
        ): Boolean {
            if (fromVersion == toVersion) return true

            // Check for direct migration
            if (migrations.containsKey(Pair(fromVersion, toVersion))) {
                return true
            }

            // Check for indirect path through other versions
            val visited = mutableSetOf<Int>()
            return hasPath(fromVersion, toVersion, visited)
        }

        /**
         * Recursive helper to check for migration paths.
         */
        private fun hasPath(
            from: Int,
            to: Int,
            visited: MutableSet<Int>,
        ): Boolean {
            if (from == to) return true
            if (from in visited) return false

            visited.add(from)

            // Try all migrations from current version
            migrations.values
                .filter { it.fromVersion == from }
                .forEach { migration ->
                    if (hasPath(migration.toVersion, to, visited)) {
                        return true
                    }
                }

            return false
        }

        /**
         * Checks for circular dependencies in migration chain.
         */
        private fun hasCircularDependency(
            from: Int,
            to: Int,
            visited: MutableSet<Pair<Int, Int>>,
        ): Boolean {
            val key = Pair(from, to)
            if (key in visited) return true

            visited.add(key)

            // Check all migrations that can follow this one
            migrations.values
                .filter { it.fromVersion == to }
                .forEach { migration ->
                    if (hasCircularDependency(migration.fromVersion, migration.toVersion, visited)) {
                        return true
                    }
                }

            visited.remove(key)
            return false
        }

        /**
         * Result of migration chain validation.
         */
        data class ValidationResult(
            val isValid: Boolean,
            val errors: List<String>,
        )
    }

/**
 * Builder for creating and configuring a MigrationRegistry.
 */
class MigrationRegistryBuilder {
    private val migrations = mutableListOf<DataMigration>()

    /**
     * Adds a migration to the registry.
     */
    fun addMigration(migration: DataMigration): MigrationRegistryBuilder {
        migrations.add(migration)
        return this
    }

    /**
     * Builds the migration registry with all configured migrations.
     */
    suspend fun build(): MigrationRegistry {
        val registry = MigrationRegistry()

        migrations.forEach { migration ->
            registry.registerMigration(migration)
        }

        return registry
    }
}
