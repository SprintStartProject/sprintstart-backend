package com.sprintstart.sprintstartbackend.blueprint.service

import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintApi
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.blueprint.model.mapper.toSnapshot
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.shared.ids.BlueprintId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Serves [BlueprintApi] from the blueprint repository, snapshotting inside a read transaction. */
@Service
class BlueprintApiService(
    private val blueprintPathRepository: BlueprintPathRepository,
) : BlueprintApi {
    @Transactional(readOnly = true)
    override fun projectIdOf(blueprintId: BlueprintId): ProjectId? =
        blueprintPathRepository
            .findById(blueprintId.value)
            .orElse(null)
            ?.projectId
            ?.let(::ProjectId)

    @Transactional(readOnly = true)
    override fun activeBlueprintCount(projectId: ProjectId): Long =
        blueprintPathRepository.countByProjectIdAndStatus(projectId.value, BlueprintStatus.ACTIVE)

    @Transactional(readOnly = true)
    override fun activeBlueprints(projectId: ProjectId): List<BlueprintSnapshot> =
        blueprintPathRepository
            .findAllByProjectIdAndStatus(projectId.value, BlueprintStatus.ACTIVE)
            .map { it.toSnapshot() }

    @Transactional(readOnly = true)
    override fun snapshot(blueprintId: BlueprintId): BlueprintSnapshot? =
        blueprintPathRepository.findById(blueprintId.value).orElse(null)?.toSnapshot()
}
