package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.BlueprintPathCopyFactory
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetOverviewResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.CreateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.UpdateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.CreateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathOverviewResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.UpdateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Manages the lifecycle and version history of blueprint paths.
 *
 * Paths are grouped by a stable blueprint key and exist as draft, active, or archived versions inside either global or
 * project scope. This service owns creation, draft copying, publication, rollback, archival, revision validation, and
 * mapping persistence entities to API responses.
 */
@Service
class BlueprintPathService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintPathRepository: BlueprintPathRepository,
    private val blueprintPathCopyFactory: BlueprintPathCopyFactory,
    private val entityManager: EntityManager,
) {
    /**
     * Returns path overviews grouped by key.
     *
     * Runs the scope-specific latest-version query and maps one overview per stable blueprint key.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @return The mapped result of the operation.
     */
    @Transactional(readOnly = true)
    fun getBlueprintPathOverviewsGroupedByBlueprintKey(
        scope: BlueprintScope,
    ): List<GetBlueprintPathOverviewResponse> {
        val paths = when (scope) {
            is BlueprintScope.Global -> {
                blueprintPathRepository.findLatestVersionForEachBlueprintKeyAndProjectIdIsNull()
            }

            is BlueprintScope.Project -> {
                blueprintPathRepository.findLatestVersionForEachBlueprintKeyAndProjectId(scope.projectId)
            }
        }
        return paths.map { it.toGetOverviewResponse() }
    }

    /**
     * Returns path history by key.
     *
     * Loads all versions for the stable key inside the requested scope in descending version order.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param blueprintKey Stable key shared by every version of a blueprint.
     * @return The mapped result of the operation.
     */
    @Transactional(readOnly = true)
    fun getBlueprintPathHistoryByBlueprintKey(
        scope: BlueprintScope,
        blueprintKey: UUID,
    ): List<GetBlueprintPathResponse> {
        val paths = when (scope) {
            is BlueprintScope.Global -> {
                blueprintPathRepository
                    .findAllByProjectIdNullAndBlueprintKeyOrderByVersionDesc(blueprintKey)
            }

            is BlueprintScope.Project -> {
                blueprintPathRepository
                    .findAllByProjectIdAndBlueprintKeyOrderByVersionDesc(scope.projectId, blueprintKey)
            }
        }
        return paths.map { it.toGetResponse() }
    }

    // remove soon

    /**
     * Returns path overviews for project id.
     *
     * Loads all blueprint paths owned by the project. This legacy query does not group versions by stable key.
     *
     * @param projectId Identifier of the owning project.
     * @return The mapped result of the operation.
     */
    @Transactional(readOnly = true)
    fun getBlueprintPathOverviewsForProjectId(projectId: UUID): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathRepository
            .findAllByProjectId(projectId)
            .map { it.toGetOverviewResponse() }
    }

    /**
     * Returns path by id.
     *
     * Uses the access service to enforce the ownership scope before mapping the path.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the path does not exist in the requested scope.
     */
    @Transactional(readOnly = true)
    fun getBlueprintPathById(scope: BlueprintScope, pathId: UUID): GetBlueprintPathResponse {
        return blueprintAccessService
            .getAuthorizedPath(scope, pathId)
            .toGetResponse()
    }

    /**
     * Creates path.
     *
     * Creates version zero in DRAFT status and derives project ownership directly from the supplied scope.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     */
    @Transactional
    fun createBlueprintPath(
        scope: BlueprintScope,
        request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        val path = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = when (scope) {
                is BlueprintScope.Global -> null
                is BlueprintScope.Project -> scope.projectId
            },
            title = request.title,
            description = request.description,
            version = 0,
            revision = 0,
            status = BlueprintStatus.DRAFT,
        )

        return blueprintPathRepository.save(path).toCreateResponse()
    }

    /**
     * Opens path draft by key.
     *
     * Returns an existing draft when present; otherwise deep-copies the active aggregate into the next draft version.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param blueprintKey Stable key shared by every version of a blueprint.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when no active version exists for the key.
     */
    @Transactional
    fun openBlueprintPathDraftByBlueprintKey(
        scope: BlueprintScope,
        blueprintKey: UUID,
    ): GetBlueprintPathResponse {
        val activePath = blueprintAccessService
            .findActiveForAuthorizedBlueprintKey(scope, blueprintKey)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No active path found")

        val draft = blueprintAccessService
            .findDraftForAuthorizedBlueprintKey(scope, blueprintKey)

        if (draft != null) {
            return draft.toGetResponse()
        }

        val copy = blueprintPathCopyFactory.createCopyFrom(
            path = activePath,
            blueprintKey = blueprintKey,
            projectId = activePath.projectId,
            status = BlueprintStatus.DRAFT,
            version = activePath.version + 1,
        )

        entityManager.persist(copy)
        entityManager.flush()
        return copy.toGetResponse()
    }

    /**
     * Publishes path draft by id.
     *
     * Requires a draft, archives the current active version, and promotes the draft in the same transaction.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the draft or active version is missing, or 409 when the supplied
     *   path is not a draft.
     */
    @Transactional
    fun publishBlueprintPathDraftById(
        scope: BlueprintScope,
        pathId: UUID,
    ): GetBlueprintPathResponse {
        val draft = blueprintAccessService.getAuthorizedDraftPath(scope, pathId)

        val activePath = blueprintAccessService.findActiveForAuthorizedBlueprintKey(
            scope,
            draft.blueprintKey,
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No active path found")

        activePath.status = BlueprintStatus.ARCHIVED
        draft.status = BlueprintStatus.ACTIVE

        return draft.toGetResponse()
    }

    /**
     * Rolls back path by key.
     *
     * Validates that the requested version predates the active one, deletes later versions, and reactivates the
     * selected archive.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param blueprintKey Stable key shared by every version of a blueprint.
     * @param rollbackVersion Earlier archived version that should become active.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid rollback version, 404 when no active version exists, or
     *   500 when archived history is inconsistent.
     */
    @Transactional
    fun rollbackBlueprintPathByBlueprintKey(
        scope: BlueprintScope,
        blueprintKey: UUID,
        rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        val activePath = blueprintAccessService
            .findActiveForAuthorizedBlueprintKey(scope, blueprintKey)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No active path found for blueprintKey: $blueprintKey",
            )

        if (rollbackVersion < 0 || rollbackVersion >= activePath.version) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Rollback version has to be between 0 and ${activePath.version - 1}",
            )
        }

        val rollbackPath = blueprintAccessService
            .getArchivedForAuthorizedBlueprintKey(scope, blueprintKey, rollbackVersion)

        when (scope) {
            is BlueprintScope.Global -> {
                blueprintPathRepository
                    .deleteAllByProjectIdIsNullAndBlueprintKeyAndVersionAfter(blueprintKey, rollbackVersion)
            }

            is BlueprintScope.Project -> {
                blueprintPathRepository
                    .deleteAllByProjectIdAndBlueprintKeyAndVersionAfter(scope.projectId, blueprintKey, rollbackVersion)
            }
        }

        rollbackPath.status = BlueprintStatus.ACTIVE

        return rollbackPath.toGetResponse()
    }

    /**
     * Updates path by id.
     *
     * Requires a scoped draft and matching revision before replacing its mutable metadata.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the path is missing, or 409 when it is not a draft or its revision
     *   is stale.
     */
    @Transactional
    fun updateBlueprintPathById(
        scope: BlueprintScope,
        pathId: UUID,
        request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        val path = blueprintAccessService.getAuthorizedDraftPath(scope, pathId)

        if (path.revision != request.revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint path has been modified by another request. Please reload and try again.",
            )
        }

        path.title = request.title
        path.description = request.description

        return blueprintPathRepository.save(path).toUpdateResponse()
    }

    /**
     * Deletes path draft by id.
     *
     * Deletes the scoped path only when it is still a draft.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @throws ResponseStatusException With 404 when the path is missing, or 400 when it is not a draft.
     */
    @Transactional
    fun deleteBlueprintPathDraftById(
        scope: BlueprintScope,
        pathId: UUID,
    ) {
        val path = blueprintAccessService.getAuthorizedPath(scope, pathId)

        if (path.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Path with id: $pathId is not a draft!")
        }

        blueprintPathRepository.delete(path)
    }

    /**
     * Archives path by key.
     *
     * Archives the active version and deletes any unpublished draft for the same key and scope.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param blueprintKey Stable key shared by every version of a blueprint.
     * @throws ResponseStatusException With 404 when no active version exists for the key.
     */
    @Transactional
    fun archiveBlueprintPathByBlueprintKey(scope: BlueprintScope, blueprintKey: UUID) {
        val path = blueprintAccessService
            .findActiveForAuthorizedBlueprintKey(scope, blueprintKey)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No active path found for blueprintKey: $blueprintKey",
            )

        path.status = BlueprintStatus.ARCHIVED

        // find and delet any draft
        blueprintAccessService
            .findDraftForAuthorizedBlueprintKey(scope, blueprintKey)
            ?.let { blueprintPathRepository.delete(it) }
    }
}
