package com.sprintstart.sprintstartbackend.connectors.notion.controller

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionAuthenticationException
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionExternalServiceException
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionInvalidResponseException
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionResourceNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionTransportException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

internal class NotionExceptionHandlerTest {
    private val handler = NotionExceptionHandler()

    @Test
    fun `persistence exceptions retain their domain status and safe message`() {
        val badRequest = handler.handlePersistenceException(
            NotionWorkspaceConnectionConfigurationException("Invalid Notion page"),
        )
        val notFound = handler.handlePersistenceException(NotionCredentialNotFoundException("missing"))
        val conflict = handler.handlePersistenceException(NotionCredentialAlreadyExistsException("duplicate"))

        assertThat(badRequest.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(badRequest.body?.message).isEqualTo("Invalid Notion page")
        assertThat(badRequest.body?.code).isEqualTo("NOTION_PERSISTENCE_ERROR")
        assertThat(notFound.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(conflict.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `Notion authentication and access failures do not look like expired backend sessions`() {
        val unauthorized = handler.handleClientException(NotionAuthenticationException("validating credentials"))
        val forbidden = handler.handleClientException(NotionAccessDeniedException("discovering pages"))
        val notFound = handler.handleClientException(NotionResourceNotFoundException("retrieving page"))

        assertThat(unauthorized.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(unauthorized.body?.code).isEqualTo("NOTION_AUTHENTICATION_FAILED")
        assertThat(forbidden.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(forbidden.body?.code).isEqualTo("NOTION_ACCESS_DENIED")
        assertThat(notFound.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(notFound.body?.code).isEqualTo("NOTION_RESOURCE_NOT_FOUND")
    }

    @Test
    fun `all other Notion failures become bad gateway without raw upstream data`() {
        val failures = listOf(
            NotionExternalServiceException("discovering pages", 429, 3, retryExhausted = true),
            NotionExternalServiceException("discovering pages", 529, 3, retryExhausted = true),
            NotionTransportException("discovering pages", 3, retryExhausted = true),
            NotionInvalidResponseException("discovering pages"),
        )

        val responses = failures.map(handler::handleClientException)

        assertThat(responses).allSatisfy { response ->
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
            assertThat(response.body?.code).isEqualTo("NOTION_UPSTREAM_ERROR")
            assertThat(response.body?.message).doesNotContain("secret", "Authorization", "response body")
        }
    }
}
