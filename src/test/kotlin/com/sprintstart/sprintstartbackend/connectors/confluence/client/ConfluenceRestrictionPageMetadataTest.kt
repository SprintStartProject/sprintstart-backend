package com.sprintstart.sprintstartbackend.connectors.confluence.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.DateTimeException

internal class ConfluenceRestrictionPageMetadataTest {
    @Test
    fun `a page whose version date cannot be read is an invalid response and keeps the cause`() {
        val page = ConfluencePageResponse(
            id = "42",
            title = "Runbook",
            status = "current",
            spaceId = "1",
            version = ConfluencePageVersionResponse(number = 3, createdAt = "yesterday"),
            body = ConfluencePageBodyResponse(ConfluenceStorageBodyResponse("storage", "<p>x</p>")),
        )

        val thrown = runCatching { page.toDomain(restrictions = ConfluencePageRestrictions()) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(ConfluenceInvalidResponseException::class.java)
        assertThat((thrown as ConfluenceInvalidResponseException).requestContext).isEqualTo("mapping page 42")
        assertThat(thrown.cause).isInstanceOf(DateTimeException::class.java)
    }

    @Test
    fun `a restriction window that overflows the next start is an invalid response and keeps the cause`() {
        val limit = 10
        val start = Int.MAX_VALUE - 5
        val response = ConfluenceRestrictionsResponse(
            operation = "read",
            restrictions = ConfluenceRestrictionCollectionsResponse(
                user = ConfluenceRestrictedUsersResponse(
                    results = emptyList(),
                    start = start,
                    limit = limit,
                    size = 0,
                ),
                group = ConfluenceRestrictedGroupsResponse(
                    results = emptyList(),
                    start = start,
                    limit = limit,
                    size = 0,
                ),
            ),
        )

        val thrown = runCatching {
            response.validatePage(
                requestedStart = start,
                requestedLimit = limit,
                requestContext = "reading restrictions",
            )
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(ConfluenceInvalidResponseException::class.java)
        assertThat((thrown as ConfluenceInvalidResponseException).requestContext).isEqualTo("reading restrictions")
        assertThat(thrown.cause).isInstanceOf(ArithmeticException::class.java)
    }
}
