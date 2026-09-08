package com.sprintstart.sprintstartbackend.connectors.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataMember
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataTeam
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataTeamMember
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubOrganization
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMembersResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMetadataResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.Team
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubOrganizationRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GithubOrgService(
    private val githubClient: GithubClient,
    private val orgRepository: GithubOrganizationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Fetches & triggers ingestion of organization level metadata for GitHub, if the given org is not already
     * connected.
     *
     * The fetch is skipped when the organization's metadata has already been fetched before or when
     * the given login is not an organization (for example a user account). In both cases a completed
     * event is still published so the ingestion run can mark the org-metadata phase as finished.
     *
     * @param org The organization to fetch metadata of.
     * @param token The GitHub user token to use for auth.
     * @param transactionId The UUID identifying the fetch & ingest transaction.
     */
    @Tracked("Connecting GitHub org level data")
    suspend fun connectGithubOrgIfNecessary(org: String, token: String, transactionId: UUID) {
        eventPublisher.publishEvent(GithubOrgMetadataFetchingStartedEvent(transactionId))

        if (withContext(Dispatchers.IO) { orgRepository.existsById(org) }) {
            eventPublisher.publishEvent(GithubOrgMetadataFetchingCompletedEvent(transactionId))
            return
        }

        if (!githubClient.isOrganization(org, token)) {
            eventPublisher.publishEvent(GithubOrgMetadataFetchingCompletedEvent(transactionId))
            return
        }

        connectGithubOrg(org, token, transactionId)

        eventPublisher.publishEvent(GithubOrgMetadataFetchingCompletedEvent(transactionId))
    }

    /**
     * Fetches all useful metadata from a GitHub organization and triggers ingestion of the data.
     *
     * @param org The GitHub organization to fetch & ingest metadata of.
     * @param token The GitHub user token to use for auth.
     * @param transactionId The UUID identifying the fetch & ingest transaction.
     * @throws WebClientException if fetching fails.
     * @throws SerializationException If deserialization of GitHub api responses fails.
     */
    private suspend fun connectGithubOrg(org: String, token: String, transactionId: UUID) {
        runCatching {
            val metadata = githubClient.fetchOrgMetadata(org, token)
            val teams = githubClient.getOrgTeams(org, token)
            val teamMembers = githubClient.getOrgMembers(org, token)

            val event = buildOrgMetadataFetchedEvent(transactionId, metadata, teams, teamMembers)
            eventPublisher.publishEvent(event)

            withContext(Dispatchers.IO) {
                orgRepository.save(GithubOrganization(login = org, name = metadata.name))
            }
        }.onFailure { error ->
            eventPublisher.publishEvent(GithubOrgMetadataFetchingFailedEvent(transactionId, error.message))
            throw error
        }
    }

    /**
     * Builds the [GithubOrgMetadataFetchedEvent] off the information retrieved by fetching methods in [GithubClient].
     *
     * @param transactionId The UUID identifying the fetch & ingest transaction.
     * @param metadata The org level metadata that was fetched.
     * @param teams A list of teams that belongs to the org that was fetched.
     * @param orgMembers Information about the members of the org.
     */
    private fun buildOrgMetadataFetchedEvent(
        transactionId: UUID,
        metadata: OrgMetadataResponse,
        teams: List<Team>,
        orgMembers: OrgMembersResponse,
    ): GithubOrgMetadataFetchedEvent {
        return GithubOrgMetadataFetchedEvent(
            transactionId = transactionId,
            login = metadata.login,
            name = metadata.name,
            description = metadata.description,
            company = metadata.company,
            blog = metadata.blog,
            location = metadata.location,
            email = metadata.email,
            publicRepos = metadata.publicRepos,
            privateRepos = metadata.privateRepos,
            teams = teams.map { team ->
                GithubOrgMetadataTeam(
                    name = team.name,
                    slug = team.slug,
                    orgLogin = team.organization.login,
                    orgName = team.organization.name,
                    members = team.members.nodes.map { member ->
                        GithubOrgMetadataTeamMember(
                            login = member.login,
                            name = member.name,
                        )
                    },
                )
            },
            members = orgMembers.members.map { member ->
                GithubOrgMetadataMember(
                    login = member.login,
                    url = member.url,
                )
            },
        )
    }
}
