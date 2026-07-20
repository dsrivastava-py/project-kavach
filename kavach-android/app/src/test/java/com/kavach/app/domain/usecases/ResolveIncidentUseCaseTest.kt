package com.kavach.app.domain.usecases

import com.kavach.app.domain.model.ResolvedIncident
import com.kavach.app.domain.repository.IncidentRepository
import com.kavach.app.domain.usecases.incident.ResolveIncidentUseCase
import com.kavach.app.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ResolveIncidentUseCaseTest {

    private lateinit var incidentRepository: IncidentRepository
    private lateinit var useCase: ResolveIncidentUseCase

    @Before
    fun setUp() {
        incidentRepository = mockk()
        useCase = ResolveIncidentUseCase(incidentRepository)
    }

    @Test
    fun `returns error for blank incident id`() = runTest {
        val result = useCase("", "resolved")
        assertTrue(result is NetworkResult.Error)
        assertEquals("Incident ID is required", (result as NetworkResult.Error).message)
    }

    @Test
    fun `returns error for invalid resolution value`() = runTest {
        val result = useCase("incident-id", "dismissed")
        assertTrue(result is NetworkResult.Error)
        assertEquals("Resolution must be 'resolved' or 'false_positive'", (result as NetworkResult.Error).message)
    }

    @Test
    fun `accepts resolved as valid resolution`() = runTest {
        val expected = NetworkResult.Success(ResolvedIncident("incident-id", "resolved", "2024-01-01T00:00:00Z"))
        coEvery { incidentRepository.resolveIncident("incident-id", "resolved", null) } returns expected

        val result = useCase("incident-id", "resolved")

        assertTrue(result is NetworkResult.Success)
        assertEquals("resolved", (result as NetworkResult.Success).data.status)
    }

    @Test
    fun `accepts false_positive as valid resolution`() = runTest {
        val expected = NetworkResult.Success(ResolvedIncident("incident-id", "false_positive", "2024-01-01T00:00:00Z"))
        coEvery { incidentRepository.resolveIncident("incident-id", "false_positive", any()) } returns expected

        val result = useCase("incident-id", "false_positive", "Elder confirmed safe")

        assertTrue(result is NetworkResult.Success)
    }
}
