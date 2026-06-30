package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.FileMetaDataResolver
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubSourceUrlFactory.buildCommitUrl
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildSourceId
import com.sprintstart.sprintstartbackend.ingestion.util.sha256
import org.springframework.stereotype.Component
import java.time.Instant

private const val GITHUB_COMMIT_MESSAGE_LENGTH = 72

/**
 * Translates GitHub domain events into ingestion artifact commands.
 *
 * The mapper normalizes source-specific identifiers, derives stable hashes where the ingestion
 * service uses change detection, and enriches file artifacts with lightweight metadata such as
 * mime type and language.
 */
@Component
class GithubArtifactMapper {
    /**
     * Maps a fetched commit into the ingestion command shape.
     *
     * The commit title is intentionally shortened to keep list views compact while preserving the
     * full message in `bodyText`.
     */
    fun toCommand(event: GithubCommitFetchedEvent): ArtifactCommand {
        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.COMMIT,
                unique = event.sha,
            ),
            sourceUrl = buildCommitUrl(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                sha = event.sha,
            ),
            artifactType = ArtifactType.COMMIT,
            title = event.msg.take(GITHUB_COMMIT_MESSAGE_LENGTH),
            bodyText = event.msg,
            mime = null,
            language = null,
            createdAtSource = event.date,
            updatedAtSource = null,
            hash = null,
        )
    }

    /**
     * Maps a fetched repository file and derives metadata from the file name.
     *
     * Content is hashed immediately so the ingestion service can treat file re-fetches as
     * idempotent when the payload is unchanged.
     */
    fun toCommand(event: GithubFileFetchedEvent): ArtifactCommand {
        val title = event.path.split("/").last()
        val extension = when (title.lowercase()) {
            "dockerfile" -> "dockerfile"
            else -> title.substringAfterLast(".", "").lowercase()
        }

        val language = FileMetaDataResolver.languageFor(extension)
        val mime = FileMetaDataResolver.mimeFor(extension)
        val hash = event.content.toByteArray().sha256()
        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.FILE,
                unique = event.path,
            ),
            sourceUrl = event.sourceUrl,
            artifactType = ArtifactType.FILE,
            title = title,
            bodyText = event.content,
            mime = mime,
            language = language,
            createdAtSource = null,
            updatedAtSource = null,
            hash = hash,
        )
    }

    /**
     * Maps a fetched GitHub issue and computes a hash from the visible issue content.
     *
     * The hash currently tracks title and body, which is the change signal used by
     * `ArtifactIngestionService` for issue updates.
     * @throws java.time.format.DateTimeParseException when the issue creation timestamp is malformed.
     */
    fun toCommand(event: GithubIssueFetchedEvent): ArtifactCommand {
        val content =
            buildString {
                append(event.title)
                append("|")
                append(event.body ?: "")
            }
        val hash = content.toByteArray().sha256()
        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.ISSUE,
                unique = event.number.toString(),
            ),
            sourceUrl = event.url,
            artifactType = ArtifactType.ISSUE,
            title = "Issue #${event.number} " + event.title,
            bodyText = event.body,
            mime = null,
            language = null,
            createdAtSource = Instant.parse(event.createdAt),
            updatedAtSource = null,
            hash = hash,
        )
    }

    /**
     * Maps a fetched pull request into the ingestion pull-request representation.
     *
     * Pull requests intentionally omit a hash because the ingestion service treats them as mutable
     * records and always overwrites the current title/body on re-fetch.
     * @throws java.time.format.DateTimeParseException when the pull request creation timestamp is malformed.
     */
    fun toCommand(event: GithubPullRequestFetchedEvent): ArtifactCommand {
        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.PULL_REQUEST,
                unique = event.number.toString(),
            ),
            sourceUrl = event.url,
            artifactType = ArtifactType.PULL_REQUEST,
            title = "PR #${event.number} " + event.title,
            bodyText = event.body,
            mime = null,
            language = null,
            createdAtSource = Instant.parse(event.createdAt),
            updatedAtSource = null,
            hash = null, // PRs are always re-ingested on updates
        )
    }
}
