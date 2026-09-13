package com.sprintstart.sprintstartbackend.ingestion.listener

import com.sprintstart.sprintstartbackend.ingestion.events.RunFinishedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class IngestionIndustryEventListenerTest {
    private val testScope = TestScope()
    private val projectIndustryApi = mockk<ProjectIndustryApi>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val ingestionRunRepository = mockk<IngestionRunRepository>()

    private val listener = IngestionIndustryEventListener(
        projectIndustryApi = projectIndustryApi,
        artifactRepository = artifactRepository,
        ingestionRunRepository = ingestionRunRepository,
        applicationScope = testScope,
    )

    private val runId = UUID.randomUUID()
    private val project1 = UUID.randomUUID()
    private val project2 = UUID.randomUUID()

    @Test
    fun `handleRunFinished triggers auto evaluation for all resolved projects`() {
        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns Optional.empty()
        every { artifactRepository.findProjectIdsByIngestionRunId(runId) } returns setOf(project1, project2)
        coEvery { projectIndustryApi.evaluateIndustryAutomatically(any()) } just runs

        listener.handleRunFinished(RunFinishedEvent(runId))
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project1) }
        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project2) }
    }

    @Test
    fun `handleRunFinished includes projects from reingested artifacts`() {
        val artifactId = UUID.randomUUID()
        val run = IngestionRun(
            id = runId,
            sourceSystem = SourceSystem.GITHUB,
            status = IngestionRunStatus.COMPLETED,
            artifactIdsToReingest = mutableSetOf(artifactId),
        )
        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns Optional.of(run)
        every { artifactRepository.findProjectIdsByIngestionRunId(runId) } returns setOf(project1)
        every { artifactRepository.findProjectIdsByArtifactIdIn(setOf(artifactId)) } returns setOf(project2)
        coEvery { projectIndustryApi.evaluateIndustryAutomatically(any()) } just runs

        listener.handleRunFinished(RunFinishedEvent(runId))
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project1) }
        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project2) }
    }

    @Test
    fun `handleRunFinished does nothing when no projects are associated with the run`() {
        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns Optional.empty()
        every { artifactRepository.findProjectIdsByIngestionRunId(runId) } returns emptySet()

        listener.handleRunFinished(RunFinishedEvent(runId))
        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { projectIndustryApi.evaluateIndustryAutomatically(any()) }
    }

    @Test
    fun `handleRunFinished logs and does not throw when evaluation fails`() {
        every { ingestionRunRepository.findWithAiSyncArtifactIdsById(runId) } returns Optional.empty()
        every { artifactRepository.findProjectIdsByIngestionRunId(runId) } returns setOf(project1)
        coEvery { projectIndustryApi.evaluateIndustryAutomatically(project1) } throws
            IllegalStateException("AI timeout")

        // Must not crash the scope or rethrow
        listener.handleRunFinished(RunFinishedEvent(runId))
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { projectIndustryApi.evaluateIndustryAutomatically(project1) }
    }
}
