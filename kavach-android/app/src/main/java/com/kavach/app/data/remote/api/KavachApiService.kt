package com.kavach.app.data.remote.api

import com.kavach.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Primary Retrofit interface for all JWT-authenticated (and public) endpoints.
 * Bearer token is attached automatically by [AuthInterceptor].
 *
 * All paths are relative to BASE_URL from BuildConfig.
 * All functions are suspending — called from coroutine scope in Repository.
 */
interface KavachApiService {

    // ── Health ────────────────────────────────────────────────

    /** Public health probe — no auth required. */
    @GET("api/v1/health")
    suspend fun getHealth(): Response<HealthDto>

    // ── Auth / Pairing ────────────────────────────────────────

    /**
     * Pair a guardian using a short-lived Redis pairing code.
     * No JWT required — issues a JWT on success.
     */
    @POST("api/v1/guardians/pair")
    suspend fun pairGuardian(
        @Body request: PairGuardianRequest,
    ): Response<PairGuardianResponse>

    /**
     * Elder generates a 6-digit pairing code.
     * Requires JWT with role=elder.
     */
    @POST("api/v1/guardians/generate-pairing-code")
    suspend fun generatePairingCode(): Response<PairingCodeResponse>

    // ── Incidents ─────────────────────────────────────────────

    /**
     * Resolve or mark an incident as false positive.
     * Requires JWT with role=guardian.
     */
    @POST("api/v1/incidents/{incident_id}/resolve")
    suspend fun resolveIncident(
        @Path("incident_id") incidentId: String,
        @Body request: ResolveIncidentRequest,
    ): Response<ResolveIncidentResponse>

    /**
     * Generate evidence PDF for an incident.
     * Requires JWT with role=guardian or investigator.
     */
    @POST("api/v1/incidents/{incident_id}/evidence")
    suspend fun generateEvidence(
        @Path("incident_id") incidentId: String,
    ): Response<EvidenceResponse>

    // ── Deep Check ────────────────────────────────────────────

    /**
     * Start a deep-check session. Upload audio via multipart/form-data.
     * Returns 202 Accepted with session_id — poll GET endpoint for results.
     */
    @Multipart
    @POST("api/v1/deepcheck/sessions")
    suspend fun createDeepCheckSession(
        @Part audio: MultipartBody.Part,
        @Part("elder_id") elderId: RequestBody,
        @Part("incident_id") incidentId: RequestBody? = null,
    ): Response<DeepCheckSessionDto>

    /**
     * Poll the status of a deep-check session.
     * Poll every 3 seconds; max 40 polls (2 minutes total).
     */
    @GET("api/v1/deepcheck/sessions/{session_id}")
    suspend fun getDeepCheckSession(
        @Path("session_id") sessionId: String,
    ): Response<DeepCheckSessionDto>

    // ── Fraud Graph ───────────────────────────────────────────

    /**
     * Get the mule-ring subgraph for a phone number.
     * Requires JWT with role=investigator.
     */
    @GET("api/v1/graph/ring/{phone}")
    suspend fun getMuleRing(
        @Path("phone") phone: String,
        @Query("depth") depth: Int = 3,
    ): Response<RingSubgraphDto>

    // ── Billing ───────────────────────────────────────────────

    /** Public — returns static plan metadata. No auth required. */
    @GET("api/v1/billing/plans")
    suspend fun getBillingPlans(): Response<PlansResponse>
}
