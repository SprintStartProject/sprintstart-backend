package com.sprintstart.sprintstartbackend.canonical.model.mapper

import com.sprintstart.sprintstartbackend.canonical.model.dto.ArtifactCommand
import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.github.external.events.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubPullRequestFetchedEvent
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant

@Component
class GithubArtifactMapper() {

    fun toCommand(event : GithubCommitFetchedEvent) : ArtifactCommand
    {
       return ArtifactCommand(
           ingestionRunId = event.transactionId,
           sourceSystem = SourceSystem.GITHUB,
           sourceId = event.sha,
           sourceUrl = null,
           artifactType = ArtifactType.COMMIT,
           title = event.msg.take(72),
           bodyText = event.msg,
           mime = null,
           language = null,
           createdAtSource = event.date,
           updatedAtSource = null,
           hash = null,
           version = null,
       )
    }

    fun toCommand(event : GithubFileFetchedEvent) : ArtifactCommand
    {
        val title = event.path.split("/").last()
        val extension = when (title.lowercase()){
            "dockerfile" -> "dockerfile"
            else -> title.substringAfterLast(".", "").lowercase()
        }
        val language = EXTENSION_TO_LANGUAGE[extension]
        val mime = EXTENSION_TO_MIME[extension]
        val hash = sha256(event.content.toByteArray())

        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = event.path,
            sourceUrl = event.sourceUrl,
            artifactType = ArtifactType.FILE,
            title = title,
            bodyText = event.content,
            mime = mime,
            language = language,
            createdAtSource = null,
            updatedAtSource = null,
            hash = hash,
            version = null,
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
        val hash = sha256(content.toByteArray())
        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = event.url,
            sourceUrl = event.url,
            artifactType = ArtifactType.ISSUE,
            title = "PR #${event.title}",
            bodyText = event.body,
            mime = null,
            language = null,
            createdAtSource = Instant.parse(event.createdAt),
            updatedAtSource = null,
            hash = hash,
            version = null,
        )
    }

    fun toCommand(event : GithubPullRequestFetchedEvent) : ArtifactCommand
    {
        return ArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.GITHUB,
            sourceId = event.number.toString(),
            sourceUrl = event.url,
            artifactType = ArtifactType.PULL_REQUEST,
            title = "PR #${event.number}",
            bodyText = event.body,
            mime = null,
            language = null,
            createdAtSource = Instant.parse(event.createdAt),
            updatedAtSource = null,
            hash = null, //PRs are always re-ingested on updates
            version = null,
        )
    }

    private fun sha256(bytes: ByteArray): String {
        val digest =
            MessageDigest.getInstance("SHA-256")

        return digest
            .digest(bytes)
            .joinToString("") {
                "%02x".format(it)
            }
    }

    private val EXTENSION_TO_LANGUAGE =
        mapOf(
            "java" to "Java",
            "kt" to "Kotlin",
            "kts" to "Kotlin",
            "groovy" to "Groovy",

            "js" to "JavaScript",
            "jsx" to "JavaScript",
            "ts" to "TypeScript",
            "tsx" to "TypeScript",

            "py" to "Python",
            "go" to "Go",
            "rs" to "Rust",
            "cs" to "C#",
            "cpp" to "C++",
            "cc" to "C++",
            "cxx" to "C++",
            "c" to "C",

            "php" to "PHP",
            "rb" to "Ruby",
            "swift" to "Swift",
            "scala" to "Scala",

            "sql" to "SQL",

            "html" to "HTML",
            "css" to "CSS",
            "scss" to "SCSS",

            "xml" to "XML",
            "json" to "JSON",
            "yaml" to "YAML",
            "yml" to "YAML",
            "toml" to "TOML",

            "md" to "Markdown",
            "txt" to "Plain Text",

            "sh" to "Shell",
            "bash" to "Shell",

            "dockerfile" to "Dockerfile"
        )

    private val EXTENSION_TO_MIME =
        mapOf(
            "java" to "text/x-java",
            "kt" to "text/x-kotlin",
            "kts" to "text/x-kotlin",

            "js" to "text/javascript",
            "jsx" to "text/javascript",
            "ts" to "text/typescript",
            "tsx" to "text/typescript",

            "py" to "text/x-python",
            "go" to "text/x-go",
            "rs" to "text/x-rust",

            "html" to "text/html",
            "css" to "text/css",

            "json" to "application/json",
            "xml" to "application/xml",
            "yaml" to "application/yaml",
            "yml" to "application/yaml",

            "md" to "text/markdown",
            "txt" to "text/plain",

            "sql" to "application/sql",

            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "svg" to "image/svg+xml",
            "webp" to "image/webp",

            "pdf" to "application/pdf",

            "zip" to "application/zip",
            "jar" to "application/java-archive"
        )
}