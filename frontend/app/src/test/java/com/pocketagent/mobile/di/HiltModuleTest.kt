package com.pocketagent.mobile.di

import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import com.pocketagent.mobile.domain.repository.DataRepository
import com.pocketagent.mobile.domain.repository.ProjectRepository
import com.pocketagent.mobile.domain.repository.SshIdentityRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Test class for Hilt dependency injection modules.
 *
 * This class verifies that all dependencies are properly injected and configured.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HiltModuleTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var encryptedStorage: EncryptedJsonStorage

    @Inject
    lateinit var dataRepository: DataRepository

    @Inject
    lateinit var sshIdentityRepository: SshIdentityRepository

    @Inject
    lateinit var projectRepository: ProjectRepository

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun `verify all dependencies are injected`() {
        // Verify that all dependencies are properly injected
        assert(::encryptedStorage.isInitialized) { "EncryptedJsonStorage should be injected" }
        assert(::dataRepository.isInitialized) { "DataRepository should be injected" }
        assert(::sshIdentityRepository.isInitialized) { "SshIdentityRepository should be injected" }
        assert(::projectRepository.isInitialized) { "ProjectRepository should be injected" }
        assert(::ioDispatcher.isInitialized) { "IO dispatcher should be injected" }
        assert(::mainDispatcher.isInitialized) { "Main dispatcher should be injected" }
    }

    @Test
    fun `verify dispatcher types are correct`() {
        // Verify that the correct dispatcher types are injected
        assert(ioDispatcher.toString().contains("IO")) { "IO dispatcher should be of correct type" }
        assert(mainDispatcher.toString().contains("Main")) { "Main dispatcher should be of correct type" }
    }
}
