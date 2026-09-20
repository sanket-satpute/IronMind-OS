package com.sanket_satpute_20.ironmind.domain.usecase.autonomy

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAutonomySettingsRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateAutonomyLevelUseCaseTest {
    private lateinit var repository: FakeAutonomySettingsRepository
    private lateinit var logger: FakeIronLogger
    private lateinit var useCase: UpdateAutonomyLevelUseCase

    @Before
    fun setup() {
        repository = FakeAutonomySettingsRepository()
        logger = FakeIronLogger()
        useCase = UpdateAutonomyLevelUseCase(repository, logger)
    }

    @Test
    fun `invoke updates repository and logs lifecycle event on success`() = runTest {
        val result = useCase("user-1", AutonomyCapability.PLANNING, AutonomyLevel.FULL_AUTO)
        assertTrue(result is Result.Success)

        val settingsResult = repository.getSettings("user-1") as Result.Success
        assertEquals(AutonomyLevel.FULL_AUTO, settingsResult.data.getLevel(AutonomyCapability.PLANNING))

        assertTrue(logger.loggedMessages.any { it.contains("[Autonomy] [CHANGED]") && it.contains("PLANNING") && it.contains("FULL_AUTO") })
    }

    @Test
    fun `invoke does not log on failure`() = runTest {
        repository.shouldFail = true
        val result = useCase("user-1", AutonomyCapability.PLANNING, AutonomyLevel.FULL_AUTO)
        assertTrue(result is Result.Failure)
        assertTrue(logger.loggedMessages.isEmpty())
    }
}
