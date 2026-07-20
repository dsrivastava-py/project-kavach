package com.kavach.app.data.repository

import com.kavach.app.data.local.dao.PlanCacheDao
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.PlanDto
import com.kavach.app.data.remote.dto.PlansResponse
import com.kavach.app.utils.NetworkResult
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class BillingRepositoryTest {

    private lateinit var api: KavachApiService
    private lateinit var dao: PlanCacheDao
    private lateinit var repository: BillingRepositoryImpl

    private val samplePlans = listOf(
        PlanDto("free", "Free", 0, null, listOf("Basic")),
        PlanDto("family_99", "Family", 99, "monthly", listOf("Advanced")),
    )

    @Before
    fun setUp() {
        api = mockk()
        dao = mockk(relaxed = true)
        coEvery { dao.observeAll() } returns flowOf(emptyList())
        repository = BillingRepositoryImpl(api, dao)
    }

    @Test
    fun `refreshPlans returns Success and upserts to dao`() = runTest {
        coEvery { api.getBillingPlans() } returns Response.success(PlansResponse(samplePlans))

        val result = repository.refreshPlans()

        assertTrue(result is NetworkResult.Success)
        assertEquals(2, (result as NetworkResult.Success).data.size)
        coVerify { dao.clearAll() }
        coVerify { dao.upsertAll(any()) }
    }

    @Test
    fun `refreshPlans returns Error on HTTP 500`() = runTest {
        coEvery { api.getBillingPlans() } returns
            Response.error(500, "".toResponseBody())

        val result = repository.refreshPlans()

        assertTrue(result is NetworkResult.Error)
        assertEquals(500, (result as NetworkResult.Error).code)
    }
}
