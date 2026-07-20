package com.kavach.app.domain.repository

import com.kavach.app.domain.model.GuardianSession
import com.kavach.app.domain.model.PairingCode
import com.kavach.app.utils.NetworkResult

/**
 * Authentication repository interface.
 *
 * The backend has no /login endpoint. Auth flows through:
 * 1. Guardian: POST /guardians/pair (redeems pairing code → issues JWT)
 * 2. Elder: POST /guardians/generate-pairing-code (elder JWT required)
 */
interface AuthRepository {

    /**
     * Pair as a guardian using a short-lived Redis pairing code.
     * On success, the JWT is stored in [SessionManager] automatically.
     */
    suspend fun pairGuardian(
        pairingCode: String,
        guardianPhone: String,
    ): NetworkResult<GuardianSession>

    /**
     * Generate a 6-digit pairing code as an elder.
     * Requires a valid elder JWT already stored in [SessionManager].
     */
    suspend fun generatePairingCode(): NetworkResult<PairingCode>

    /** Clear the stored session (logout). */
    suspend fun logout()
}
