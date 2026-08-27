package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientCredentials
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePage
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePageRestrictions
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePageVersion
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceRestrictedGroup
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceRestrictedUser
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceStorageBody
import com.sprintstart.sprintstartbackend.connectors.confluence.parser.ParsedConfluenceBody
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConfluencePageArtifactMapperTest {
    private val mapper = ConfluencePageArtifactMapper()

    @Test
    fun `maps provenance ACL safe URL and connection-scoped identity`() {
        val connection = connection()
        val page = page(
            restrictions = ConfluencePageRestrictions(
                users = listOf(
                    ConfluenceRestrictedUser("account-b"),
                    ConfluenceRestrictedUser("account-a"),
                    ConfluenceRestrictedUser("account-a"),
                ),
                groups = listOf(ConfluenceRestrictedGroup("group-b"), ConfluenceRestrictedGroup("group-a")),
            ),
        )

        val result = mapper.toCommand(
            connection = connection,
            page = page,
            parsedBody = ParsedConfluenceBody(bodyText = "Clean text"),
            relationships = listOf(
                ConfluenceRelationshipCommand(ConfluenceRelationshipType.CHILD_OF, "100"),
            ),
        )

        assertThat(result.sourceId).isEqualTo("confluence:${connection.id}:page:200")
        assertThat(result.sourceUrl).isEqualTo("https://tenant.atlassian.net/wiki/spaces/ENG/pages/200")
        assertThat(result.sourceVersion).isEqualTo("7")
        assertThat(result.metadata.connectionId).isEqualTo(connection.id)
        assertThat(result.metadata.spaceId).isEqualTo("42")
        assertThat(result.metadata.spaceKey).isEqualTo("ENG")
        assertThat(result.metadata.pageId).isEqualTo("200")
        assertThat(result.metadata.parentId).isEqualTo("100")
        assertThat(result.metadata.sourceAcl.userAccountIds).containsExactly("account-a", "account-b")
        assertThat(result.metadata.sourceAcl.groupIds).containsExactly("group-a", "group-b")
    }

    @Test
    fun `same page ID on another connection has another source identity`() {
        val first = mapper.toCommand(connection(), page(), ParsedConfluenceBody("text"), emptyList())
        val second = mapper.toCommand(connection(), page(), ParsedConfluenceBody("text"), emptyList())

        assertThat(first.sourceId).isNotEqualTo(second.sourceId)
        assertThat(first.metadata.pageId).isEqualTo(second.metadata.pageId)
    }

    @Test
    fun `rejects cross-origin web UI URL without failing mapping`() {
        val result = mapper.toCommand(
            connection(),
            page(webUiPath = "https://attacker.invalid/secret"),
            ParsedConfluenceBody("text"),
            emptyList(),
        )

        assertThat(result.sourceUrl).isNull()
        assertThat(result.metadata.webUiPath).isNull()
    }

    private fun connection() = ConfluenceConnectionIngestionSnapshot(
        id = UUID.randomUUID(),
        projectId = UUID.randomUUID(),
        baseUrl = "https://tenant.atlassian.net",
        spaceId = "42",
        spaceKey = "ENG",
        sourceEnabled = true,
        pageAllowlist = emptyList(),
        pageDenylist = emptyList(),
        credentials = ConfluenceClientCredentials("fake-user@example.invalid", "fake-token"),
    )

    private fun page(
        webUiPath: String? = "/wiki/spaces/ENG/pages/200",
        restrictions: ConfluencePageRestrictions = ConfluencePageRestrictions(),
    ) = ConfluencePage(
        id = "200",
        title = "Runbook",
        status = "current",
        spaceId = "42",
        parentId = "100",
        parentType = "page",
        version = ConfluencePageVersion(7, Instant.parse("2026-08-01T10:00:00Z")),
        storage = ConfluenceStorageBody("storage", "<p>Clean text</p>"),
        webUiPath = webUiPath,
        restrictions = restrictions,
    )
}
