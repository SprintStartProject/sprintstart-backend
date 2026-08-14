package com.sprintstart.sprintstartbackend.connectors.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMemberResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMembersResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMetadataResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.Member
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.MemberConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PageInfo
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.Team
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.TeamOrganization
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID
import kotlin.test.assertFailsWith

class GithubOrgServiceTest {
    private val githubClient = mockk<GithubClient>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = GithubOrgService(githubClient, eventPublisher)
    private val transactionId = UUID.randomUUID()

    @Test
    fun `connectGithubOrgIfNecessary publishes started and completed events around the fetch`() = runTest {
        coEvery { githubClient.fetchOrgMetadata("octocat", "token") } returns orgMetadata()
        coEvery { githubClient.getOrgTeams("octocat", "token") } returns emptyList()
        coEvery { githubClient.getOrgMembers("octocat", "token") } returns OrgMembersResponse(emptyList())

        service.connectGithubOrgIfNecessary("octocat", "token", transactionId)

        verify { eventPublisher.publishEvent(GithubOrgMetadataFetchingStartedEvent(transactionId)) }
        verify { eventPublisher.publishEvent(any<GithubOrgMetadataFetchedEvent>()) }
        verify { eventPublisher.publishEvent(GithubOrgMetadataFetchingCompletedEvent(transactionId)) }
    }

    @Test
    fun `connectGithubOrgIfNecessary maps fetched data into the fetched event`() = runTest {
        val published = mutableListOf<Any>()
        every { eventPublisher.publishEvent(capture(published)) } just runs
        coEvery { githubClient.fetchOrgMetadata("octocat", "token") } returns orgMetadata()
        coEvery { githubClient.getOrgTeams("octocat", "token") } returns listOf(platformTeam())
        coEvery {
            githubClient.getOrgMembers("octocat", "token")
        } returns OrgMembersResponse(listOf(OrgMemberResponse("bob", "https://github.com/bob")))

        service.connectGithubOrgIfNecessary("octocat", "token", transactionId)

        val event = published.filterIsInstance<GithubOrgMetadataFetchedEvent>().single()
        assertThat(event.transactionId).isEqualTo(transactionId)
        assertThat(event.login).isEqualTo("octocat")
        assertThat(event.name).isEqualTo("The Octocats")
        assertThat(event.publicRepos).isEqualTo(12)
        assertThat(event.privateRepos).isEqualTo(4)
        assertThat(event.teams).hasSize(1)
        assertThat(event.teams!![0].members[0].login).isEqualTo("alice")
        assertThat(event.members[0].login).isEqualTo("bob")
        assertThat(event.members[0].url).isEqualTo("https://github.com/bob")
    }

    @Test
    fun `connectGithubOrgIfNecessary publishes failed event and rethrows on fetch failure`() = runTest {
        val published = mutableListOf<Any>()
        every { eventPublisher.publishEvent(capture(published)) } just runs
        coEvery { githubClient.fetchOrgMetadata("octocat", "token") } throws RuntimeException("boom")

        assertFailsWith<RuntimeException> { service.connectGithubOrgIfNecessary("octocat", "token", transactionId) }

        val failed = published.filterIsInstance<GithubOrgMetadataFetchingFailedEvent>().single()
        assertThat(failed.transactionId).isEqualTo(transactionId)
        assertThat(failed.reason).isEqualTo("boom")
        assertThat(published.filterIsInstance<GithubOrgMetadataFetchingCompletedEvent>()).isEmpty()
    }

    private fun orgMetadata() = OrgMetadataResponse(
        login = "octocat",
        name = "The Octocats",
        description = "A GitHub organization",
        company = "GitHub",
        blog = "https://github.blog",
        location = "San Francisco",
        email = "octocat@github.com",
        publicRepos = 12,
        privateRepos = 4,
    )

    private fun platformTeam() = Team(
        name = "Platform",
        slug = "platform",
        organization = TeamOrganization(login = "octocat", name = "The Octocats"),
        members = MemberConnection(
            nodes = listOf(Member(login = "alice", name = "Alice")),
            pageInfo = PageInfo(hasNextPage = false, endCursor = null),
        ),
    )
}
