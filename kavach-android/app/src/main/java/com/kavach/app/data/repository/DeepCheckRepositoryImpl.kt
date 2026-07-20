package com.kavach.app.data.repository

import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.domain.model.DeepCheckSession
import com.kavach.app.domain.model.DeepCheckStatus
import com.kavach.app.domain.repository.DeepCheckRepository
import com.kavach.app.utils.NetworkResult
import com.kavach.app.utils.safeApiCall
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

class DeepCheckRepositoryImpl @Inject constructor(
    private val api: KavachApiService,
) : DeepCheckRepository {

    override suspend fun createSession(
        audioFile: File,
        elderId: String,
        incidentId: String?,
    ): NetworkResult<DeepCheckSession> {
        val audioPart = MultipartBody.Part.createFormData(
            name     = "audio_file",
            filename = audioFile.name,
            body     = audioFile.asRequestBody("audio/ogg".toMediaTypeOrNull()),
        )
        val elderIdBody    = elderId.toRequestBody("text/plain".toMediaTypeOrNull())
        val incidentIdBody = incidentId?.toRequestBody("text/plain".toMediaTypeOrNull())

        val result = safeApiCall {
            api.createDeepCheckSession(audioPart, elderIdBody, incidentIdBody)
        }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toDomain())
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }

    override suspend fun pollSession(sessionId: String): NetworkResult<DeepCheckSession> {
        val result = safeApiCall { api.getDeepCheckSession(sessionId) }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toDomain())
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }

    private fun com.kavach.app.data.remote.dto.DeepCheckSessionDto.toDomain() = DeepCheckSession(
        sessionId      = sessionId,
        status         = DeepCheckStatus.fromString(status),
        transcript     = transcript,
        redFlags       = redFlags ?: emptyList(),
        spoofScore     = spoofScore,
        assistiveOnly  = assistiveOnly ?: true,
        spoofDisclaimer = spoofDisclaimer,
        summary        = summary,
        confidence     = confidence,
    )
}
