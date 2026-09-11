package com.sprintstart.sprintstartbackend.onboarding.blueprint.util

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.BlueprintPathCopyFactory
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.user.external.events.ProjectCreatedEvent
import jakarta.persistence.EntityManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Reacts to cross-module events with blueprint-related side effects.
 *
 * Keeps the blueprint module decoupled from the user/project module by reacting
 * to published events instead of being called directly.
 */
@Component
class BlueprintEventListener(
    private val blueprintPathRepository: BlueprintPathRepository,
    private val blueprintPathCopyFactory: BlueprintPathCopyFactory,
    private val entityManager: EntityManager,
) {
    /**
     * Copies all global blueprint paths into a newly created project.
     *
     * Every global blueprint (project ID is `null`) is deep-copied via
     * [BlueprintPathCopyFactory] with a fresh blueprint key, status
     * [BlueprintStatus.ACTIVE], and version 0, then persisted as a
     * project-specific blueprint owned by the project from the event. Runs in a
     * transaction so all copies are persisted atomically.
     *
     * @param event the event carrying the ID of the newly created project.
     */
    @Transactional
    @EventListener
    fun handleProjectCreatedEvent(event: ProjectCreatedEvent) {
        val projectPaths = blueprintPathRepository
            .findAllByProjectIdIsNull()
            .map {
                blueprintPathCopyFactory.createCopyFrom(
                    path = it,
                    blueprintKey = UUID.randomUUID(),
                    projectId = event.projectId,
                    status = BlueprintStatus.ACTIVE,
                    version = 0,
                )
            }

        projectPaths.forEach(entityManager::persist)
    }
}
