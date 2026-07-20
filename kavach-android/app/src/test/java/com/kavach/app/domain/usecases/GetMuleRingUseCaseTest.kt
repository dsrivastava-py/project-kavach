package com.kavach.app.domain.usecases

import com.kavach.app.domain.model.RingSubgraph
import com.kavach.app.domain.repository.GraphRepository
import com.kavach.app.domain.usecases.graph.GetMuleRingUseCase
import com.kavach.app.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GetMuleRingUseCaseTest {

    private lateinit var graphRepository: GraphRepository
    private lateinit var useCase: GetMuleRingUseCase

    @Before
    fun setUp() {
        graphRepository = mockk()
        useCase = GetMuleRingUseCase(graphRepository)
    }

    @Test
    fun `returns error for blank phone`() = runTest {
        val result = useCase("", 3)
        assertTrue(result is NetworkResult.Error)
        assertEquals("Phone number is required", (result as NetworkResult.Error).message)
    }

    @Test
    fun `returns error for depth 0`() = runTest {
        val result = useCase("+91999", 0)
        assertTrue(result is NetworkResult.Error)
        assertEquals("Depth must be between 1 and 6", (result as NetworkResult.Error).message)
    }

    @Test
    fun `returns error for depth 7`() = runTest {
        val result = useCase("+91999", 7)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `delegates to repository for valid input`() = runTest {
        val graph = RingSubgraph(emptyList(), emptyList(), 0, 0)
        coEvery { graphRepository.getMuleRing("+919876543210", 3) } returns NetworkResult.Success(graph)

        val result = useCase("+919876543210", 3)

        assertTrue(result is NetworkResult.Success)
    }
}
