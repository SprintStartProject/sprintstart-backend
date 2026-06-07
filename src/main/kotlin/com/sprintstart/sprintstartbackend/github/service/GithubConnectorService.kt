package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.github.GithubClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

@Service
class GithubConnectorService(
    val applicationScope: CoroutineScope,
    val githubClient: GithubClient,
) {
    suspend fun connectRepository(owner: String, name: String) {
        applicationScope.launch {
            launch { githubClient.fetchAndIngestAllGithubCommits(owner, name) }
            launch { githubClient.fetchAndIngestAllIssues(owner, name) }
        }
    }
}
