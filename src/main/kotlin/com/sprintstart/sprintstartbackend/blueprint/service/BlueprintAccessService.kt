package com.sprintstart.sprintstartbackend.blueprint.service

import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintSubGraphNode
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintCheckOptionRepository
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintCheckQuestionRepository
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintPhaseRepository
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintResourceRepository
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintStepRepository
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintSubGraphNodeRepository
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintTaskRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Centralizes scope-aware access to blueprint entities.
 *
 * Every lookup chooses the global or the project-qualified repository path from [BlueprintScope], and editable
 * lookups additionally require the owning path to be a draft. Keeping both checks here stops individual services
 * from reading across project boundaries or mutating active and archived blueprint versions.
 */
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
    private val blueprintSubGraphNodeRepository: BlueprintSubGraphNodeRepository,
) {
    /**
     * The path in [scope]; an id from another scope does not resolve.
     *
     * @throws ResponseStatusException 404 when it does not exist in the scope.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedPath(scope: BlueprintScope, pathId: UUID): BlueprintPath {
        val path = when (scope) {
            BlueprintScope.Global -> blueprintPathRepository.findByProjectIdIsNullAndId(pathId)
            is BlueprintScope.Project -> blueprintPathRepository.findByProjectIdAndId(scope.projectId, pathId)
        }
        return path ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint Path not found for this scope",
        )
    }

    /**
     * The path in [scope], which must be a draft.
     *
     * @throws ResponseStatusException 404 when it is outside the scope, 409 when it is not a draft.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedDraftPath(scope: BlueprintScope, pathId: UUID): BlueprintPath {
        val draft = getAuthorizedPath(scope, pathId)

        requireDraft(draft)
        return draft
    }

    /**
     * The active path of [blueprintKey] in [scope], or null.
     *
     * @throws ResponseStatusException 500 when more than one path is active.
     */
    @Transactional(readOnly = true)
    fun findActiveForAuthorizedBlueprintKey(scope: BlueprintScope, blueprintKey: UUID): BlueprintPath? =
        singleWithStatus(scope, blueprintKey, BlueprintStatus.ACTIVE)

    /**
     * The draft path of [blueprintKey] in [scope], or null.
     *
     * @throws ResponseStatusException 500 when more than one path is a draft.
     */
    @Transactional(readOnly = true)
    fun findDraftForAuthorizedBlueprintKey(scope: BlueprintScope, blueprintKey: UUID): BlueprintPath? =
        singleWithStatus(scope, blueprintKey, BlueprintStatus.DRAFT)

    /**
     * The archived [version] of [blueprintKey] in [scope].
     *
     * @throws ResponseStatusException 500 when that version is missing or duplicated.
     */
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

    /**
     * The phase in [scope]; an id from another scope does not resolve.
     *
     * @throws ResponseStatusException 404 when it does not exist in the scope.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedPhase(scope: BlueprintScope, phaseId: UUID): BlueprintPhase {
        val phase = when (scope) {
            is BlueprintScope.Global -> blueprintPhaseRepository.findByBlueprintPathProjectIdIsNullAndId(phaseId)
            is BlueprintScope.Project -> {
                blueprintPhaseRepository.findByBlueprintPathProjectIdAndId(scope.projectId, phaseId)
            }
        }

        return phase ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint Phase not found for this project",
        )
    }

    /**
     * The phase in [scope], which must also belong to a draft path.
     *
     * @throws ResponseStatusException 404 when it is outside the scope, 409 when its path is not a draft.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedEditablePhase(scope: BlueprintScope, phaseId: UUID): BlueprintPhase {
        val phase = getAuthorizedPhase(scope, phaseId)

        requireDraft(phase.blueprintPath)
        return phase
    }

    /**
     * The step in [scope]; an id from another scope does not resolve.
     *
     * @throws ResponseStatusException 404 when it does not exist in the scope.
     */
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

    /**
     * The step in [scope], which must also belong to a draft path.
     *
     * @throws ResponseStatusException 404 when it is outside the scope, 409 when its path is not a draft.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedEditableStep(scope: BlueprintScope, stepId: UUID): BlueprintStep {
        val step = getAuthorizedStep(scope, stepId)

        requireDraft(step.blueprintPhase.blueprintPath)
        return step
    }

    /**
     * The resource in [scope]; an id from another scope does not resolve.
     *
     * @throws ResponseStatusException 404 when it does not exist in the scope.
     */
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

    /**
     * The resource in [scope], which must also belong to a draft path.
     *
     * @throws ResponseStatusException 404 when it is outside the scope, 409 when its path is not a draft.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedEditableResource(scope: BlueprintScope, resourceId: UUID): BlueprintResource {
        val resource = getAuthorizedResource(scope, resourceId)
        requireDraft(resource.blueprintStep.blueprintPhase.blueprintPath)
        return resource
    }

    /**
     * The task in [scope]; an id from another scope does not resolve.
     *
     * @throws ResponseStatusException 404 when it does not exist in the scope.
     */
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

    /**
     * The task in [scope], which must also belong to a draft path.
     *
     * @throws ResponseStatusException 404 when it is outside the scope, 409 when its path is not a draft.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedEditableTask(scope: BlueprintScope, taskId: UUID): BlueprintTask {
        val task = getAuthorizedTask(scope, taskId)

        requireDraft(task.blueprintStep.blueprintPhase.blueprintPath)
        return task
    }

    /**
     * The check question in [scope]; an id from another scope does not resolve.
     *
     * @throws ResponseStatusException 404 when it does not exist in the scope.
     */
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

    /**
     * The check question in [scope], which must also belong to a draft path.
     *
     * @throws ResponseStatusException 404 when it is outside the scope, 409 when its path is not a draft.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedEditableCheckQuestion(scope: BlueprintScope, questionId: UUID): BlueprintCheckQuestion {
        val question = getAuthorizedCheckQuestion(scope, questionId)

        requireDraft(question.blueprintPhase.blueprintPath)
        return question
    }

    /**
     * The check option in [scope]; an id from another scope does not resolve.
     *
     * @throws ResponseStatusException 404 when it does not exist in the scope.
     */
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

    /**
     * The check option in [scope], which must also belong to a draft path.
     *
     * @throws ResponseStatusException 404 when it is outside the scope, 409 when its path is not a draft.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedEditableCheckOption(scope: BlueprintScope, optionId: UUID): BlueprintCheckOption {
        val option = getAuthorizedCheckOption(scope, optionId)

        requireDraft(option.blueprintCheckQuestion.blueprintPhase.blueprintPath)
        return option
    }

    /**
     * The sub graph node in [scope], which must also belong to a draft path.
     *
     * @throws ResponseStatusException 404 when it is outside the scope, 409 when its path is not a draft.
     */
    @Transactional(readOnly = true)
    fun getAuthorizedEditableSubGraphNode(scope: BlueprintScope, nodeId: UUID): BlueprintSubGraphNode {
        val node = when (scope) {
            is BlueprintScope.Global -> {
                blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(nodeId)
            }

            is BlueprintScope.Project -> {
                blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdAndId(scope.projectId, nodeId)
            }
        } ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Blueprint graph node not found for this project",
        )

        requireDraft(node.blueprintPhase.blueprintPath)

        return node
    }

    private fun singleWithStatus(scope: BlueprintScope, blueprintKey: UUID, status: BlueprintStatus): BlueprintPath? {
        val paths = when (scope) {
            is BlueprintScope.Global ->
                blueprintPathRepository.findByProjectIdNullAndBlueprintKeyAndStatus(blueprintKey, status)

            is BlueprintScope.Project ->
                blueprintPathRepository.findByProjectIdAndBlueprintKeyAndStatus(scope.projectId, blueprintKey, status)
        }

        return when (paths.size) {
            0 -> null

            1 -> paths.single()

            else -> throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one ${status.name.lowercase()} path found for blueprintKey: $blueprintKey, " +
                    "please contact support",
            )
        }
    }

    private fun requireDraft(path: BlueprintPath) {
        if (path.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Blueprint can only be modified while in DRAFT status")
        }
    }
}
