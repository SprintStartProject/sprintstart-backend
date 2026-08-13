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
    private val blueprintPathRepository: BlueprintPathRepository,
    private val blueprintPathDraftFactory: BlueprintPathDraftFactory,
) {
    @Transactional(readOnly = true)
    fun getBlueprintPathOverviews(): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathRepository
            .findAll()
            .map { it.toGetOverviewResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintPathById(pathId: UUID): GetBlueprintPathResponse {
        return blueprintPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found with id: $pathId") }
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintPath(
        request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        val path = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            title = request.title,
            description = request.description,
            version = 0,
            revision = 0,
            status = BlueprintStatus.DRAFT,
        )

        return blueprintPathRepository.save(path).toCreateResponse()
    }

    @Transactional
    fun openBlueprintPathDraftById(pathId: UUID): GetBlueprintPathResponse {
        val path = blueprintPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found with id: $pathId") }

        val draftList = blueprintPathRepository
            .findByBlueprintKeyAndStatus(path.blueprintKey, BlueprintStatus.DRAFT)

        if (draftList.size > 1) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one draft found for blueprintKey: ${path.blueprintKey}, please contact support",
            )
        }

        val draft =
            if (draftList.isEmpty()) {
                blueprintPathRepository.save(blueprintPathDraftFactory.createDraftFrom(path))
            } else {
                draftList.first()
            }

        return draft.toGetResponse()
    }

    @Transactional
    fun publishBlueprintPathDraftById(pathId: UUID): GetBlueprintPathResponse {
        val draft = blueprintPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found with id: $pathId") }

        if (draft.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Selected path has status: ${draft.status}, expected status: ${BlueprintStatus.DRAFT}",
            )
        }

        val currentPathList = blueprintPathRepository
            .findByBlueprintKeyAndStatus(draft.blueprintKey, BlueprintStatus.ACTIVE)

        if (currentPathList.size > 1) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one active path found for blueprintKey: ${draft.blueprintKey}, please contact support",
            )
        }

        if (currentPathList.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "No active path found for blueprintKey: ${draft.blueprintKey}",
            )
        }

        val currentPath = currentPathList.first()
        currentPath.status = BlueprintStatus.ARCHIVED
        blueprintPathRepository.save(currentPath)

        draft.status = BlueprintStatus.ACTIVE
        return blueprintPathRepository.save(draft).toGetResponse()
    }

    @Transactional
    fun updateBlueprintPathById(
        pathId: UUID,
        request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        val path = blueprintPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found with id: $pathId") }

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
    fun deleteBlueprintPathById(pathId: UUID) {
        val path = blueprintPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found with id: $pathId") }

        blueprintPathRepository.delete(path)
    }

//  ========================== Helper methods ==========================
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
//  - [] change all the update, create and delete calls to use the draft
//  - [x] Create an endpoint to save a draft as the new active and retire the old blueprint
//  - [] Change the path delete endpoint to a retire
//  - [x] Create Blueprint status enum
//  - [] Add a revert function to the path
//  - [] ( Add an option to make phases be blocked by a previous one or not )
//  - [] ( Add authors to the Blueprint, as a Set with all the people that edited the draft )
//  - [] ( Add filter options to the phase query )

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
 */
