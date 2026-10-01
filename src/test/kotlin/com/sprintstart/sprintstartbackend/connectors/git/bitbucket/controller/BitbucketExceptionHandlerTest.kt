package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.controller

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryDoesNotExistException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotEnabledException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Pins the HTTP status each Bitbucket failure answers with.
 *
 * The mapping is the contract callers build against — 400 for a request that cannot be served, 403
 * for a denied project, 404 for something that is genuinely absent — so it is asserted here rather
 * than inferred from whichever endpoint happens to surface it.
 */
class BitbucketExceptionHandlerTest {
    private val handler = BitbucketExceptionHandler()

    @Test
    fun `answers 400 when an update targets a disabled repository`() {
        val response = handler.handleRepositoryNotEnabled(
            BitbucketRepositoryNotEnabledException(workspace = "sprintstart", slug = "backend"),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).contains("sprintstart/backend")
    }

    @Test
    fun `answers 400 when the repository is not connected`() {
        val response = handler.handleRepositoryNotConnected(
            BitbucketRepositoryNotConnectedException(workspace = "sprintstart", slug = "backend"),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `answers 403 when the caller has no access to the project`() {
        val response = handler.handleProjectAccessDenied(BitbucketProjectAccessDeniedException(UUID.randomUUID()))

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `answers 404 for an unknown connection`() {
        val response = handler.handleConnectionNotFound(
            BitbucketRepositoryConnectionNotFoundException(UUID.randomUUID()),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `answers 404 when Bitbucket has no such repository or hides it from the credential`() {
        val response = handler.handleRepositoryDoesNotExist(
            BitbucketRepositoryDoesNotExistException(
                workspace = "sprintstart",
                slug = "missing",
                credentialName = "team-token",
            ),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.message)
            .contains("sprintstart/missing")
            .contains("team-token")
    }

    @Test
    fun `answers 404 when the connection has lost its config`() {
        val response = handler.handleRepositoryConfigNotFound(
            BitbucketRepositoryConfigNotFoundException(workspace = "sprintstart", slug = "backend"),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
