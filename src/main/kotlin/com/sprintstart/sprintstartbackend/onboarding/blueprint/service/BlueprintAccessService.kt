package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintSubGraphNode
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckOptionRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintStepRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintSubGraphNodeRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintTaskRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Centralizes scope-aware access to blueprint entities.
 *
 * Every lookup chooses either the global repository path or a project-qualified repository path from [BlueprintScope].
 * Editable lookups additionally require the owning path to be a draft. Keeping these checks here prevents individual
 * services from accidentally reading across project boundaries or mutating active and archived blueprint versions.
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
     * Returns authorized path.
     *
     * Chooses the global or project repository query from the supplied scope, preventing an identifier from
     * resolving across ownership boundaries.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
     */
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

    /**
     * Returns authorized draft path.
     *
     * First resolves the entity inside the requested ownership boundary, then verifies that its owning path has
     * DRAFT status before returning it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity is outside the scope, or 409 when its path is not a
     *   draft.
     */
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

    /**
     * Finds active for authorized key.
     *
     * Runs the scope-specific active-version query and enforces the invariant that a stable key has at most one
     * active path.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param blueprintKey Stable key shared by every version of a blueprint.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 500 when persisted versions violate the single-result invariant.
     */
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

    /**
     * Finds draft for authorized key.
     *
     * Runs the scope-specific draft query and enforces the invariant that a stable key has at most one draft path.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param blueprintKey Stable key shared by every version of a blueprint.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 500 when persisted versions violate the single-result invariant.
     */
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

    /**
     * Returns archived for authorized key.
     *
     * Runs the scope-specific key-and-version query and requires exactly one matching historical path.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param blueprintKey Stable key shared by every version of a blueprint.
     * @param version Archived blueprint version to resolve.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 500 when the requested historical version is missing or duplicated.
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
     * Returns authorized phase.
     *
     * Chooses the global or project repository query from the supplied scope, preventing an identifier from
     * resolving across ownership boundaries.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
     */
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

    /**
     * Returns authorized editable phase.
     *
     * First resolves the entity inside the requested ownership boundary, then verifies that its owning path has
     * DRAFT status before returning it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity is outside the scope, or 409 when its path is not a
     *   draft.
     */
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

    /**
     * Returns authorized step.
     *
     * Chooses the global or project repository query from the supplied scope, preventing an identifier from
     * resolving across ownership boundaries.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
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
     * Returns authorized editable step.
     *
     * First resolves the entity inside the requested ownership boundary, then verifies that its owning path has
     * DRAFT status before returning it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity is outside the scope, or 409 when its path is not a
     *   draft.
     */
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

    /**
     * Returns authorized resource.
     *
     * Chooses the global or project repository query from the supplied scope, preventing an identifier from
     * resolving across ownership boundaries.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param resourceId Identifier of the blueprint resource.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
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
     * Returns authorized editable resource.
     *
     * First resolves the entity inside the requested ownership boundary, then verifies that its owning path has
     * DRAFT status before returning it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param resourceId Identifier of the blueprint resource.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity is outside the scope, or 409 when its path is not a
     *   draft.
     */
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

    /**
     * Returns authorized task.
     *
     * Chooses the global or project repository query from the supplied scope, preventing an identifier from
     * resolving across ownership boundaries.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param taskId Identifier of the blueprint task.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
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
     * Returns authorized editable task.
     *
     * First resolves the entity inside the requested ownership boundary, then verifies that its owning path has
     * DRAFT status before returning it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param taskId Identifier of the blueprint task.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity is outside the scope, or 409 when its path is not a
     *   draft.
     */
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

    /**
     * Returns authorized check question.
     *
     * Chooses the global or project repository query from the supplied scope, preventing an identifier from
     * resolving across ownership boundaries.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param questionId Identifier of the check question.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
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
     * Returns authorized editable check question.
     *
     * First resolves the entity inside the requested ownership boundary, then verifies that its owning path has
     * DRAFT status before returning it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param questionId Identifier of the check question.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity is outside the scope, or 409 when its path is not a
     *   draft.
     */
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

    /**
     * Returns authorized check option.
     *
     * Chooses the global or project repository query from the supplied scope, preventing an identifier from
     * resolving across ownership boundaries.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param optionId Identifier of the check option.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
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
     * Returns authorized editable check option.
     *
     * First resolves the entity inside the requested ownership boundary, then verifies that its owning path has
     * DRAFT status before returning it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param optionId Identifier of the check option.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity is outside the scope, or 409 when its path is not a
     *   draft.
     */
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

    /**
     * Returns authorized editable sub graph node.
     *
     * First resolves the entity inside the requested ownership boundary, then verifies that its owning path has
     * DRAFT status before returning it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param nodeId Operation input.
     * @return The authorized entity or lookup result.
     * @throws ResponseStatusException With 404 when the entity is outside the scope, or 409 when its path is not a
     *   draft.
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

        if (node.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

        return node
    }
}
