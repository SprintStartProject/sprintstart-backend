package com.sprintstart.sprintstartbackend.insights.model.dto.request

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Which questions a manual FAQ rebuild should regroup.
 *
 * Both bounds are optional and combine: the time window narrows the set, the limit then takes the
 * newest of what is left. Neither can widen the configured ceiling — the scope is a way to ask for
 * *less* than everything, never for more than the service is willing to send.
 *
 * The scope matters because a rebuild replaces the FAQ: whatever it does not cover is gone from
 * the counts afterwards. That is a PM's decision to make deliberately, which is why it is a
 * parameter rather than a default.
 *
 * @property questionLimit at most this many questions, newest first
 * @property sinceMonths only questions asked within roughly this many months
 */
data class FaqRebuildScope(
    val questionLimit: Int? = null,
    val sinceMonths: Int? = null,
) {
    init {
        // Rejected rather than coerced: a caller asking for zero or fewer questions has a bug,
        // and quietly rebuilding from something else would hide it behind a wiped FAQ.
        reject(questionLimit, "questionLimit")
        reject(sinceMonths, "sinceMonths")
    }

    private fun reject(value: Int?, name: String) {
        if (value != null && value < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$name must be at least 1")
        }
    }

    companion object {
        /** Everything the service is willing to send. */
        val EVERYTHING = FaqRebuildScope()
    }
}
