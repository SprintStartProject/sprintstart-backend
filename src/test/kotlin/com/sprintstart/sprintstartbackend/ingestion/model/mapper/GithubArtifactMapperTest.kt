package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataMember
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataTeam
import com.sprintstart.sprintstartbackend.connectors.github.external.events.org.GithubOrgMetadataTeamMember
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubOrgMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.util.sha256
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GithubArtifactMapperTest {
    private val mapper = GithubArtifactMapper()
    private val runId = UUID.randomUUID()
    private val repositoryId = UUID.randomUUID()

    @Test
    fun `toCommand maps github file metadata and hash`() {
        val event = GithubFileFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
            content = "fun main() = Unit",
            sourceUrl = "https://github.com/owner/repo/blob/main/src/main/App.kt",
        )

        val result = mapper.toCommand(event)

        assertThat(result.ingestionRunId).isEqualTo(runId)
        assertThat(result.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat((result.metadata as GithubArtifactMetadata).repositoryId).isEqualTo(repositoryId)
        assertThat(result.sourceId).isEqualTo("github:owner/repo:FILE:src/main/App.kt")
        assertThat(result.sourceUrl).isEqualTo(event.sourceUrl)
        assertThat(result.metadata.repositoryFullName).isEqualTo("owner/repo")
        assertThat(result.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(result.title).isEqualTo("App.kt")
        assertThat(result.bodyText).isEqualTo("fun main() = Unit")
        assertThat(result.mime).isEqualTo("text/x-kotlin")
        assertThat(result.language).isEqualTo("Kotlin")
        assertThat(result.hash).isEqualTo(event.content.toByteArray().sha256())
    }

    @Test
    fun `toCommand maps dockerfile as dockerfile language`() {
        val event = GithubFileFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "Dockerfile",
            content = "FROM eclipse-temurin:21",
            sourceUrl = "https://github.com/owner/repo/blob/main/Dockerfile",
        )

        val result = mapper.toCommand(event)

        assertThat(result.title).isEqualTo("Dockerfile")
        assertThat(result.language).isEqualTo("Dockerfile")
        assertThat(result.mime).isNull()
    }

    @Test
    fun `toCommand maps commit source and truncates long title`() {
        val message = "a".repeat(80)
        val event = GithubCommitFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            author = "alice",
            date = Instant.parse("2024-01-01T00:00:00Z"),
            sha = "abc123",
            msg = message,
        )

        val result = mapper.toCommand(event)

        assertThat(result.sourceId).isEqualTo("github:owner/repo:COMMIT:abc123")
        assertThat((result.metadata as GithubArtifactMetadata).repositoryId).isEqualTo(repositoryId)
        assertThat(result.sourceUrl).isEqualTo("https://github.com/owner/repo/commit/abc123")
        assertThat(result.metadata.repositoryFullName).isEqualTo("owner/repo")
        assertThat(result.artifactType).isEqualTo(ArtifactType.COMMIT)
        assertThat(result.title).hasSize(72)
        assertThat(result.bodyText).isEqualTo(message)
        assertThat(result.createdAtSource).isEqualTo(event.date)
        assertThat(result.hash).isNull()
    }

    @Test
    fun `toCommand maps issue with stable hash`() {
        val event = GithubIssueFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            number = 42,
            title = "Bug report",
            body = "Something broke",
            state = "OPEN",
            createdAt = "2024-01-01T00:00:00Z",
            closedAt = null,
            url = "https://github.com/owner/repo/issues/42",
            author = "alice",
            labels = emptyList(),
            assignees = emptyList(),
            comments = emptyList(),
        )

        val result = mapper.toCommand(event)

        assertThat(result.sourceId).isEqualTo("github:owner/repo:ISSUE:42")
        assertThat((result.metadata as GithubArtifactMetadata).repositoryId).isEqualTo(repositoryId)
        assertThat(result.sourceUrl).isEqualTo(event.url)
        assertThat(result.metadata.repositoryFullName).isEqualTo("owner/repo")
        assertThat(result.artifactType).isEqualTo(ArtifactType.ISSUE)
        assertThat(result.title).isEqualTo("Issue #42 Bug report")
        assertThat(result.bodyText).isEqualTo("Something broke")
        assertThat(result.createdAtSource).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"))
        assertThat(result.hash).isEqualTo("Bug report|Something broke".toByteArray().sha256())
    }

    @Test
    fun `toCommand maps pull request without hash`() {
        val event = GithubPullRequestFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            number = 7,
            title = "Improve docs",
            body = null,
            state = "MERGED",
            createdAt = "2024-01-02T00:00:00Z",
            mergedAt = null,
            url = "https://github.com/owner/repo/pull/7",
            author = "bob",
            labels = null,
            reviews = null,
            comments = null,
            reviewThreads = null,
        )

        val result = mapper.toCommand(event)

        assertThat(result.sourceId).isEqualTo("github:owner/repo:PULL_REQUEST:7")
        assertThat((result.metadata as GithubArtifactMetadata).repositoryId).isEqualTo(repositoryId)
        assertThat(result.sourceUrl).isEqualTo(event.url)
        assertThat(result.metadata.repositoryFullName).isEqualTo("owner/repo")
        assertThat(result.artifactType).isEqualTo(ArtifactType.PULL_REQUEST)
        assertThat(result.title).isEqualTo("PR #7 Improve docs")
        assertThat(result.bodyText).isNull()
        assertThat(result.hash).isNull()
    }

    @Test
    fun `toCommand maps github org metadata using login as source identity`() {
        val event = GithubOrgMetadataFetchedEvent(
            transactionId = runId,
            login = "octocat",
            name = "The Octocats",
            description = "A GitHub organization",
            company = "GitHub",
            blog = "https://github.blog",
            location = "San Francisco",
            email = "octocat@github.com",
            publicRepos = 12,
            privateRepos = 4,
            teams = listOf(
                GithubOrgMetadataTeam(
                    name = "Platform",
                    slug = "platform",
                    orgLogin = "octocat",
                    orgName = "The Octocats",
                    members = listOf(
                        GithubOrgMetadataTeamMember(login = "alice", name = "Alice"),
                    ),
                ),
            ),
            members = listOf(
                GithubOrgMetadataMember(login = "bob", url = "https://github.com/bob"),
            ),
        )

        val result = mapper.toCommand(event)

        assertThat(result.ingestionRunId).isEqualTo(runId)
        assertThat(result.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(result.sourceId).isEqualTo("octocat")
        assertThat(result.sourceUrl).isEqualTo("https://github.com/octocat")
        assertThat(result.artifactType).isEqualTo(ArtifactType.ORG_METADATA)
        assertThat(result.title).isEqualTo("The Octocats")

        val metadata = result.metadata as GithubOrgMetadataArtifactMetadata
        assertThat(metadata.login).isEqualTo("octocat")
        assertThat(metadata.name).isEqualTo("The Octocats")
        assertThat(metadata.publicRepos).isEqualTo(12)
        assertThat(metadata.privateRepos).isEqualTo(4)
        assertThat(metadata.teams).hasSize(1)
        assertThat(metadata.teams!![0].members[0].login).isEqualTo("alice")
        assertThat(metadata.members[0].login).isEqualTo("bob")
        assertThat(metadata.members[0].url).isEqualTo("https://github.com/bob")
    }
}
