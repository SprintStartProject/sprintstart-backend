package com.sprintstart.sprintstartbackend.onboarding.model.response.history

import com.sprintstart.sprintstartbackend.onboarding.model.entity.GithubHistoryPrior
import java.time.Instant

/**
 * Everything held about a user's prior repository work — the complete record, by design.
 *
 * The user is shown exactly this, so opting in is not a black box: only counted, namespaced
 * buckets (`repo:owner/name`, `type:PULL_REQUEST`, `label:bug`), never the content of the work
 * itself.
 */
data class GithubHistoryPriorResponse(
    val consented: Boolean,
    val signals: Map<String, Int> = emptyMap(),
    val computedAt: Instant? = null,
) {
    companion object {
        fun from(prior: GithubHistoryPrior): GithubHistoryPriorResponse =
            GithubHistoryPriorResponse(
                consented = true,
                signals = prior.signals.toMap(),
                computedAt = prior.computedAt,
            )

        /** No consent on record: nothing is derived, so there is nothing to show. */
        fun notConsented(): GithubHistoryPriorResponse = GithubHistoryPriorResponse(consented = false)
    }
}
