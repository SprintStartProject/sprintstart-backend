package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketWorkspaceMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketWorkspaceMetadataMember
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubOrgMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubOrgMetadataMember
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubOrgMetadataTeam
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubOrgMetadataTeamMember
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraArtifactMetadataWrapper
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraAuthor
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueComment
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistory
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistoryItem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistorySubitem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueType
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraProject
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

/**
 * Locks the metadata read paths against Jackson's DEDUCTION field-set guessing.
 *
 * The Jira wrapper carries nested `name` shapes (`JiraIssueType`, `JiraProject`), and the
 * org-level types were one registration away from hijacking those reads — a regression no test
 * caught because nothing round tripped Jira metadata. Each type below pins its own way home.
 */
class ArtifactMetadataJsonMapperTest {
    private val mapper = ArtifactMetadataJsonMapper(jacksonObjectMapper())
    private val created = Instant.parse("2026-08-01T09:00:00Z")

    private fun author(name: String) = JiraAuthor(
        displayName = name,
        active = true,
        createdAt = created,
        updatedAt = created,
    )

    private fun jiraWrapper() = JiraArtifactMetadataWrapper(
        issueType = JiraIssueType(name = "Task", description = "A unit of work"),
        issueKey = "ONB-42",
        statusName = "In Progress",
        statusDescription = "",
        statusCategory = "indeterminate",
        createdBy = author("Grace Hopper"),
        reportedBy = author("Grace Hopper"),
        assignee = author("Ada Lovelace"),
        project = JiraProject(key = "ONB", name = "Onboarding", projectTypeKey = "software"),
        history = JiraIssueHistory(
            historyItems = listOf(
                JiraIssueHistoryItem(
                    author = author("Grace Hopper"),
                    createdAt = created,
                    items = listOf(
                        JiraIssueHistorySubitem(field = "status", fieldtype = "jira", from = "", to = "In Progress"),
                    ),
                ),
            ),
        ),
        comments = listOf(JiraIssueComment(author = author("Grace Hopper"), content = "Welcome aboard")),
    )

    private fun workspaceMetadata() = BitbucketWorkspaceMetadataArtifactMetadata(
        workspace = "acme",
        uuid = "{12345678-1234-1234-1234-123456789012}",
        name = "Acme",
        isPrivate = true,
        createdOn = "2020-01-01T00:00:00.000Z",
        url = "https://bitbucket.org/acme",
        members = listOf(
            BitbucketWorkspaceMetadataMember(accountId = "1", nickname = "ada", displayName = "Ada Lovelace"),
        ),
    )

    private fun orgMetadata() = GithubOrgMetadataArtifactMetadata(
        login = "acme",
        name = "Acme",
        description = "Acme org",
        company = "Acme Inc",
        blog = "https://acme.test",
        location = "Berlin",
        email = null,
        publicRepos = 3,
        privateRepos = 1,
        teams = listOf(
            GithubOrgMetadataTeam(
                name = "Core",
                slug = "core",
                orgLogin = "acme",
                orgName = "Acme",
                members = listOf(GithubOrgMetadataTeamMember(login = "ada", name = "Ada Lovelace")),
            ),
        ),
        members = listOf(GithubOrgMetadataMember(login = "ada", url = "https://api.github.com/users/ada")),
    )

    @Test
    fun `a jira wrapper with nested name shapes survives a metadata round trip`() {
        val wrapper = jiraWrapper()

        val json = mapper.toJson(wrapper)

        assertThat(mapper.fromJson(json)).isEqualTo(wrapper)
        assertThat(mapper.fromJson(json, SourceSystem.JIRA, ArtifactType.ISSUE)).isEqualTo(wrapper)
    }

    @Test
    fun `a bitbucket workspace row survives an explicit round trip`() {
        val metadata = workspaceMetadata()

        val json = mapper.toJson(metadata)

        assertThat(mapper.fromWorkspaceJson(json)).isEqualTo(metadata)
        assertThat(mapper.fromJson(json, SourceSystem.BITBUCKET, ArtifactType.ORG_METADATA)).isEqualTo(metadata)
    }

    @Test
    fun `a github org row survives an explicit round trip`() {
        val metadata = orgMetadata()

        val json = mapper.toJson(metadata)

        assertThat(mapper.fromGithubOrgJson(json)).isEqualTo(metadata)
        assertThat(mapper.fromJson(json, SourceSystem.GITHUB, ArtifactType.ORG_METADATA)).isEqualTo(metadata)
    }

    /**
     * DEDUCTION narrows candidates by field name, so any registered type carrying `name` hijacks
     * the nested Jira shapes (`JiraIssueType`, `JiraProject`) and their reads fail. Org-level
     * metadata stays off the candidate list and travels through the explicit methods instead.
     */
    @Test
    fun `no deduction candidate carries a name field`() {
        val candidates = ArtifactMetadata::class.java
            .getAnnotation(JsonSubTypes::class.java)
            .value
            .map { it.value.java }

        val offenders = candidates.filter { candidate ->
            candidate.declaredFields.any { field -> field.name == "name" }
        }

        assertThat(offenders).isEmpty()
    }
}
