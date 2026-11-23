package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.service.FileComplexityService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class FileComplexityListenerTest {
    private val complexity = mockk<FileComplexityService>(relaxed = true)
    private val listener = FileComplexityListener(complexity)
    private val run = UUID.randomUUID()
    private val repository = UUID.randomUUID()

    @Test
    fun `a github file that was read is measured under the key of its repository`() {
        listener.on(GithubFileFetchedEvent(run, repository, "acme", "app", "src/A.kt", "text", "https://x"))

        verify { complexity.measure("github:acme/app", "src/A.kt", "text") }
    }

    @Test
    fun `a github file that is gone is forgotten`() {
        listener.on(GithubFileDeletedEvent(run, repository, "acme", "app", "src/A.kt"))

        verify { complexity.forget("github:acme/app", "src/A.kt") }
    }

    @Test
    fun `a bitbucket file that was read is measured under the key of its repository`() {
        listener.on(BitbucketFileFetchedEvent(run, repository, "team", "app", "src/A.kt", "text", "https://x"))

        verify { complexity.measure("bitbucket:team/app", "src/A.kt", "text") }
    }
}
