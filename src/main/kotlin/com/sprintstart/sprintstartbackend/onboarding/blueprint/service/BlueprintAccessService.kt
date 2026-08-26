package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class BlueprintAccessService(
    private val blueprintPathRepository: BlueprintPathRepository,
) {
    fun getAuthorizedPath(projectId: UUID, pathId: UUID): BlueprintPath {
        return blueprintPathRepository.findByProjectIdAndId(projectId, pathId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Blueprint step not found for this project",
            )
    }

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
}
