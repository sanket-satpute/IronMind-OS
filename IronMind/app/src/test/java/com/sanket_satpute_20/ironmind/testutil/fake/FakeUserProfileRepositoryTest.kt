package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUserProfileRepositoryTest {

    @Test
    fun `save and get profile returns success`() = runTest {
        val repo = FakeUserProfileRepository()
        val profile = UserProfile(
            id = "user1",
            createdAt = 1L,
            updatedAt = 2L,
            displayName = "Test",
            timezone = "UTC",
            createdFrom = "APP",
            status = "ACTIVE"
        )

        val saveResult = repo.saveProfile(profile)
        assertTrue(saveResult.isSuccess)

        val getResult = repo.getProfile("user1")
        assertTrue(getResult.isSuccess)
        assertEquals("Test", getResult.getOrNull()?.displayName)
    }

    @Test
    fun `shouldFail flag causes failures`() = runTest {
        val repo = FakeUserProfileRepository()
        repo.shouldFail = true

        val getResult = repo.getProfile("user1")
        assertTrue(getResult.isFailure)
        assertEquals("Fake failure", getResult.errorOrNull()?.message)
    }
}
