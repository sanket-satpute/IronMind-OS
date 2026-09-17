package com.sanket_satpute_20.ironmind.domain.usecase.profile

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeUserProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class UserProfileInitializationTest {

    private lateinit var repository: FakeUserProfileRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    
    private lateinit var initializeLocalProfileUseCase: InitializeLocalProfileUseCase
    private lateinit var getLocalProfileUseCase: GetLocalProfileUseCase

    @Before
    fun setup() {
        repository = FakeUserProfileRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        
        initializeLocalProfileUseCase = InitializeLocalProfileUseCase(repository, idGenerator, clock)
        getLocalProfileUseCase = GetLocalProfileUseCase(repository)
    }

    @Test
    fun `initialize local profile creates a new profile when none exists`() = runTest {
        idGenerator.nextId = "test-profile-id"
        
        // 1. Initially, no local profile
        val initialResult = getLocalProfileUseCase()
        assertTrue(initialResult is Result.Success)
        assertEquals(null, (initialResult as Result.Success).data)
        
        // 2. Initialize
        val initResult = initializeLocalProfileUseCase()
        assertTrue(initResult is Result.Success)
        val createdProfile = (initResult as Result.Success).data
        
        assertEquals("test-profile-id", createdProfile.id)
        assertEquals("User", createdProfile.displayName)
        assertEquals(clock.currentTimeMillis(), createdProfile.createdAt)
        
        // 3. Verify it was saved to the repository
        val fetchedResult = getLocalProfileUseCase()
        assertTrue(fetchedResult is Result.Success)
        assertEquals(createdProfile, (fetchedResult as Result.Success).data)
    }

    @Test
    fun `initialize local profile returns existing profile when already exists (reload after process death)`() = runTest {
        // 1. Manually inject a profile into the repository
        val existingProfile = UserProfile(
            id = "existing-id",
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            displayName = "Custom Name",
            timezone = TimeZone.getDefault().id,
            createdFrom = "local_device",
            status = "ACTIVE"
        )
        repository.saveProfile(existingProfile)
        
        // 2. Initialize
        val initResult = initializeLocalProfileUseCase()
        assertTrue(initResult is Result.Success)
        val returnedProfile = (initResult as Result.Success).data
        
        // 3. Assert it returned the existing one, not a new one
        assertEquals("existing-id", returnedProfile.id)
        assertEquals("Custom Name", returnedProfile.displayName)
    }

    @Test
    fun `repository failure during initialization propagates error`() = runTest {
        repository.shouldFail = true
        
        val initResult = initializeLocalProfileUseCase()
        assertTrue(initResult is Result.Failure)
    }
}
