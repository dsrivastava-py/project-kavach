package com.kavach.app.presentation.viewmodel

import app.cash.turbine.test
import com.kavach.app.data.local.SessionManager
import com.kavach.app.domain.model.GuardianSession
import com.kavach.app.domain.usecases.auth.GeneratePairingCodeUseCase
import com.kavach.app.domain.usecases.auth.LogoutUseCase
import com.kavach.app.domain.usecases.auth.PairGuardianUseCase
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
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var pairGuardianUseCase: PairGuardianUseCase
    private lateinit var generatePairingCodeUseCase: GeneratePairingCodeUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        pairGuardianUseCase        = mockk()
        generatePairingCodeUseCase = mockk()
        logoutUseCase              = mockk(relaxed = true)

        // SessionManager is a Singleton — mock its Flow properties correctly
        sessionManager = mockk {
            every { isLoggedIn } returns flowOf(false)
            every { userRole   } returns flowOf(null)
        }

        viewModel = AuthViewModel(
            pairGuardianUseCase,
            generatePairingCodeUseCase,
            logoutUseCase,
            sessionManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pairState starts as Idle`() {
        assertEquals(AuthUiState.Idle, viewModel.pairState.value)
    }

    @Test
    fun `codeState starts as Idle`() {
        assertEquals(AuthUiState.Idle, viewModel.codeState.value)
    }

    @Test
    fun `pairGuardian emits Success on happy path`() = runTest {
        coEvery { pairGuardianUseCase("123456", "+919876543210") } returns
            NetworkResult.Success(GuardianSession("guardian-id", "jwt-token"))

        viewModel.pairGuardian("123456", "+919876543210")
        advanceUntilIdle()

        val state = viewModel.pairState.value
        assertTrue("Expected Success but got $state", state is AuthUiState.Success<*>)
        val session = (state as AuthUiState.Success<*>).data as GuardianSession
        assertEquals("guardian-id", session.guardianId)
        assertEquals("jwt-token",   session.token)
    }

    @Test
    fun `pairGuardian emits Error on API failure`() = runTest {
        coEvery { pairGuardianUseCase(any(), any()) } returns
            NetworkResult.Error("Invalid or expired pairing code", 400)

        viewModel.pairGuardian("000000", "+919876543210")
        advanceUntilIdle()

        val state = viewModel.pairState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(
            "Invalid or expired pairing code",
            (state as AuthUiState.Error).message,
        )
    }

    @Test
    fun `pairGuardian emits Error on validation failure`() = runTest {
        coEvery { pairGuardianUseCase("12", "+91999") } returns
            NetworkResult.Error("Pairing code must be exactly 6 digits")

        viewModel.pairGuardian("12", "+91999")
        advanceUntilIdle()

        assertTrue(viewModel.pairState.value is AuthUiState.Error)
    }

    @Test
    fun `resetPairState resets to Idle`() = runTest {
        coEvery { pairGuardianUseCase(any(), any()) } returns
            NetworkResult.Error("err")

        viewModel.pairGuardian("x", "y")
        advanceUntilIdle()

        assertFalse(viewModel.pairState.value is AuthUiState.Idle)

        viewModel.resetPairState()
        assertEquals(AuthUiState.Idle, viewModel.pairState.value)
    }

    @Test
    fun `resetCodeState resets to Idle`() = runTest {
        viewModel.resetCodeState()
        assertEquals(AuthUiState.Idle, viewModel.codeState.value)
    }

    @Test
    fun `isLoggedIn reflects sessionManager`() = runTest {
        viewModel.isLoggedIn.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
