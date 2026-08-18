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
import java.util.Optional
import java.util.UUID

@Service
class BlueprintPathService(
    private val blueprintPathRepository: BlueprintPathRepository,
    private val blueprintPathDraftFactory: BlueprintPathDraftFactory,
) {
    @Transactional(readOnly = true)
    fun getBlueprintPathOverviewsByBlueprintKeys(): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathRepository
            .findLatestVersionForEachBlueprintKey()
            .map { it.toGetOverviewResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintPathHistoryByBlueprintKey(blueprintKey: UUID): List<GetBlueprintPathResponse> {
        return blueprintPathRepository
            .findAllByBlueprintKeyOrderByVersionDesc(blueprintKey)
            .map { it.toGetResponse() }
    }

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

        val draft = getDraftBlueprintPath(path.blueprintKey)

        return if (draft.isEmpty) {
            blueprintPathRepository.save(blueprintPathDraftFactory.createDraftFrom(path)).toGetResponse()
        } else {
            draft.get().toGetResponse()
        }
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

        val pathForBlueprintKeyCount = blueprintPathRepository.countByBlueprintKey(draft.blueprintKey)

        if (pathForBlueprintKeyCount > 1) {
            val currentPath = getActiveBlueprintPath(draft.blueprintKey)
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "No active path found for blueprintKey: ${draft.blueprintKey}",
                    )
                }
            currentPath.status = BlueprintStatus.ARCHIVED
            blueprintPathRepository.save(currentPath)
        }

        draft.status = BlueprintStatus.ACTIVE

        return draft.toGetResponse()
    }

    @Transactional
    fun rollbackBlueprintPathByBlueprintKey(
        blueprintKey: UUID,
        rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        val activePath = getActiveBlueprintPath(blueprintKey)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "No active path found for blueprintKey: $blueprintKey")
            }

        if (rollbackVersion < 0 || rollbackVersion >= activePath.version) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Rollback version has to be between 0 and ${activePath.version - 1}",
            )
        }

        val rollbackPath = getArchivedBlueprintPath(blueprintKey, rollbackVersion)

        blueprintPathRepository.deleteAllByBlueprintKeyAndVersionAfter(blueprintKey, rollbackVersion)
        rollbackPath.status = BlueprintStatus.ACTIVE

        return rollbackPath.toGetResponse()
    }

    @Transactional
    fun updateBlueprintPathById(
        pathId: UUID,
        request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        val path = blueprintPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found with id: $pathId") }

        if (path.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

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
    fun deleteBlueprintPathDraftById(pathId: UUID) {
        val path = blueprintPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found with id: $pathId") }

        if (path.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Path with id: $pathId is not a draft!")
        }

        blueprintPathRepository.delete(path)
    }

    @Transactional
    fun archiveBlueprintPathByBlueprintKey(blueprintKey: UUID) {
        val path = getActiveBlueprintPath(blueprintKey)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No active path found for blueprintKey: $blueprintKey",
                )
            }

        path.status = BlueprintStatus.ARCHIVED

        // find and delet any draft
        getDraftBlueprintPath(blueprintKey)
            .ifPresent { blueprintPathRepository.delete(it) }
    }

//  ========================== Helper methods ==========================

    private fun getActiveBlueprintPath(blueprintKey: UUID): Optional<BlueprintPath> {
        val activePathList = blueprintPathRepository
            .findByBlueprintKeyAndStatus(blueprintKey, BlueprintStatus.ACTIVE)

        if (activePathList.isEmpty()) return Optional.empty()

        if (activePathList.size > 1) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one active path found for blueprintKey: $blueprintKey, please contact support",
            )
        }

        return Optional.of(activePathList.first())
    }

    private fun getDraftBlueprintPath(blueprintKey: UUID): Optional<BlueprintPath> {
        val draftList = blueprintPathRepository
            .findByBlueprintKeyAndStatus(blueprintKey, BlueprintStatus.DRAFT)

        if (draftList.isEmpty()) return Optional.empty()

        if (draftList.size > 1) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one draft path found for blueprintKey: $blueprintKey, please contact support",
            )
        }

        return Optional.of(draftList.first())
    }

    private fun getArchivedBlueprintPath(blueprintKey: UUID, version: Int): BlueprintPath {
        val archivedList = blueprintPathRepository
            .findByBlueprintKeyAndVersion(blueprintKey, version)

        if (archivedList.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Archived blueprint path with blueprintKey: $blueprintKey and version: $version not found",
            )
        }

        if (archivedList.size > 1) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "More than one archived path found for blueprintKey: " +
                    "$blueprintKey and version $version, please contact support",
            )
        }

        return archivedList.first()
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
//  - [] Add role and skill "requirements" to phases
//  - [] Add an option to just specify a prompt as the phase and the ai will created dynamically
//  - [] Think about a teamoverview phase with : (Name, roles, Ai worksummary) per person
//  - [] Add a for all members option which will add a Task with the each members name (only 70% need to be reached)
//  - [] Add the Blueprint -> AI Conversion service and controller
//  - [] Add a way that Ai could SSE stream a phase or path (via Buddy or Button)
//  - [] Add @PreAutherize and @ResponseStatus to every controller function
//  - [] Add Documentation
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

Frontend session:
codex resume 01a0005d-d842-7f40-ab68-a6d9c08d7ead
 */
