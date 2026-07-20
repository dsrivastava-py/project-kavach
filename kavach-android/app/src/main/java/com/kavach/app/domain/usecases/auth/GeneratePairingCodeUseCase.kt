package com.kavach.app.domain.usecases.auth

import com.kavach.app.domain.model.PairingCode
import com.kavach.app.domain.repository.AuthRepository
import com.kavach.app.utils.NetworkResult
import javax.inject.Inject

/**
 * Generate a 6-digit pairing code as an elder.
 * Requires a valid elder JWT already stored in SessionManager.
 */
class GeneratePairingCodeUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): NetworkResult<PairingCode> =
        authRepository.generatePairingCode()
}
