package com.sprintstart.sprintstartbackend.canonical.model.mapper

import com.sprintstart.sprintstartbackend.canonical.model.FileMetaDataResolver
import com.sprintstart.sprintstartbackend.canonical.model.dto.command.ArtifactCommand
import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.model.mapper.SourceIdFactory.buildSourceId
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class GithubArtifactMapper() {

    fun toCommand(event : GithubCommitFetchedEvent) : ArtifactCommand
    {
       return ArtifactCommand(
           ingestionRunId = event.transactionId,
           sourceSystem = SourceSystem.GITHUB,
           sourceId = buildSourceId(
               repositoryOwner = event.repositoryOwner,
               repositoryName = event.repositoryName,
               type = ArtifactType.COMMIT,
               unique = event.sha
           ),
           sourceUrl = "https://github.com/${event.repositoryOwner}/${event.repositoryName}/commit/${event.sha}",
           artifactType = ArtifactType.COMMIT,
           title = event.msg.take(72),
           bodyText = event.msg,
           mime = null,
           language = null,
           createdAtSource = event.date,
           updatedAtSource = null,
           hash = null,
       )
    }

    fun toCommand(event : GithubFileFetchedEvent) : ArtifactCommand
    {
        val title = event.path.split("/").last()
        val extension = when (title.lowercase()){
            "dockerfile" -> "dockerfile"
            else -> title.substringAfterLast(".", "").lowercase()
        }

        val language = FileMetaDataResolver.languageFor(extension)
        val mime = FileMetaDataResolver.mimeFor(extension)
        val hash = Sha256.sha256(event.content.toByteArray())

        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.FILE,
                unique = event.path
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

    fun toCommand(event : GithubIssueFetchedEvent) : ArtifactCommand
    {
        val content =
            buildString {
                append(event.title)
                append("|")
                append(event.body?:"")
            }
        val hash = Sha256.sha256(content.toByteArray())
        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.ISSUE,
                unique = event.number.toString()
            ),
            sourceUrl = event.url,
            artifactType = ArtifactType.ISSUE,
            title = "PR #${event.number} " + event.title,
            bodyText = event.body,
            mime = null,
            language = null,
            createdAtSource = Instant.parse(event.createdAt),
            updatedAtSource = null,
            hash = hash,
        )
    }

    fun toCommand(event : GithubPullRequestFetchedEvent) : ArtifactCommand
    {
        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.PULL_REQUEST,
                unique = event.number.toString()
            ),
            sourceUrl = event.url,
            artifactType = ArtifactType.PULL_REQUEST,
            title = "PR #${event.number} " + event.title,
            bodyText = event.body,
            mime = null,
            language = null,
            createdAtSource = Instant.parse(event.createdAt),
            updatedAtSource = null,
            hash = null, //PRs are always re-ingested on updates
        )
    }

}