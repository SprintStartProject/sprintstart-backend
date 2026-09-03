package com.sprintstart.sprintstartbackend.onboarding.blueprint.listener

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.BlueprintPathCopyFactory
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.user.external.events.ProjectCreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class BlueprintEventListener(
    private val blueprintPathRepository: BlueprintPathRepository,
    private val blueprintPathCopyFactory: BlueprintPathCopyFactory,
) {
    @Transactional
    @EventListener
    fun handleProjectCreatedEvent(event: ProjectCreatedEvent) {
        // create a copy of the available public blueprints and save them as project blueprints
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

        blueprintPathRepository.saveAll(projectPaths)
    }
}
