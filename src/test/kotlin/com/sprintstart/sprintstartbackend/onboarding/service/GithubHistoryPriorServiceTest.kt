package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredArtifact
import com.sprintstart.sprintstartbackend.onboarding.model.entity.GithubHistoryPrior
import com.sprintstart.sprintstartbackend.onboarding.repository.GithubHistoryPriorRepository
import com.sprintstart.sprintstartbackend.user.external.GithubSeedingContext
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The prior is consent-gated and derived only from artifacts a project has already ingested, so
 * these tests are as much about what is *not* read or kept as about what is.
 */
class GithubHistoryPriorServiceTest {
    private val githubHistoryPriorRepository: GithubHistoryPriorRepository = mockk()
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val userApi: UserApi = mockk(relaxed = true)

    private val service = GithubHistoryPriorService(
        githubHistoryPriorRepository,
        artifactIngestionApi,
        userApi,
    )

    private val userId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { githubHistoryPriorRepository.findById(userId) } returns Optional.empty()
        every { githubHistoryPriorRepository.save(any()) } answers { firstArg() }
        every { userApi.getGithubSeedingContext(userId) } returns GithubSeedingContext(
            githubLogin = "octocat",
            projectIds = setOf(projectId),
            seedingConsentAt = Instant.now(),
        )
    }

    @Test
    fun `derives counted buckets from the work a user authored`() {
        every { artifactIngestionApi.getAuthoredWork(projectId, "octocat") } returns listOf(
            AuthoredArtifact("PULL_REQUEST", "owner/repo", emptyList()),
            AuthoredArtifact("PULL_REQUEST", "owner/repo", emptyList()),
            AuthoredArtifact("ISSUE", "owner/repo", listOf("bug", "good first issue")),
        )

        val prior = service.recompute(userId)

        assertEquals(2, prior.signals["type:PULL_REQUEST"])
        assertEquals(1, prior.signals["type:ISSUE"])
        assertEquals(3, prior.signals["repo:owner/repo"])
        assertEquals(1, prior.signals["label:bug"])
    }

    @Test
    fun `stores an empty prior when the user has no GitHub login yet`() {
        every { userApi.getGithubSeedingContext(userId) } returns GithubSeedingContext(
            githubLogin = null,
            projectIds = setOf(projectId),
            seedingConsentAt = Instant.now(),
        )

        val prior = service.recompute(userId)

        // "We looked and found nothing" must be distinguishable from "we never looked".
        assertTrue(prior.signals.isEmpty())
        verify(exactly = 0) { artifactIngestionApi.getAuthoredWork(any(), any()) }
    }

    @Test
    fun `replaces previous signals instead of accumulating them`() {
        val existing = GithubHistoryPrior(userId = userId, signals = mutableMapOf("repo:old/repo" to 9))
        every { githubHistoryPriorRepository.findById(userId) } returns Optional.of(existing)
        every { artifactIngestionApi.getAuthoredWork(projectId, "octocat") } returns listOf(
            AuthoredArtifact("ISSUE", "owner/repo", emptyList()),
        )

        val prior = service.recompute(userId)

        // A crawl that removed artifacts must shrink the prior, not leave stale counts behind.
        assertNull(prior.signals["repo:old/repo"])
        assertEquals(1, prior.signals["repo:owner/repo"])
    }

    @Test
    fun `only reads projects the user actually belongs to`() {
        val otherProject = UUID.randomUUID()
        every { artifactIngestionApi.getAuthoredWork(projectId, "octocat") } returns emptyList()

        service.recompute(userId)

        verify(exactly = 0) { artifactIngestionApi.getAuthoredWork(otherProject, any()) }
    }

    @Test
    fun `granting consent records it and derives the prior in one step`() {
        every { artifactIngestionApi.getAuthoredWork(projectId, "octocat") } returns listOf(
            AuthoredArtifact("ISSUE", "owner/repo", emptyList()),
        )

        val prior = service.grantConsent(userId)

        verify { userApi.setGithubSeedingConsent(userId, any<Instant>()) }
        assertEquals(1, prior.signals["type:ISSUE"])
    }

    @Test
    fun `revoking consent deletes the derived prior, not just the flag`() {
        every { githubHistoryPriorRepository.deleteById(userId) } returns Unit

        service.revokeConsent(userId)

        verify { userApi.setGithubSeedingConsent(userId, null) }
        verify { githubHistoryPriorRepository.deleteById(userId) }
    }

    @Test
    fun `reads nothing back without consent on record`() {
        every { userApi.getGithubSeedingContext(userId) } returns GithubSeedingContext(
            githubLogin = "octocat",
            projectIds = setOf(projectId),
            seedingConsentAt = null,
        )

        assertNull(service.getPrior(userId))
        verify(exactly = 0) { githubHistoryPriorRepository.findById(userId) }
    }
}
