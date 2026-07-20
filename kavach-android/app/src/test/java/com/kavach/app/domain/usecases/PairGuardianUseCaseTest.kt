package com.kavach.app.domain.usecases

import com.kavach.app.domain.model.GuardianSession
import com.kavach.app.domain.repository.AuthRepository
import com.kavach.app.domain.usecases.auth.PairGuardianUseCase
import com.kavach.app.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PairGuardianUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var useCase: PairGuardianUseCase

    @Before
    fun setUp() {
        authRepository = mockk()
        useCase = PairGuardianUseCase(authRepository)
    }

    @Test
    fun `returns error when pairing code is shorter than 6 digits`() = runTest {
        val result = useCase("123", "+919876543210")
        assertTrue(result is NetworkResult.Error)
        assertEquals("Pairing code must be exactly 6 digits", (result as NetworkResult.Error).message)
    }

    @Test
    fun `returns error when pairing code contains non-digits`() = runTest {
        val result = useCase("12345X", "+919876543210")
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `returns error when phone does not start with plus`() = runTest {
        val result = useCase("123456", "919876543210")
        assertTrue(result is NetworkResult.Error)
        assertEquals("Phone number must be in E.164 format (e.g. +919876543210)", (result as NetworkResult.Error).message)
    }

    @Test
    fun `delegates to repository when input is valid`() = runTest {
        val expected = NetworkResult.Success(GuardianSession("guardian-id", "token"))
        coEvery { authRepository.pairGuardian("123456", "+919876543210") } returns expected

        val result = useCase("123456", "+919876543210")

        assertTrue(result is NetworkResult.Success)
        assertEquals("guardian-id", (result as NetworkResult.Success).data.guardianId)
    }

    @Test
    fun `propagates repository error`() = runTest {
        coEvery { authRepository.pairGuardian(any(), any()) } returns
            NetworkResult.Error("Invalid or expired pairing code", 400)

        val result = useCase("999999", "+919876543210")

        assertTrue(result is NetworkResult.Error)
        assertEquals("Invalid or expired pairing code", (result as NetworkResult.Error).message)
    }
}
