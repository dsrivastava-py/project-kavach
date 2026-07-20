package com.kavach.app.utils

import org.junit.Assert.*
import org.junit.Test

class NetworkResultTest {

    @Test
    fun `Success isSuccess returns true`() {
        val result: NetworkResult<Int> = NetworkResult.Success(42)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `Error isSuccess returns false`() {
        val result: NetworkResult<Int> = NetworkResult.Error("fail")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `Loading isSuccess returns false`() {
        val result: NetworkResult<Int> = NetworkResult.Loading
        assertFalse(result.isSuccess)
    }

    @Test
    fun `dataOrNull returns data for Success`() {
        val result: NetworkResult<String> = NetworkResult.Success("hello")
        assertEquals("hello", result.dataOrNull)
    }

    @Test
    fun `dataOrNull returns null for Error`() {
        val result: NetworkResult<String> = NetworkResult.Error("oops")
        assertNull(result.dataOrNull)
    }

    @Test
    fun `dataOrNull returns null for Loading`() {
        val result: NetworkResult<String> = NetworkResult.Loading
        assertNull(result.dataOrNull)
    }
}
