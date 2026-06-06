package com.sprintstart.sprintstartbackend.github.controller

import com.sprintstart.sprintstartbackend.github.models.api.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.github.service.GithubConnectorService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Github Connector", description = "Endpoints for interacting with the Github connector")
@RestController
@RequestMapping("/api/v1/github")
@Validated
internal class GithubConnectorController(
    val githubConnectorService: GithubConnectorService,
) {

    @PostMapping("/connect")
    suspend fun connectRepository(@RequestBody request: ConnectRepositoryRequest) {
        githubConnectorService.connectRepository(request.owner, request.name)
    }
}
