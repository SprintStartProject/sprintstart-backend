package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.CreateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhasePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.GetBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhasePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.ranges.contains

@Service
class BlueprintPhaseService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
    private val blueprintPathRepository: BlueprintPathRepository,
) {
    @Transactional(readOnly = true)
    fun getBlueprintPhasesForPath(
        projectId: UUID,
        pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return blueprintPhaseRepository
            .findAllByProjectIdAndBlueprintPathId(projectId, pathId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintPhaseById(
        projectId: UUID,
        phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintAccessService
            .getAuthorizedPhase(projectId, phaseId)
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintPhaseForPath(
        projectId: UUID,
        pathId: UUID,
        request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        val path = blueprintAccessService.getAuthorizedDraftPath(projectId, pathId)

        shiftPhasesRight(path, request)

        val phase = BlueprintPhase(
            blueprintPath = path,
            position = request.position,
            title = request.title,
            description = request.description,
            aiPrompt = request.aiPrompt,
            type = request.type,
        )

        return blueprintPhaseRepository.save(phase).toCreateResponse()
    }

    @Transactional
    fun updateBlueprintPhaseById(
        projectId: UUID,
        phaseId: UUID,
        request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(projectId, phaseId)

        if (phase.revision != request.revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint phase has been modified by another request. Please reload and try again.",
            )
        }

        shiftPhasesBetween(phase, request.position)

        phase.position = request.position
        phase.title = request.title
        phase.description = request.description
        phase.aiPrompt = request.aiPrompt
        phase.type = request.type

        return blueprintPhaseRepository.save(phase).toUpdateResponse()
    }

    @Transactional
    fun updateBlueprintPhasePositionById(
        projectId: UUID,
        phaseId: UUID,
        request: UpdateBlueprintPhasePositionRequest,
    ): List<UpdateBlueprintPhasePositionResponse> {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(projectId, phaseId)

        if (phase.revision != request.revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint phase has been modified by another request. Please reload and try again.",
            )
        }

        val shiftedPhases = shiftPhasesBetween(phase, request.position)
        phase.position = request.position
        shiftedPhases.add(phase)
        blueprintPhaseRepository.saveAllAndFlush(shiftedPhases)

        return shiftedPhases.map { it.toUpdatePositionResponse() }
    }

    @Transactional
    fun deleteBlueprintPhaseById(
        projectId: UUID,
        phaseId: UUID,
    ) {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(projectId, phaseId)

        blueprintPhaseRepository.delete(phase)
    }

//  ========================== Helper Methods ==========================

    private fun shiftPhasesRight(
        blueprintPath: BlueprintPath,
        request: CreateBlueprintPhaseRequest,
    ) {
        val phaseCount = blueprintPhaseRepository.countByBlueprintPathId(blueprintPath.id)

        if (request.position !in 0..phaseCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $phaseCount",
            )
        }

        val phasesToShift = blueprintPhaseRepository
            .findAllByBlueprintPathIdAndPositionGreaterThanEqualOrderByPositionDesc(
                blueprintPath.id,
                request.position,
            )

        phasesToShift.forEach { it.position += 1 }
    }

    private fun shiftPhasesBetween(
        blueprintPhase: BlueprintPhase,
        newPosition: Int,
    ): MutableList<BlueprintPhase> {
        val phaseCount = blueprintPhaseRepository.countByBlueprintPathId(blueprintPhase.blueprintPath.id)

        if (newPosition !in 0 until phaseCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${phaseCount - 1}")
        }

        val oldPosition = blueprintPhase.position
        var phasesToShift: MutableList<BlueprintPhase> = mutableListOf()

        if (oldPosition < newPosition) {
            phasesToShift = blueprintPhaseRepository
                .findAllByBlueprintPathIdAndPositionBetween(
                    blueprintPhase.blueprintPath.id,
                    oldPosition + 1,
                    newPosition,
                )

            phasesToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            phasesToShift = blueprintPhaseRepository
                .findAllByBlueprintPathIdAndPositionBetween(
                    blueprintPhase.blueprintPath.id,
                    newPosition,
                    oldPosition - 1,
                )

            phasesToShift.forEach { it.position += 1 }
        }

        return phasesToShift
    }
}
