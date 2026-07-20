package com.kavach.app.presentation.viewmodel

import app.cash.turbine.test
import com.kavach.app.domain.model.Incident
import com.kavach.app.domain.model.IncidentStatus
import com.kavach.app.domain.model.ResolvedIncident
import com.kavach.app.domain.usecases.incident.GenerateEvidenceUseCase
import com.kavach.app.domain.usecases.incident.ObserveIncidentsUseCase
import com.kavach.app.domain.usecases.incident.ResolveIncidentUseCase
import com.kavach.app.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var observeIncidentsUseCase: ObserveIncidentsUseCase
    private lateinit var resolveIncidentUseCase: ResolveIncidentUseCase
    private lateinit var generateEvidenceUseCase: GenerateEvidenceUseCase
    private lateinit var viewModel: IncidentViewModel

    private val sampleIncident = Incident(
        id        = "inc-001",
        elderId   = "elder-001",
        status    = IncidentStatus.OPEN,
        riskScore = 0.85,
        startedAt = "2024-01-01T10:00:00Z",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        observeIncidentsUseCase = mockk()
        every { observeIncidentsUseCase() } returns flowOf(listOf(sampleIncident))
        resolveIncidentUseCase  = mockk()
        generateEvidenceUseCase = mockk()
        viewModel = IncidentViewModel(observeIncidentsUseCase, resolveIncidentUseCase, generateEvidenceUseCase)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `incidents state emits Success with items from use case`() = runTest {
        viewModel.incidents.test {
            val state = awaitItem()
            assertTrue(state is IncidentListState.Success)
            assertEquals(1, (state as IncidentListState.Success).items.size)
            assertEquals("inc-001", state.items[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolve emits Loading then Resolved on success`() = runTest {
        val resolved = ResolvedIncident("inc-001", "resolved", "2024-01-01T11:00:00Z")
        coEvery { resolveIncidentUseCase("inc-001", "resolved", null) } returns
            NetworkResult.Success(resolved)

        viewModel.resolve("inc-001", "resolved")
        advanceUntilIdle()

        assertTrue(viewModel.actionState.value is IncidentActionState.Resolved)
    }

    @Test
    fun `resolve emits Error on failure`() = runTest {
        coEvery { resolveIncidentUseCase(any(), any(), any()) } returns
            NetworkResult.Error("Not a guardian for this elder", 403)

        viewModel.resolve("inc-001", "resolved")
        advanceUntilIdle()

        val state = viewModel.actionState.value
        assertTrue(state is IncidentActionState.Error)
        assertEquals("Not a guardian for this elder", (state as IncidentActionState.Error).message)
    }

    @Test
    fun `resetActionState returns to Idle`() = runTest {
        coEvery { resolveIncidentUseCase(any(), any(), any()) } returns
            NetworkResult.Error("err")
        viewModel.resolve("inc-001", "resolved")
        advanceUntilIdle()

        viewModel.resetActionState()
        assertEquals(IncidentActionState.Idle, viewModel.actionState.value)
    }
}
