package com.sprintstart.sprintstartbackend.config

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor

internal class CoroutineConfigTest {
    private val config = CoroutineConfig()

    @Test
    fun `blocking work runs on the IO dispatcher`() {
        assertSame(Dispatchers.IO, config.ioDispatcher())
    }

    @Test
    fun `the application scope runs on the dispatcher it is given`() {
        val scope = config.applicationScope(Dispatchers.Unconfined)

        assertSame(Dispatchers.Unconfined, scope.coroutineContext[ContinuationInterceptor])
    }

    @Test
    fun `a failing job does not stop the next one`() {
        val scope = config.applicationScope(Dispatchers.Unconfined)
        var ranAfterFailure = false

        scope.launch(CoroutineExceptionHandler { _, _ -> }) { error("background job failed") }
        scope.launch { ranAfterFailure = true }

        assertTrue(ranAfterFailure)
    }
}
