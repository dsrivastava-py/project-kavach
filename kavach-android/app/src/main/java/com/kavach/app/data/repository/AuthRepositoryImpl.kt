package com.kavach.app.data.repository

import com.kavach.app.data.local.SessionManager
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.PairGuardianRequest
import com.kavach.app.domain.model.GuardianSession
import com.kavach.app.domain.model.PairingCode
import com.kavach.app.domain.repository.AuthRepository
import com.kavach.app.utils.NetworkResult
import com.kavach.app.utils.safeApiCall
import javax.inject.Inject

/**
 * Concrete implementation of [AuthRepository].
 *
 * On successful guardian pairing, the JWT is persisted in [SessionManager]
 * before returning — the caller never needs to call save separately.
 */
class AuthRepositoryImpl @Inject constructor(
    private val api: KavachApiService,
    private val sessionManager: SessionManager,
) : AuthRepository {

    override suspend fun pairGuardian(
        pairingCode: String,
        guardianPhone: String,
    ): NetworkResult<GuardianSession> {
        val result = safeApiCall {
            api.pairGuardian(PairGuardianRequest(pairingCode, guardianPhone))
        }

        if (result is NetworkResult.Success) {
            // Persist session — extract sub from JWT manually (no decode lib needed)
            sessionManager.saveGuardianSession(
                token = result.data.token,
                guardianId = result.data.guardianId,
                userId = result.data.guardianId, // backend sub == guardian user id
            )
        }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(
                GuardianSession(result.data.guardianId, result.data.token)
            )
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }

    override suspend fun generatePairingCode(): NetworkResult<PairingCode> {
        val result = safeApiCall { api.generatePairingCode() }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(
                PairingCode(result.data.pairingCode, result.data.expiresInSeconds)
            )
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }

    override suspend fun logout() {
        sessionManager.clearSession()
    }
}
