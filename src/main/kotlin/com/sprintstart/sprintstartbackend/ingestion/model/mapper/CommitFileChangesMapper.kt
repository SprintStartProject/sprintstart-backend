package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.CommitFileChangesCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildBitbucketRepositoryKey
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildBitbucketSourceId
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildGithubRepositoryKey
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildSourceId
import org.springframework.stereotype.Component

/**
 * Translates a fetched commit of any Git provider into the neutral command that records its files.
 *
 * Kept apart from the artifact mappers: they decide what a commit looks like as an artifact, this
 * decides what it says about who changed which file, and the second is identical for every provider.
 * The commit's source id is built by the same factory the artifact mappers use, so the files always
 * attach to the artifact stored for that commit.
 */
@Component
class CommitFileChangesMapper {
    /**
     * Maps a GitHub commit.
     *
     * @param event The fetched commit.
     */
    fun toCommand(event: GithubCommitFetchedEvent): CommitFileChangesCommand =
        CommitFileChangesCommand(
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.COMMIT,
                unique = event.sha,
            ),
            repository = buildGithubRepositoryKey(event.repositoryOwner, event.repositoryName),
            authorName = event.author,
            authorEmail = event.authorEmail,
            changedAt = event.date,
            files = event.files,
        )

    /**
     * Maps a Bitbucket commit.
     *
     * @param event The fetched commit.
     */
    fun toCommand(event: BitbucketCommitFetchedEvent): CommitFileChangesCommand =
        CommitFileChangesCommand(
            sourceId = buildBitbucketSourceId(
                workspace = event.workspace,
                slug = event.slug,
                type = ArtifactType.COMMIT,
                unique = event.sha,
            ),
            repository = buildBitbucketRepositoryKey(event.workspace, event.slug),
            authorName = event.author,
            authorEmail = event.authorEmail,
            changedAt = event.committedAt,
            files = event.files,
        )
}
