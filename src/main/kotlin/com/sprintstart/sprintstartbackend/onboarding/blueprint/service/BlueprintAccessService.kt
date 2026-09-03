package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckOptionRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckQuestionRepository
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

@Suppress("TooManyFunctions")
@Service
class BlueprintAccessService(
    private val blueprintPathRepository: BlueprintPathRepository,
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
    private val blueprintStepRepository: BlueprintStepRepository,
    private val blueprintResourceRepository: BlueprintResourceRepository,
    private val blueprintTaskRepository: BlueprintTaskRepository,
    private val blueprintCheckQuestionRepository: BlueprintCheckQuestionRepository,
    private val blueprintCheckOptionRepository: BlueprintCheckOptionRepository,
) {
    @Transactional(readOnly = true)
    fun getAuthorizedPath(scope: BlueprintScope, pathId: UUID): BlueprintPath {
        val path = when (scope) {
            BlueprintScope.Global -> {
                blueprintPathRepository.findByProjectIdIsNullAndId(pathId)
            }

            is BlueprintScope.Project -> {
                blueprintPathRepository.findByProjectIdAndId(scope.projectId, pathId)
            }
        }
        return path ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint Path not found for this scope",
        )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedDraftPath(scope: BlueprintScope, pathId: UUID): BlueprintPath {
        val draft = getAuthorizedPath(scope, pathId)

        if (draft.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return draft
    }

    @Transactional(readOnly = true)
    fun findActiveForAuthorizedBlueprintKey(scope: BlueprintScope, blueprintKey: UUID): BlueprintPath? {
        val activePathList = when (scope) {
            is BlueprintScope.Global -> {
                blueprintPathRepository
                    .findByProjectIdNullAndBlueprintKeyAndStatus(blueprintKey, BlueprintStatus.ACTIVE)
            }

            is BlueprintScope.Project -> {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(scope.projectId, blueprintKey, BlueprintStatus.ACTIVE)
            }
        }

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
    fun findDraftForAuthorizedBlueprintKey(scope: BlueprintScope, blueprintKey: UUID): BlueprintPath? {
        val draftList = when (scope) {
            is BlueprintScope.Global -> {
                blueprintPathRepository
                    .findByProjectIdNullAndBlueprintKeyAndStatus(blueprintKey, BlueprintStatus.DRAFT)
            }

            is BlueprintScope.Project -> {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(scope.projectId, blueprintKey, BlueprintStatus.DRAFT)
            }
        }

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
    fun getArchivedForAuthorizedBlueprintKey(scope: BlueprintScope, blueprintKey: UUID, version: Int): BlueprintPath {
        val archivedList = when (scope) {
            is BlueprintScope.Global -> {
                blueprintPathRepository
                    .findByProjectIdNullAndBlueprintKeyAndVersion(blueprintKey, version)
            }

            is BlueprintScope.Project -> {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndVersion(scope.projectId, blueprintKey, version)
            }
        }

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
    fun getAuthorizedPhase(scope: BlueprintScope, phaseId: UUID): BlueprintPhase {
        val phase = when (scope) {
            is BlueprintScope.Global -> {
                blueprintPhaseRepository.findByBlueprintPathProjectIdIsNullAndId(phaseId)
            }

            is BlueprintScope.Project -> {
                blueprintPhaseRepository.findByBlueprintPathProjectIdAndId(scope.projectId, phaseId)
            }
        }

        return phase ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint Phase not found for this project",
        )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditablePhase(scope: BlueprintScope, phaseId: UUID): BlueprintPhase {
        val phase = getAuthorizedPhase(scope, phaseId)

        if (phase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return phase
    }

    @Transactional(readOnly = true)
    fun getAuthorizedStep(scope: BlueprintScope, stepId: UUID): BlueprintStep {
        val step = when (scope) {
            is BlueprintScope.Global -> {
                blueprintStepRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(stepId)
            }

            is BlueprintScope.Project -> {
                blueprintStepRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdAndId(scope.projectId, stepId)
            }
        }
        return step ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint step not found for this project",
        )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditableStep(scope: BlueprintScope, stepId: UUID): BlueprintStep {
        val step = getAuthorizedStep(scope, stepId)

        if (step.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return step
    }

    @Transactional(readOnly = true)
    fun getAuthorizedResource(scope: BlueprintScope, resourceId: UUID): BlueprintResource {
        val resource = when (scope) {
            is BlueprintScope.Global -> {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(resourceId)
            }

            is BlueprintScope.Project -> {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(scope.projectId, resourceId)
            }
        }
        return resource ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint resource not found for this project",
        )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditableResource(scope: BlueprintScope, resourceId: UUID): BlueprintResource {
        val resource = getAuthorizedResource(scope, resourceId)
        if (resource.blueprintStep.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return resource
    }

    @Transactional(readOnly = true)
    fun getAuthorizedTask(scope: BlueprintScope, taskId: UUID): BlueprintTask {
        val task = when (scope) {
            is BlueprintScope.Global -> {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(taskId)
            }

            is BlueprintScope.Project -> {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(scope.projectId, taskId)
            }
        }
        return task ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint task not found for this project",
        )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditableTask(scope: BlueprintScope, taskId: UUID): BlueprintTask {
        val task = getAuthorizedTask(scope, taskId)

        if (task.blueprintStep.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return task
    }

    @Transactional(readOnly = true)
    fun getAuthorizedCheckQuestion(scope: BlueprintScope, questionId: UUID): BlueprintCheckQuestion {
        val question = when (scope) {
            is BlueprintScope.Global -> {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(questionId)
            }

            is BlueprintScope.Project -> {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdAndId(scope.projectId, questionId)
            }
        }
        return question ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint check question not found for this project",
        )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditableCheckQuestion(scope: BlueprintScope, questionId: UUID): BlueprintCheckQuestion {
        val question = getAuthorizedCheckQuestion(scope, questionId)

        if (question.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return question
    }

    @Transactional(readOnly = true)
    fun getAuthorizedCheckOption(scope: BlueprintScope, optionId: UUID): BlueprintCheckOption {
        val option = when (scope) {
            is BlueprintScope.Global -> {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdIsNullAndId(optionId)
            }

            is BlueprintScope.Project -> {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdAndId(scope.projectId, optionId)
            }
        }
        return option ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint check option not found for this project",
        )
    }

    @Transactional(readOnly = true)
    fun getAuthorizedEditableCheckOption(scope: BlueprintScope, optionId: UUID): BlueprintCheckOption {
        val option = getAuthorizedCheckOption(scope, optionId)

        if (option.blueprintCheckQuestion.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }
        return option
    }
}
