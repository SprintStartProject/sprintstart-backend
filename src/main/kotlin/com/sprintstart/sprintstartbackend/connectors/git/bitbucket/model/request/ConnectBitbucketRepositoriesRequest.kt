package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

/**
 * Represents a request to connect several Bitbucket repositories in one call.
 *
 * Each entry is a complete connection request with its own credential and target project, so one
 * submission can span workspaces and projects. The list must not be empty: a batch covering no
 * repository is a caller mistake, and answering 400 is more useful than accepting work that
 * cannot exist.
 *
 * @property repositories The repositories to connect, each with its own credential and project.
 */
internal data class ConnectBitbucketRepositoriesRequest(
    @NotEmpty
    val repositories: List<@Valid ConnectBitbucketRepositoryRequest>,
)
