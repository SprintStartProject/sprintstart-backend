package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintStepRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintTaskRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class BlueprintAccessService(
    private val blueprintPathRepository: BlueprintPathRepository,
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
    private val blueprintStepRepository: BlueprintStepRepository,
    private val blueprintResourceRepository: BlueprintResourceRepository,
    private val blueprintTaskRepository: BlueprintTaskRepository,
) {
    @Transactional(readOnly = true)
    fun getAuthorizedPath(projectId: UUID, pathId: UUID): BlueprintPath {
        return blueprintPathRepository.findByProjectIdAndId(projectId, pathId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Blueprint Path not found for this project",
            )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedDraftPath(projectId: UUID, pathId: UUID): BlueprintPath {
        val draft = getAuthorizedPath(projectId, pathId)

        if (draft.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return draft
    }

    @Transactional(readOnly = true)
    fun findActiveForAuthorizedBlueprintKey(projectId: UUID, blueprintKey: UUID): BlueprintPath? {
        val activePathList = blueprintPathRepository
            .findByProjectIdAndBlueprintKeyAndStatus(projectId, blueprintKey, BlueprintStatus.ACTIVE)

        return when (activePathList.size) {
            0 -> null

            1 -> activePathList.single()

            else -> throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one active path found for blueprintKey: $blueprintKey, please contact support",
            )
        }
    }

    @Transactional(readOnly = true)
    fun findDraftForAuthorizedBlueprintKey(projectId: UUID, blueprintKey: UUID): BlueprintPath? {
        val draftList = blueprintPathRepository
            .findByProjectIdAndBlueprintKeyAndStatus(projectId, blueprintKey, BlueprintStatus.DRAFT)

        return when (draftList.size) {
            0 -> null

            1 -> draftList.single()

            else -> throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one draft path found for blueprintKey: $blueprintKey, please contact support",
            )
        }
    }

    @Transactional(readOnly = true)
    fun getArchivedForAuthorizedBlueprintKey(projectId: UUID, blueprintKey: UUID, version: Int): BlueprintPath {
        val archivedList = blueprintPathRepository
            .findByProjectIdAndBlueprintKeyAndVersion(projectId, blueprintKey, version)

        return when (archivedList.size) {
            0 -> throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Archived blueprint path with blueprintKey: $blueprintKey and version: $version not found",
            )

            1 -> archivedList.single()

            else -> throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one archived path found for blueprintKey: " +
                    "$blueprintKey and version $version, please contact support",
            )
        }
    }

    @Transactional(readOnly = true)
    fun getAuthorizedPhase(projectId: UUID, phaseId: UUID): BlueprintPhase {
        return blueprintPhaseRepository
            .findByBlueprintPathProjectIdAndId(projectId, phaseId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Blueprint Phase not found for this project",
            )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditablePhase(projectId: UUID, phaseId: UUID): BlueprintPhase {
        val phase = getAuthorizedPhase(projectId, phaseId)

        if (phase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return phase
    }

    @Transactional(readOnly = true)
    fun getAuthorizedStep(projectId: UUID, stepId: UUID): BlueprintStep {
        return blueprintStepRepository
            .findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, stepId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Blueprint step not found for this project",
            )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditableStep(projectId: UUID, stepId: UUID): BlueprintStep {
        val step = getAuthorizedStep(projectId, stepId)

        if (step.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return step
    }

    @Transactional(readOnly = true)
    fun getAuthorizedResource(projectId: UUID, resourceId: UUID): BlueprintResource {
        return blueprintResourceRepository
            .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(projectId, resourceId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Blueprint resource not found for this project",
            )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditableResource(projectId: UUID, resourceId: UUID): BlueprintResource {
        val resource = getAuthorizedResource(projectId, resourceId)
        if (resource.blueprintStep.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return resource
    }

    @Transactional(readOnly = true)
    fun getAuthorizedTask(projectId: UUID, taskId: UUID): BlueprintTask {
        return blueprintTaskRepository
            .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(projectId, taskId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Blueprint task not found for this project",
            )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditableTask(projectId: UUID, taskId: UUID): BlueprintTask {
        val task = getAuthorizedTask(projectId, taskId)

        if (task.blueprintStep.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return task
    }
}
