package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.projects.BitbucketRepositoryProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactProjectService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The workspace metadata artifact follows its repositories' project links: it is visible to a
 * project when any repository of its workspace is, so linking a repository links the workspace
 * artifact too — and unlinking only drops it once no repository still carries the project.
 */
class BitbucketRepositoryProjectsListenerTest {
    private val artifactProjectService = mockk<ArtifactProjectService>()
    private val bitbucketRepositoryApi = mockk<BitbucketRepositoryApi>()

    // Unconfined so the launched propagation runs inline and can be verified without waiting.
    private val applicationScope = CoroutineScope(Dispatchers.Unconfined)

    private val listener = BitbucketRepositoryProjectsListener(
        artifactProjectService = artifactProjectService,
        bitbucketRepositoryApi = bitbucketRepositoryApi,
        applicationScope = applicationScope,
    )

    private val projectId = UUID.randomUUID()

    private fun event(linked: Boolean) = BitbucketRepositoryProjectLinkChangedEvent(
        workspace = "sprintstart",
        slug = "backend",
        projectId = projectId,
        linked = linked,
    )

    @Test
    fun `linking a repository links the workspace artifact too`() {
        coEvery { artifactProjectService.applyProjectLink(any(), any(), any()) } just runs

        listener.on(event(linked = true))

        coVerify {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.BitbucketRepository("sprintstart", "backend"),
                projectId,
                true,
            )
        }
        coVerify {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.BitbucketWorkspace("sprintstart"),
                projectId,
                true,
            )
        }
    }

    @Test
    fun `unlinking keeps the workspace artifact while another repository still carries the project`() {
        coEvery { artifactProjectService.applyProjectLink(any(), any(), any()) } just runs
        every { bitbucketRepositoryApi.getWorkspaceProjectIds("sprintstart") } returns setOf(projectId)

        listener.on(event(linked = false))

        coVerify {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.BitbucketRepository("sprintstart", "backend"),
                projectId,
                false,
            )
        }
        coVerify(exactly = 0) {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.BitbucketWorkspace("sprintstart"),
                projectId,
                false,
            )
        }
    }

    @Test
    fun `unlinking drops the workspace artifact once no repository carries the project`() {
        coEvery { artifactProjectService.applyProjectLink(any(), any(), any()) } just runs
        every { bitbucketRepositoryApi.getWorkspaceProjectIds("sprintstart") } returns emptySet()

        listener.on(event(linked = false))

        coVerify {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.BitbucketWorkspace("sprintstart"),
                projectId,
                false,
            )
        }
    }
}
