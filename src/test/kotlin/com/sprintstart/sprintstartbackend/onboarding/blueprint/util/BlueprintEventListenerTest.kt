package com.sprintstart.sprintstartbackend.onboarding.blueprint.util

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.BlueprintPathCopyFactory
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.user.external.events.ProjectCreatedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class BlueprintEventListenerTest {
    private val blueprintPathRepository: BlueprintPathRepository = mockk()
    private val blueprintPathCopyFactory: BlueprintPathCopyFactory = mockk()
    private val entityManager: EntityManager = mockk(relaxed = true)

    private val listener = BlueprintEventListener(
        blueprintPathRepository,
        blueprintPathCopyFactory,
        entityManager,
    )

    private val projectId = UUID.randomUUID()
    private val event = ProjectCreatedEvent(projectId = projectId)

    private fun stubCopyFactory() {
        every {
            blueprintPathCopyFactory.createCopyFrom(any(), any(), any(), any(), any())
        } answers {
            BlueprintPath(
                blueprintKey = secondArg(),
                projectId = thirdArg(),
                title = "copy",
                status = arg(3),
                version = arg(4),
            )
        }
    }

    @Test
    fun `copies every active global blueprint into the new project as active version zero`() {
        val globalPath1 = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            title = "Global 1",
            status = BlueprintStatus.ACTIVE,
        )
        val globalPath2 = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            title = "Global 2",
            status = BlueprintStatus.ACTIVE,
        )
        every {
            blueprintPathRepository.findAllByProjectIdIsNullAndStatus(BlueprintStatus.ACTIVE)
        } returns listOf(globalPath1, globalPath2)
        stubCopyFactory()

        listener.handleProjectCreatedEvent(event)

        verify(exactly = 1) {
            blueprintPathCopyFactory.createCopyFrom(globalPath1, any(), projectId, BlueprintStatus.ACTIVE, 0)
        }
        verify(exactly = 1) {
            blueprintPathCopyFactory.createCopyFrom(globalPath2, any(), projectId, BlueprintStatus.ACTIVE, 0)
        }

        val persisted = mutableListOf<BlueprintPath>()
        verify(exactly = 2) { entityManager.persist(capture(persisted)) }

        assertEquals(2, persisted.size)
        persisted.forEach { copy ->
            assertEquals(projectId, copy.projectId)
            assertEquals(BlueprintStatus.ACTIVE, copy.status)
            assertEquals(0, copy.version)
        }
        assertNotEquals(persisted[0].blueprintKey, persisted[1].blueprintKey)
    }

    @Test
    fun `copies only the latest version when a blueprint key has several active rows`() {
        val blueprintKey = UUID.randomUUID()
        val oldActive = BlueprintPath(
            blueprintKey = blueprintKey,
            title = "Old active",
            version = 0,
            status = BlueprintStatus.ACTIVE,
        )
        val latestActive = BlueprintPath(
            blueprintKey = blueprintKey,
            title = "Latest active",
            version = 1,
            status = BlueprintStatus.ACTIVE,
        )
        every {
            blueprintPathRepository.findAllByProjectIdIsNullAndStatus(BlueprintStatus.ACTIVE)
        } returns listOf(oldActive, latestActive)
        stubCopyFactory()

        listener.handleProjectCreatedEvent(event)

        verify(exactly = 0) {
            blueprintPathCopyFactory.createCopyFrom(oldActive, any(), any(), any(), any())
        }
        verify(exactly = 1) {
            blueprintPathCopyFactory.createCopyFrom(latestActive, any(), projectId, BlueprintStatus.ACTIVE, 0)
        }
        verify(exactly = 1) { entityManager.persist(any()) }
    }

    @Test
    fun `queries only active global blueprints so drafts and archived versions are never copied`() {
        every {
            blueprintPathRepository.findAllByProjectIdIsNullAndStatus(BlueprintStatus.ACTIVE)
        } returns emptyList()

        listener.handleProjectCreatedEvent(event)

        verify(exactly = 1) { blueprintPathRepository.findAllByProjectIdIsNullAndStatus(BlueprintStatus.ACTIVE) }
        verify(exactly = 0) { blueprintPathRepository.findAllByProjectIdIsNull() }
        verify(exactly = 0) { blueprintPathCopyFactory.createCopyFrom(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { entityManager.persist(any()) }
    }
}
