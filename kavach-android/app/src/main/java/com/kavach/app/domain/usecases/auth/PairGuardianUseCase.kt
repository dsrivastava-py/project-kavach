package com.kavach.app.domain.usecases.auth

import com.kavach.app.domain.model.GuardianSession
import com.kavach.app.domain.repository.AuthRepository
import com.kavach.app.utils.NetworkResult
import javax.inject.Inject

/**
 * Pair a guardian using a short-lived pairing code.
 *
 * Validates input before hitting the network:
 * - Pairing code must be exactly 6 digits.
 * - Phone must start with '+' (E.164).
 */
class PairGuardianUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        pairingCode: String,
        guardianPhone: String,
    ): NetworkResult<GuardianSession> {
        if (pairingCode.length != 6 || !pairingCode.all { it.isDigit() }) {
            return NetworkResult.Error("Pairing code must be exactly 6 digits")
        }
        if (!guardianPhone.startsWith("+")) {
            return NetworkResult.Error("Phone number must be in E.164 format (e.g. +919876543210)")
        }
        return authRepository.pairGuardian(pairingCode, guardianPhone)
    }
}
