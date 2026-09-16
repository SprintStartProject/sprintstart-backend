package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.external.events.ArtifactsIndexedEvent
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class IngestionIndustryEventListenerTest {
    private val testScope = TestScope()
    private val projectIndustryApi = mockk<ProjectIndustryApi>()

    private val listener = IngestionIndustryEventListener(
        projectIndustryApi = projectIndustryApi,
        applicationScope = testScope,
    )

    private val runId = UUID.randomUUID()
    private val project1 = UUID.randomUUID()
    private val project2 = UUID.randomUUID()

    @Test
    fun `handleArtifactsIndexed triggers auto evaluation for all projects in event`() {
        coEvery { projectIndustryApi.evaluateIndustryAutomatically(any()) } just runs

        listener.handleArtifactsIndexed(ArtifactsIndexedEvent(runId = runId, projectIds = setOf(project1, project2)))
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project1) }
        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project2) }
    }

    @Test
    fun `handleArtifactsIndexed does nothing when projectIds is empty`() {
        listener.handleArtifactsIndexed(ArtifactsIndexedEvent(runId = runId, projectIds = emptySet()))
        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { projectIndustryApi.evaluateIndustryAutomatically(any()) }
    }

    @Test
    fun `handleArtifactsIndexed catches failures per project and continues evaluating remaining projects`() {
        coEvery { projectIndustryApi.evaluateIndustryAutomatically(project1) } throws
            IllegalStateException("AI timeout for project 1")
        coEvery { projectIndustryApi.evaluateIndustryAutomatically(project2) } just runs

        listener.handleArtifactsIndexed(ArtifactsIndexedEvent(runId = runId, projectIds = setOf(project1, project2)))
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project1) }
        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project2) }
    }

    @Test
    fun `handleArtifactsIndexed logs and does not throw when evaluation fails`() {
        coEvery { projectIndustryApi.evaluateIndustryAutomatically(project1) } throws
            IllegalStateException("AI timeout")

        // Must not crash the scope or rethrow
        listener.handleArtifactsIndexed(ArtifactsIndexedEvent(runId = runId, projectIds = setOf(project1)))
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project1) }
    }
}
