package com.sprintstart.sprintstartbackend.connectors.github.controller

import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.ProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.SourceNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class GithubExceptionHandlerTest {
    private val handler = GithubExceptionHandler()

    @Test
    fun `handleSourceNotFound returns 404 with the exception message`() {
        val exception = SourceNotFoundException("abc-123")

        val response = handler.handleSourceNotFound(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.message).isEqualTo("Source with id abc-123 not found.")
    }

    @Test
    fun `handleProjectAccessDenied returns 403 with the exception message`() {
        val projectId = UUID.randomUUID()
        val exception = ProjectAccessDeniedException(projectId)

        val response = handler.handleProjectAccessDenied(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(response.body?.message).isEqualTo("No access to project with id $projectId")
    }
}
