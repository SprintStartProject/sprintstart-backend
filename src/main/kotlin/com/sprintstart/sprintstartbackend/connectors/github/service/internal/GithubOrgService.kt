package com.sprintstart.sprintstartbackend.connectors.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMembersResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.OrgMetadataResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.Team
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GithubOrgService(
    private val connectionRepository: GithubRepositoryConnectionRepository,
    private val githubClient: GithubClient,
) {
    @Tracked("Connecting GitHub org level data")
    @Transactional(readOnly = true)
    suspend fun connectGithubOrgIfNecessary(org: String, token: String) {
        if (withContext(Dispatchers.IO) { connectionRepository.existsByOwner(org) }) {
            return
        }

        return connectGithubOrg(org, token)
    }

    private suspend fun connectGithubOrg(org: String, token: String) {
        var metadata: OrgMetadataResponse
        var teams: List<Team>
        var teamMembers: OrgMembersResponse

        runCatching {
            metadata = githubClient.fetchOrgMetadata(org, token)
            teams = githubClient.getOrgTeams(org, token)
            teamMembers = githubClient.getOrgMembers(org, token)
        }.onFailure { error ->
            throw error
        }
    }
}
