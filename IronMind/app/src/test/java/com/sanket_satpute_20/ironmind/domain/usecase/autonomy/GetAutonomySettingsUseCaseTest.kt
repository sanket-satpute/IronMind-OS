package com.sanket_satpute_20.ironmind.domain.usecase.autonomy

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAutonomySettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetAutonomySettingsUseCaseTest {
    private lateinit var repository: FakeAutonomySettingsRepository
    private lateinit var useCase: GetAutonomySettingsUseCase

    @Before
    fun setup() {
        repository = FakeAutonomySettingsRepository()
        useCase = GetAutonomySettingsUseCase(repository)
    }

    @Test
    fun `invoke returns autonomy settings successfully`() = runTest {
        repository.updateLevel("user-1", AutonomyCapability.SCHEDULING, AutonomyLevel.FULL_AUTO)
        val result = useCase("user-1")

        assertTrue(result is Result.Success)
        val settings = (result as Result.Success).data
        assertEquals(AutonomyLevel.FULL_AUTO, settings.getLevel(AutonomyCapability.SCHEDULING))
        
        // Defaults to SUGGEST_ONLY
        assertEquals(AutonomyLevel.SUGGEST_ONLY, settings.getLevel(AutonomyCapability.PLANNING))
    }
}
