package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketAccount
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketLink
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketLinks
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMemberResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMembersResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMetadataResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketWorkspace
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketWorkspaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

class BitbucketWorkspaceServiceTest {
    private val bitbucketClient = mockk<BitbucketClient>()
    private val workspaceRepository = mockk<BitbucketWorkspaceRepository>()
    private val credentialApi = mockk<AtlassianCredentialApi>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = BitbucketWorkspaceService(bitbucketClient, workspaceRepository, credentialApi, eventPublisher)

    private val transactionId = UUID.randomUUID()

    @Test
    fun `connectWorkspaceIfNecessary publishes started and completed events around the fetch`() = runTest {
        stubFetchableWorkspace()

        service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, transactionId)

        verify { eventPublisher.publishEvent(BitbucketWorkspaceMetadataFetchingStartedEvent(transactionId)) }
        verify { eventPublisher.publishEvent(match<Any> { it is BitbucketWorkspaceMetadataFetchedEvent }) }
        verify { eventPublisher.publishEvent(BitbucketWorkspaceMetadataFetchingCompletedEvent(transactionId)) }
    }

    @Test
    fun `connectWorkspaceIfNecessary maps fetched data into the fetched event`() = runTest {
        val published = mutableListOf<Any>()
        every { eventPublisher.publishEvent(capture(published)) } just runs
        stubFetchableWorkspace()

        service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, transactionId)

        val event = published.filterIsInstance<BitbucketWorkspaceMetadataFetchedEvent>().single()
        assertThat(event.transactionId).isEqualTo(transactionId)
        assertThat(event.workspace).isEqualTo(WORKSPACE)
        assertThat(event.uuid).isEqualTo("{ws-uuid}")
        assertThat(event.name).isEqualTo("SprintStart")
        assertThat(event.isPrivate).isTrue()
        assertThat(event.createdOn).isEqualTo("2024-01-01T00:00:00Z")
        assertThat(event.url).isEqualTo("https://bitbucket.org/sprintstart")
        assertThat(event.members).hasSize(2)
        assertThat(event.members[0].accountId).isEqualTo("{account-1}")
        assertThat(event.members[0].displayName).isEqualTo("Alice")
        assertThat(event.members[1].accountId).isNull()
    }

    @Test
    fun `connectWorkspaceIfNecessary publishes failed event and rethrows on fetch failure`() = runTest {
        val published = mutableListOf<Any>()
        every { eventPublisher.publishEvent(capture(published)) } just runs
        every { workspaceRepository.findById(WORKSPACE) } returns Optional.empty()
        every { credentialApi.findSecret(AUTH_ID, CREDENTIAL_NAME) } returns credential()
        coEvery { bitbucketClient.fetchWorkspaceMetadata(WORKSPACE, credential()) } throws RuntimeException("boom")

        assertFailsWith<RuntimeException> {
            service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, transactionId)
        }

        val failed = published.filterIsInstance<BitbucketWorkspaceMetadataFetchingFailedEvent>().single()
        assertThat(failed.transactionId).isEqualTo(transactionId)
        assertThat(failed.reason).isEqualTo("boom")
        assertThat(published.filterIsInstance<BitbucketWorkspaceMetadataFetchingCompletedEvent>()).isEmpty()
    }

    @Test
    fun `connectWorkspaceIfNecessary skips the fetch when a fresh fetch is stored`() = runTest {
        val published = mutableListOf<Any>()
        every { eventPublisher.publishEvent(capture(published)) } just runs
        every { workspaceRepository.findById(WORKSPACE) } returns Optional.of(freshMarker())

        service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, transactionId)

        assertThat(published.filterIsInstance<BitbucketWorkspaceMetadataFetchedEvent>()).isEmpty()
        assertThat(published).contains(
            BitbucketWorkspaceMetadataFetchingStartedEvent(transactionId),
            BitbucketWorkspaceMetadataFetchingCompletedEvent(transactionId),
        )
        coVerify(exactly = 0) { bitbucketClient.fetchWorkspaceMetadata(any(), any()) }
        coVerify(exactly = 0) { bitbucketClient.getWorkspaceMembers(any(), any()) }
    }

    @Test
    fun `connectWorkspaceIfNecessary saves the fetch time with the workspace record`() = runTest {
        stubFetchableWorkspace()
        val saved = slot<BitbucketWorkspace>()
        every { workspaceRepository.save(capture(saved)) } answers { saved.captured }

        service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, transactionId)

        assertThat(saved.captured.slug).isEqualTo(WORKSPACE)
        assertThat(saved.captured.name).isEqualTo("SprintStart")
        assertThat(saved.captured.fetchedAt).isNotNull()
    }

    @Test
    fun `connectWorkspaceIfNecessary refetches a workspace whose stored fetch went stale`() = runTest {
        val published = mutableListOf<Any>()
        every { eventPublisher.publishEvent(capture(published)) } just runs
        every { workspaceRepository.findById(WORKSPACE) } returns Optional.of(staleMarker())
        every { credentialApi.findSecret(any(), any()) } returns credential()
        every { workspaceRepository.save(any()) } answers { firstArg() }
        coEvery { bitbucketClient.fetchWorkspaceMetadata(WORKSPACE, credential()) } returns workspaceMetadata()
        coEvery { bitbucketClient.getWorkspaceMembers(WORKSPACE, credential()) } returns workspaceMembers()

        service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, transactionId)

        assertThat(published.filterIsInstance<BitbucketWorkspaceMetadataFetchedEvent>()).hasSize(1)
    }

    @Test
    fun `connectWorkspaceIfNecessary refetches a workspace with no stored fetch time`() = runTest {
        val published = mutableListOf<Any>()
        every { eventPublisher.publishEvent(capture(published)) } just runs
        every { workspaceRepository.findById(WORKSPACE) } returns
            Optional.of(BitbucketWorkspace(slug = WORKSPACE, name = "SprintStart", fetchedAt = null))
        every { credentialApi.findSecret(any(), any()) } returns credential()
        every { workspaceRepository.save(any()) } answers { firstArg() }
        coEvery { bitbucketClient.fetchWorkspaceMetadata(WORKSPACE, credential()) } returns workspaceMetadata()
        coEvery { bitbucketClient.getWorkspaceMembers(WORKSPACE, credential()) } returns workspaceMembers()

        service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, transactionId)

        assertThat(published.filterIsInstance<BitbucketWorkspaceMetadataFetchedEvent>()).hasSize(1)
    }

    /**
     * Parallel repository connects of one workspace must pay the API calls once: the first
     * coroutine through the lock fetches and marks, and every later one re-checks inside the lock
     * and finds fresh metadata.
     */
    @Test
    fun `parallel connects of one workspace fetch it only once`() = runTest {
        val markers = mutableMapOf<String, BitbucketWorkspace>()
        every { workspaceRepository.findById(WORKSPACE) } answers { Optional.ofNullable(markers[WORKSPACE]) }
        every { workspaceRepository.save(any()) } answers {
            firstArg<BitbucketWorkspace>().also { markers[it.slug] = it }
        }
        every { credentialApi.findSecret(any(), any()) } returns credential()
        coEvery { bitbucketClient.fetchWorkspaceMetadata(WORKSPACE, credential()) } returns workspaceMetadata()
        coEvery { bitbucketClient.getWorkspaceMembers(WORKSPACE, credential()) } returns workspaceMembers()

        val first =
            launch { service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, UUID.randomUUID()) }
        val second =
            launch { service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, UUID.randomUUID()) }
        joinAll(first, second)

        coVerify(exactly = 1) { bitbucketClient.fetchWorkspaceMetadata(WORKSPACE, credential()) }
    }

    @Test
    fun `connectWorkspaceIfNecessary refuses to fetch with an unknown credential`() = runTest {
        every { workspaceRepository.findById(WORKSPACE) } returns Optional.empty()
        every { credentialApi.findSecret(AUTH_ID, CREDENTIAL_NAME) } returns null

        assertFailsWith<AtlassianCredentialNotFoundException> {
            service.connectWorkspaceIfNecessary(WORKSPACE, AUTH_ID, CREDENTIAL_NAME, transactionId)
        }

        coVerify(exactly = 0) { bitbucketClient.fetchWorkspaceMetadata(any(), any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<BitbucketWorkspaceMetadataFetchingFailedEvent> { it.reason != null })
        }
    }

    private fun stubFetchableWorkspace() {
        every { workspaceRepository.findById(any()) } returns Optional.empty()
        every { credentialApi.findSecret(any(), any()) } returns credential()
        every { workspaceRepository.save(any()) } answers { firstArg() }
        coEvery { bitbucketClient.fetchWorkspaceMetadata(WORKSPACE, credential()) } returns workspaceMetadata()
        coEvery { bitbucketClient.getWorkspaceMembers(WORKSPACE, credential()) } returns workspaceMembers()
    }

    private fun freshMarker() = BitbucketWorkspace(
        slug = WORKSPACE,
        name = "SprintStart",
        fetchedAt = Instant.now(),
    )

    private fun staleMarker() = BitbucketWorkspace(
        slug = WORKSPACE,
        name = "SprintStart",
        fetchedAt = Instant.now().minusSeconds(25 * 60 * 60),
    )

    private fun credential() = AtlassianCredentialSecret(userEmail = "user@example.com", apiToken = TOKEN)

    private fun workspaceMetadata() = WorkspaceMetadataResponse(
        uuid = "{ws-uuid}",
        slug = WORKSPACE,
        name = "SprintStart",
        isPrivate = true,
        createdOn = "2024-01-01T00:00:00Z",
        links = BitbucketLinks(html = BitbucketLink(href = "https://bitbucket.org/sprintstart")),
    )

    private fun workspaceMembers() = WorkspaceMembersResponse(
        members = listOf(
            WorkspaceMemberResponse(user = BitbucketAccount(accountId = "{account-1}", displayName = "Alice")),
            WorkspaceMemberResponse(user = BitbucketAccount(nickname = "bob")),
        ),
    )

    private companion object {
        const val WORKSPACE = "sprintstart"
        const val AUTH_ID = "auth-id"
        const val CREDENTIAL_NAME = "team-token"
        const val TOKEN = "test-token"
    }
}
