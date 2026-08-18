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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.ranges.contains

@Service
class BlueprintPhaseService(
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
    private val blueprintPathRepository: BlueprintPathRepository,
) {
    @Transactional(readOnly = true)
    fun getBlueprintPhasesForPath(
        @PathVariable pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return blueprintPhaseRepository
            .findAllByBlueprintPathId(pathId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintPhaseById(
        phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found with id: $phaseId") }
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintPhaseForPath(
        pathId: UUID,
        request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        val path = blueprintPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found: $pathId") }

        if (path.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

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
        phaseId: UUID,
        request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        val phase = blueprintPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found with id: $phaseId") }

        if (phase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

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
        phaseId: UUID,
        request: UpdateBlueprintPhasePositionRequest,
    ): List<UpdateBlueprintPhasePositionResponse> {
        val phase = blueprintPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found with id: $phaseId") }

        if (phase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

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
        phaseId: UUID,
    ) {
        val phase = blueprintPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found with id: $phaseId") }

        if (phase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

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
