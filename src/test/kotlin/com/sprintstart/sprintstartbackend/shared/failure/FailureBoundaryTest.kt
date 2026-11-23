package com.sprintstart.sprintstartbackend.shared.failure

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FailureBoundaryTest {
    private val failure = IllegalStateException("boom")

    @Test
    fun `reportingFailure returns the result when nothing fails`() {
        var reported = false

        val result = reportingFailure(onFailure = { reported = true }) { 7 }

        assertEquals(7, result)
        assertTrue(!reported)
    }

    @Test
    fun `reportingFailure reports a failure and rethrows the same one`() {
        var reported: Exception? = null

        val thrown = assertThrows<IllegalStateException> {
            reportingFailure(onFailure = { reported = it }) { throw failure }
        }

        assertSame(failure, thrown)
        assertSame(failure, reported)
    }

    @Test
    fun `reportingFailure rethrows a cancellation without reporting it`() {
        var reported = false

        assertThrows<CancellationException> {
            reportingFailure(onFailure = { reported = true }) { throw CancellationException("stopped") }
        }

        assertTrue(!reported)
    }

    @Test
    fun `recovering returns the fallback for a failure`() {
        val result = recovering(fallback = { it.message }) { throw failure }

        assertEquals("boom", result)
    }

    @Test
    fun `recovering returns the result when nothing fails`() {
        assertEquals("ok", recovering(fallback = { "fallback" }) { "ok" })
    }

    @Test
    fun `recovering rethrows a cancellation instead of absorbing it`() {
        assertThrows<CancellationException> {
            recovering(fallback = { "fallback" }) { throw CancellationException("stopped") }
        }
    }

    @Test
    fun `afterFailure cleans up and rethrows a failure`() {
        var cleaned = 0

        val thrown = assertThrows<IllegalStateException> {
            afterFailure(cleanup = { cleaned++ }) { throw failure }
        }

        assertSame(failure, thrown)
        assertEquals(1, cleaned)
    }

    @Test
    fun `afterFailure cleans up and rethrows a cancellation too`() {
        var cleaned = 0

        assertThrows<CancellationException> {
            afterFailure(cleanup = { cleaned++ }) { throw CancellationException("stopped") }
        }

        assertEquals(1, cleaned)
    }

    @Test
    fun `translatingFailure throws the translated failure with the original as its cause`() {
        val thrown = assertThrows<UnsupportedOperationException> {
            translatingFailure(translate = { UnsupportedOperationException("wrapped", it) }) { throw failure }
        }

        assertSame(failure, thrown.cause)
    }

    @Test
    fun `translatingFailure translates a cancellation too`() {
        assertThrows<UnsupportedOperationException> {
            translatingFailure(translate = { UnsupportedOperationException("wrapped", it) }) {
                throw CancellationException("stopped")
            }
        }
    }

    @Test
    fun `translatingFailure returns the result when nothing fails`() {
        assertEquals(3, translatingFailure(translate = { it }) { 3 })
    }

    @Test
    fun `afterFailure does not clean up when nothing fails`() {
        var cleaned = 0

        afterFailure(cleanup = { cleaned++ }) { 1 }

        assertEquals(0, cleaned)
    }
}
