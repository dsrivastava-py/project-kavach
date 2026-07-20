package com.kavach.app.data.repository

import com.kavach.app.data.local.dao.SignalEventQueueDao
import com.kavach.app.data.local.entity.SignalEventQueueEntity
import com.kavach.app.data.remote.api.SignalApiService
import com.kavach.app.data.remote.dto.SignalIngestResponse
import com.kavach.app.utils.NetworkResult
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class SignalRepositoryTest {

    private lateinit var api: SignalApiService
    private lateinit var dao: SignalEventQueueDao
    private lateinit var repository: SignalRepositoryImpl

    private val pendingEvent = SignalEventQueueEntity(
        localId    = 1L,
        deviceId   = "device-001",
        elderId    = "elder-001",
        eventType  = "call_start",
        occurredAt = "2024-01-01T10:00:00Z",
    )

    @Before
    fun setUp() {
        api = mockk()
        dao = mockk(relaxed = true)
        repository = SignalRepositoryImpl(api, dao)
    }

    @Test
    fun `flushPendingEvents returns Success(0) when queue is empty`() = runTest {
        coEvery { dao.getPending(any()) } returns emptyList()

        val result = repository.flushPendingEvents("device-001", "elder-001")

        assertTrue(result is NetworkResult.Success)
        assertEquals(0, (result as NetworkResult.Success).data.ingested)
        coVerify(exactly = 0) { api.ingestSignals(any()) }
    }

    @Test
    fun `flushPendingEvents uploads and marks uploaded on success`() = runTest {
        coEvery { dao.getPending(any()) } returns listOf(pendingEvent)
        coEvery { api.ingestSignals(any()) } returns
            Response.success(SignalIngestResponse(ingested = 1, taskId = "task-abc"))

        val result = repository.flushPendingEvents("device-001", "elder-001")

        assertTrue(result is NetworkResult.Success)
        assertEquals(1, (result as NetworkResult.Success).data.ingested)
        coVerify { dao.markUploaded(listOf(1L)) }
        coVerify { dao.purgeUploaded() }
    }

    @Test
    fun `flushPendingEvents returns Error and does not purge on API failure`() = runTest {
        coEvery { dao.getPending(any()) } returns listOf(pendingEvent)
        coEvery { api.ingestSignals(any()) } returns
            Response.error(401, "".toResponseBody())

        val result = repository.flushPendingEvents("device-001", "elder-001")

        assertTrue(result is NetworkResult.Error)
        assertEquals(401, (result as NetworkResult.Error).code)
        coVerify(exactly = 0) { dao.markUploaded(any()) }
        coVerify(exactly = 0) { dao.purgeUploaded() }
    }

    @Test
    fun `pendingEventCount delegates to dao`() = runTest {
        coEvery { dao.pendingCount() } returns 5
        assertEquals(5, repository.pendingEventCount())
    }
}
