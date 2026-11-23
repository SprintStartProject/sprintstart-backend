package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.service.FileAuthorshipService
import com.sprintstart.sprintstartbackend.ingestion.service.FileComplexityService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class FileComplexityListenerTest {
    private val complexity = mockk<FileComplexityService>(relaxed = true)
    private val authorship = mockk<FileAuthorshipService>(relaxed = true)
    private val listener = FileComplexityListener(complexity, authorship)
    private val run = UUID.randomUUID()
    private val repository = UUID.randomUUID()
    private val authors = mapOf("ada@x.org" to 4)

    @Test
    fun `a github file that was read is measured and its authors kept under the key of its repository`() {
        listener.on(GithubFileFetchedEvent(run, repository, "acme", "app", "src/A.kt", "text", "https://x", authors))

        verify { complexity.measure("github:acme/app", "src/A.kt", "text") }
        verify { authorship.record("github:acme/app", "src/A.kt", authors) }
    }

    @Test
    fun `a github file that is gone is forgotten everywhere`() {
        listener.on(GithubFileDeletedEvent(run, repository, "acme", "app", "src/A.kt"))

        verify { complexity.forget("github:acme/app", "src/A.kt") }
        verify { authorship.forget("github:acme/app", "src/A.kt") }
    }

    @Test
    fun `a bitbucket file that was read is measured and its authors kept under the key of its repository`() {
        listener.on(BitbucketFileFetchedEvent(run, repository, "team", "app", "src/A.kt", "text", "https://x", authors))

        verify { complexity.measure("bitbucket:team/app", "src/A.kt", "text") }
        verify { authorship.record("bitbucket:team/app", "src/A.kt", authors) }
    }

    @Test
    fun `a bitbucket file that is gone is forgotten everywhere`() {
        listener.on(BitbucketFileDeletedEvent(run, repository, "team", "app", "src/A.kt"))

        verify { complexity.forget("bitbucket:team/app", "src/A.kt") }
        verify { authorship.forget("bitbucket:team/app", "src/A.kt") }
    }
}
