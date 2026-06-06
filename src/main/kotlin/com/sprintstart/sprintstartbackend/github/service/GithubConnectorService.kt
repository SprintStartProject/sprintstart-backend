package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import org.springframework.stereotype.Service

@Service
class GithubConnectorService(
    applicationConfig: ApplicationConfig,
    private val webClient: WebClient,
    val githubClient: GithubClient,
) {
    suspend fun connectRepository(owner: String, name: String) {
        githubClient.connectGithub(owner, name)
    }
}
