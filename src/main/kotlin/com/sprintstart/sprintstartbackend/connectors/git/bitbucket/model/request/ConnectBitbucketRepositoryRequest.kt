package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request

import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * Represents a request to connect a Bitbucket repository to one SprintStart project.
 *
 * The project id is required rather than optional because it is what makes the repository's
 * artifacts visible: ingestion only announces artifacts to the AI index for the projects a
 * repository is linked to, so a connection without one would be collected and then never indexed.
 *
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property credentialName The name of the caller's stored Atlassian credential to read it with.
 * @property projectId The SprintStart project the repository is connected to.
 */
internal data class ConnectBitbucketRepositoryRequest(
    val workspace: String,
    val slug: String,
    val credentialName: String,
    @NotNull
    val projectId: UUID,
)
