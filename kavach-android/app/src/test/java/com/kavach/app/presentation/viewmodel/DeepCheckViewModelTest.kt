package com.kavach.app.presentation.viewmodel

import com.kavach.app.data.local.SessionManager
import com.kavach.app.domain.model.DeepCheckSession
import com.kavach.app.domain.model.DeepCheckStatus
import com.kavach.app.domain.usecases.deepcheck.StartDeepCheckUseCase
import com.kavach.app.utils.NetworkResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DeepCheckViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var startDeepCheckUseCase: StartDeepCheckUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: DeepCheckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        startDeepCheckUseCase = mockk()
        sessionManager        = mockk {
            every { elderId } returns flowOf("elder-001")
        }
        viewModel = DeepCheckViewModel(startDeepCheckUseCase, sessionManager)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is Idle`() {
        assertEquals(DeepCheckUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `startDeepCheck emits Done on success`() = runTest {
        val doneSession = DeepCheckSession(
            sessionId = "sess-001",
            status    = DeepCheckStatus.DONE,
            transcript = "Hello, this is a test",
            redFlags   = listOf("urgency language"),
            spoofScore = 0.85,
            assistiveOnly = true,
            spoofDisclaimer = "Assistive only.",
        )
        val mockFile = mockk<File> {
            every { exists() } returns true
            every { length() } returns 1024L
            every { name }     returns "test.ogg"
        }
        coEvery {
            startDeepCheckUseCase(
                audioFile  = mockFile,
                elderId    = "elder-001",
                incidentId = null,
                onPoll     = any(),
            )
        } returns NetworkResult.Success(doneSession)

        viewModel.startDeepCheck(mockFile)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is DeepCheckUiState.Done)
        assertEquals("sess-001", (state as DeepCheckUiState.Done).session.sessionId)
        assertEquals(0.85, state.session.spoofScore!!, 0.001)
        assertTrue(state.session.assistiveOnly)
    }

    @Test
    fun `startDeepCheck emits Error on failure`() = runTest {
        val mockFile = mockk<File> {
            every { exists() } returns true
            every { length() } returns 1024L
            every { name }     returns "test.ogg"
        }
        coEvery {
            startDeepCheckUseCase(any(), any(), any(), any())
        } returns NetworkResult.Error("Server error", 502)

        viewModel.startDeepCheck(mockFile)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is DeepCheckUiState.Error)
        assertEquals("Server error", (state as DeepCheckUiState.Error).message)
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        val mockFile = mockk<File> {
            every { exists() } returns true
            every { length() } returns 1024L
            every { name }     returns "test.ogg"
        }
        coEvery { startDeepCheckUseCase(any(), any(), any(), any()) } returns
            NetworkResult.Error("err")
        viewModel.startDeepCheck(mockFile)
        advanceUntilIdle()

        viewModel.reset()
        assertEquals(DeepCheckUiState.Idle, viewModel.state.value)
    }
}
