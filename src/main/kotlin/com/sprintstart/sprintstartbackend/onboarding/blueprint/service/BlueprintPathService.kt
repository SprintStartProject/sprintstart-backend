package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.BlueprintPathDraftFactory
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
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class BlueprintPathService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintPathRepository: BlueprintPathRepository,
    private val blueprintPathDraftFactory: BlueprintPathDraftFactory,
) {
    @Transactional(readOnly = true)
    fun getBlueprintPathOverviewsForProjectGroupedByBlueprintKey(
        projectId: UUID,
    ): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathRepository
            .findLatestVersionForEachBlueprintKeyAndProjectId(projectId)
            .map { it.toGetOverviewResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintPathHistoryForProjectByBlueprintKey(
        projectId: UUID,
        blueprintKey: UUID,
    ): List<GetBlueprintPathResponse> {
        return blueprintPathRepository
            .findAllByProjectIdAndBlueprintKeyOrderByVersionDesc(projectId, blueprintKey)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintPathOverviewsForProjectId(projectId: UUID): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathRepository
            .findAllByProjectId(projectId)
            .map { it.toGetOverviewResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintPathByProjectIdAndId(projectId: UUID, pathId: UUID): GetBlueprintPathResponse {
        return blueprintAccessService
            .getAuthorizedPath(projectId, pathId)
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintPath(
        projectId: UUID,
        request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        val path = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = projectId,
            title = request.title,
            description = request.description,
            version = 0,
            revision = 0,
            status = BlueprintStatus.DRAFT,
        )

        return blueprintPathRepository.save(path).toCreateResponse()
    }

    @Transactional
    fun openBlueprintPathDraftByBlueprintKey(projectId: UUID, blueprintKey: UUID): GetBlueprintPathResponse {
        val activePath = blueprintAccessService
            .findActiveForAuthorizedBlueprintKey(projectId, blueprintKey)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No active path found")

        val draft = blueprintAccessService
            .findDraftForAuthorizedBlueprintKey(projectId, blueprintKey)

        return draft?.toGetResponse()
            ?: blueprintPathRepository
                .save(blueprintPathDraftFactory.createDraftFrom(activePath))
                .toGetResponse()
    }

    @Transactional
    fun publishBlueprintPathDraftById(
        projectId: UUID,
        pathId: UUID,
    ): GetBlueprintPathResponse {
        val draft = blueprintAccessService.getAuthorizedDraftPath(projectId, pathId)

        blueprintAccessService
            .findActiveForAuthorizedBlueprintKey(projectId, pathId)
            ?.let { activePath -> activePath.status = draft.status }

        draft.status = BlueprintStatus.ACTIVE

        return draft.toGetResponse()
    }

    @Transactional
    fun rollbackBlueprintPathByBlueprintKey(
        projectId: UUID,
        blueprintKey: UUID,
        rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        val activePath = blueprintAccessService
            .findActiveForAuthorizedBlueprintKey(projectId, blueprintKey)
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
            .getArchivedForAuthorizedBlueprintKey(projectId, blueprintKey, rollbackVersion)

        blueprintPathRepository
            .deleteAllByProjectIdAndBlueprintKeyAndVersionAfter(projectId, blueprintKey, rollbackVersion)
        rollbackPath.status = BlueprintStatus.ACTIVE

        return rollbackPath.toGetResponse()
    }

    @Transactional
    fun updateBlueprintPathById(
        projectId: UUID,
        pathId: UUID,
        request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        val path = blueprintAccessService.getAuthorizedDraftPath(projectId, pathId)

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

    @Transactional
    fun deleteBlueprintPathDraftById(
        projectId: UUID,
        pathId: UUID,
    ) {
        val path = blueprintAccessService.getAuthorizedPath(projectId, pathId)

        if (path.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Path with id: $pathId is not a draft!")
        }

        blueprintPathRepository.delete(path)
    }

    @Transactional
    fun archiveBlueprintPathByBlueprintKey(projectId: UUID, blueprintKey: UUID) {
        val path = blueprintAccessService
            .findActiveForAuthorizedBlueprintKey(projectId, blueprintKey)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No active path found for blueprintKey: $blueprintKey",
            )

        path.status = BlueprintStatus.ARCHIVED

        // find and delet any draft
        blueprintAccessService
            .findDraftForAuthorizedBlueprintKey(projectId, blueprintKey)
            ?.let { blueprintPathRepository.delete(it) }
    }
}

// Todo:
//  - [x] Finish the rest of the data structures with simple services
//      - [x] Step
//      - [x] Questions
//      - [x] Option
//      - [x] Phase
//  - [x] Add an umbrella Blueprint structure that holds all the paths -> needs a Stable Key separate from the ID
//  - [x] Add a check for the revision in every update
//  - [x] Create an endpoint for editing the blueprint with /blueprint/paths/{pathId}/drafts
//      - [x] returns the current opened draft
//      - [x] creates a new deep-copy (draft) of the blueprint and returns it as a draft
//  - [x] change all the update, create and delete calls to use the draft
//  - [x] Create an endpoint to save a draft as the new active and retire the old blueprint
//  - [x] Change the path delete endpoint to a retire
//  - [x] Create Blueprint status enum
//  - [x] Add a revert function to the path
//  - [x] Add an extra endpoint to every blueprint entity with a position
//      -> this should return the complete changed List of entities
//  - [x] Add delete endpoint for drafts
//  - [x] Add role and skill "requirements" to phases
//  - [x] Add an option to just specify a prompt as the phase
//  - [] Make everything tied to a project id
//      - [x] Path
//      - [x] Phase
//      - [x] Step
//      - [x] Resource
//      - [] Task
//      - [x] Requirement
//      - [] Check
//      - [] CheckQuestion
//  - [] Add a for all members option which will add a Task with each members name (only 70% need to be reached)
//  - [] Add the Blueprint -> AI Conversion service and controller
//      - [] Add prompt -> phase service
//  - [] Add a way that Ai could SSE stream a phase or path (via Buddy or Button)
//  - [] Add @PreAutherize and @ResponseStatus to every controller function
//  - [] Add Documentation
//  - [] ( Add an option to make phases be blocked by a previous one or not )
//  - [] ( Add authors to the Blueprint, as a Set with all the people that edited the draft )
//  - [] ( Add filter options to the phase query )
//  -
//  - [] Think about a teamOverview phase with : (Name, roles, Ai worksummary) per person
//      -> TeamMemberProfile (I think i will move this into the user)

/*
Blueprint(
      id: UUID,              // identity of this concrete version
      blueprintKey: UUID,    // stable identity across versions
      version: Int,
      status: DRAFT | ACTIVE | RETIRED,
      @Version revision: Long // optimistic-locking revision
  )

Chat session:
codex resume 019fe621-6d7a-7c92-90a3-d7c2993d7d28

Frontend session:
codex resume 01a0005d-d842-7f40-ab68-a6d9c08d7ead

Errors for requirements:
codex resume 01a01911-9be0-7973-b087-069dc9990e52
 */
