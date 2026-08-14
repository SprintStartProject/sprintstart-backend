package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredArtifact
import com.sprintstart.sprintstartbackend.onboarding.model.entity.GithubHistoryPrior
import com.sprintstart.sprintstartbackend.onboarding.repository.GithubHistoryPriorRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Builds and stores the prior derived from a user's existing work in their projects' repositories.
 *
 * Consent-gated end to end: nothing is derived, stored or read without
 * `User.githubSeedingConsentAt`, and revoking deletes the derived row outright rather than merely
 * hiding it. The source is only what the project has *already ingested* -- no GitHub call, nothing
 * from outside the connected repositories, and no personal profile activity.
 *
 * Only issues and pull requests can contribute, because only those carry a GitHub author (a commit
 * carries a git author *name*, which is not an account). The result is therefore a picture of
 * *where and how much* somebody has been involved, not a claim about which languages they know.
 */
@Service
class GithubHistoryPriorService(
    private val githubHistoryPriorRepository: GithubHistoryPriorRepository,
    private val artifactIngestionApi: ArtifactIngestionApi,
    private val userApi: UserApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Grants consent and computes the prior immediately, so the user can see what was inferred
     * right after opting in rather than being told it will appear later.
     *
     * @return The freshly computed prior; its signals are empty when the user has authored nothing
     * in the connected repositories (or has no GitHub login yet), which is a valid outcome, not an
     * error.
     */
    @Transactional
    fun grantConsent(userId: UUID): GithubHistoryPrior {
        userApi.setGithubSeedingConsent(userId, Instant.now())
        return recompute(userId)
    }

    /**
     * Withdraws consent and deletes the derived prior.
     *
     * Any placement already made from it stays: the user earned that, and un-earning it would
     * punish them for changing their mind (the same "never un-earns progress" rule the ledger
     * follows).
     */
    @Transactional
    fun revokeConsent(userId: UUID) {
        userApi.setGithubSeedingConsent(userId, null)
        githubHistoryPriorRepository.deleteById(userId)
    }

    /**
     * Returns the stored prior, or `null` when the user has not consented (or nothing was derived).
     */
    @Transactional(readOnly = true)
    fun getPrior(userId: UUID): GithubHistoryPrior? {
        if (userApi.getGithubSeedingContext(userId)?.seedingConsentAt == null) {
            return null
        }
        return githubHistoryPriorRepository.findByIdOrNull(userId)
    }

    /**
     * Recomputes the prior from the artifacts the user authored in the projects they belong to.
     *
     * Safe to call repeatedly: the row is replaced, never accumulated, so a crawl that removed
     * artifacts shrinks the prior instead of leaving stale counts behind.
     */
    @Transactional
    fun recompute(userId: UUID): GithubHistoryPrior {
        val prior = githubHistoryPriorRepository.findByIdOrNull(userId) ?: GithubHistoryPrior(userId = userId)
        prior.signals.clear()
        prior.computedAt = Instant.now()

        val context = userApi.getGithubSeedingContext(userId)
        val githubLogin = context?.githubLogin
        if (githubLogin.isNullOrBlank()) {
            // Nothing to attribute work to yet. Stored as an empty prior rather than skipped, so
            // "we looked and found nothing" is distinguishable from "we never looked".
            logger.info("No GitHub login for user {}, storing an empty history prior", userId)
            return githubHistoryPriorRepository.save(prior)
        }

        val authored = context.projectIds.flatMap { projectId ->
            artifactIngestionApi.getAuthoredWork(projectId, githubLogin)
        }
        prior.signals.putAll(toSignals(authored))

        return githubHistoryPriorRepository.save(prior)
    }

    /**
     * Reduces authored artifacts to counted, namespaced buckets.
     *
     * Only aggregate counts survive -- what somebody wrote never enters the prior, only that they
     * were involved, where, and with what kind of work.
     */
    private fun toSignals(authored: List<AuthoredArtifact>): Map<String, Int> {
        val signals = mutableMapOf<String, Int>()

        authored.forEach { artifact ->
            signals.merge("type:${artifact.artifactType}", 1, Int::plus)
            artifact.repositoryFullName?.let { signals.merge("repo:$it", 1, Int::plus) }
            artifact.labels.forEach { label -> signals.merge("label:$label", 1, Int::plus) }
        }

        return signals
    }
}
