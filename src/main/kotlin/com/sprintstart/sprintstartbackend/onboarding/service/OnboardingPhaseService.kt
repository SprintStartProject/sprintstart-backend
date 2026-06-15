package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetAllResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.UpdateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.forEach

/**
 * Manages onboarding phases within a user's onboarding path.
 *
 * Phases are ordered siblings under a path. Create, update, and delete operations
 * keep positions contiguous by shifting neighboring phases when needed.
 */
@Suppress("TooManyFunctions")
@Service
class OnboardingPhaseService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val onboardingPhaseRepository: OnboardingPhaseRepository,
    private val userApi: UserApi,
) {
//  ========================== Methods for users ==========================

    /**
     * Returns all phases for the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @return Ordered phases for the current user.
     * @throws ResponseStatusException When the user or onboarding path does not exist.
     */
    @Transactional(readOnly = true)
    fun getOnboardingPhasesForMe(authId: String): List<GetOnboardingPhasesResponse> {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val path = onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding path not found") }

        return onboardingPhaseRepository
            .findAllByPathId(path.id)
            .map { it.toGetAllResponse() }
    }

    /**
     * Returns one phase from the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param phaseId Identifier of the phase to load.
     * @return The requested phase.
     * @throws ResponseStatusException When the user or phase does not exist.
     */
    @Transactional(readOnly = true)
    fun getOnboardingPhaseForMe(authId: String, phaseId: UUID): GetOnboardingPhaseResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        return onboardingPhaseRepository
            .findByIdAndPathUserId(phaseId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding phase not found") }
            .toGetResponse()
    }

    /**
     * Creates a phase in the authenticated user's onboarding path.
     *
     * Existing phases at or after the requested position are shifted right to make room.
     *
     * @param authId External authentication identifier.
     * @param request Phase creation payload.
     * @return The created phase.
     * @throws ResponseStatusException When the user, path, or requested position is invalid.
     */
    @Transactional
    fun createOnboardingPhaseForMe(
        authId: String,
        request: CreateOnboardingPhaseRequest,
    ): CreateOnboardingPhaseResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val path = onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding path not found") }

        // shift all the phases after the new one to make space
        shiftPhasesRight(path, request)

        val onboardingPhase = OnboardingPhase(
            path = path,
            position = request.position,
            title = request.title,
            description = request.description,
        )

        return onboardingPhaseRepository.save(onboardingPhase).toCreateResponse()
    }

    /**
     * Updates a phase in the authenticated user's onboarding path.
     *
     * If the position changes, sibling phases between the old and new position are shifted
     * to preserve contiguous ordering.
     *
     * @param authId External authentication identifier.
     * @param phaseId Identifier of the phase to update.
     * @param request Phase update payload.
     * @return The updated phase.
     * @throws ResponseStatusException When the user, phase, or requested position is invalid.
     */
    @Transactional
    fun updateOnboardingPhaseForMe(
        authId: String,
        phaseId: UUID,
        request: UpdateOnboardingPhaseRequest,
    ): UpdateOnboardingPhaseResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding user not found") }

        val phase = onboardingPhaseRepository
            .findByIdAndPathUserId(phaseId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Onboarding phase not found") }

        shiftPhasesBetween(phase, request)

        phase.position = request.position
        phase.title = request.title
        phase.description = request.description

        return phase.toUpdateResponse()
    }

    /**
     * Deletes a phase from the authenticated user's onboarding path.
     *
     * Phases positioned after the deleted phase are shifted left by one.
     *
     * @param authId External authentication identifier.
     * @param phaseId Identifier of the phase to delete.
     * @throws ResponseStatusException When the user or phase does not exist.
     */
    @Transactional
    fun deleteOnboardingPhaseForMe(
        authId: String,
        phaseId: UUID,
    ) {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val phase = onboardingPhaseRepository
            .findByIdAndPathUserId(phaseId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        // Shift left
        val phasesToShift = onboardingPhaseRepository
            .findAllByPathIdAndPositionGreaterThan(phase.path.id, phase.position)
        phasesToShift.forEach { it.position -= 1 }
        if (phasesToShift.isNotEmpty()) onboardingPhaseRepository.saveAll(phasesToShift)

        onboardingPhaseRepository.delete(phase)
    }

//  ========================== Methods for admins ==========================

    /**
     * Returns all phases for the given user.
     *
     * @param userId Identifier of the user whose phases should be loaded.
     * @return Ordered phases for the user's path.
     */
    @Transactional(readOnly = true)
    fun getOnboardingPhasesForUser(userId: UUID): List<GetOnboardingPhasesResponse> {
        return onboardingPhaseRepository.findAllByPathUserId(userId).map { it.toGetAllResponse() }
    }

    /**
     * Creates a phase in the specified user's onboarding path.
     *
     * Existing phases at or after the requested position are shifted right to make room.
     *
     * @param userId Identifier of the path owner.
     * @param request Phase creation payload.
     * @return The created phase.
     * @throws ResponseStatusException When the path or requested position does not exist.
     */
    @Transactional
    fun createOnboardingPhaseForUserId(
        userId: UUID,
        request: CreateOnboardingPhaseRequest,
    ): CreateOnboardingPhaseResponse {
        val path = onboardingPathRepository
            .findByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No path found for user $userId") }

        // Shift all phases at or after the new position up by one
        shiftPhasesRight(path, request)

        val onboardingPhase = OnboardingPhase(
            path = path,
            position = request.position,
            title = request.title,
            description = request.description,
        )

        return onboardingPhaseRepository.save(onboardingPhase).toCreateResponse()
    }

    /**
     * Returns one phase by ID.
     *
     * @param phaseId Identifier of the phase to load.
     * @return The requested phase.
     * @throws ResponseStatusException When the phase does not exist.
     */
    @Transactional(readOnly = true)
    fun getOnboardingPhaseById(phaseId: UUID): GetOnboardingPhaseResponse {
        return onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found") }
            .toGetResponse()
    }

    /**
     * Updates a phase by ID.
     *
     * If the position changes, sibling phases between the old and new position are shifted
     * to preserve contiguous ordering.
     *
     * @param phaseId Identifier of the phase to update.
     * @param request Phase update payload.
     * @return The updated phase.
     * @throws ResponseStatusException When the phase does not exist or the position is invalid.
     */
    @Transactional
    fun updateOnboardingPhaseById(
        phaseId: UUID,
        request: UpdateOnboardingPhaseRequest,
    ): UpdateOnboardingPhaseResponse {
        val phase = onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        shiftPhasesBetween(phase, request)

        phase.position = request.position
        phase.title = request.title
        phase.description = request.description

        return phase.toUpdateResponse()
    }

    /**
     * Deletes an onboarding phase and shifts subsequent sibling phases one position back.
     *
     * @param phaseId The ID of the phase to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no phase exists with [phaseId].
     */
    @Transactional
    fun deleteOnboardingPhaseById(
        phaseId: UUID,
    ) {
        val phase = onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        // Shift left
        val phasesToShift = onboardingPhaseRepository
            .findAllByPathIdAndPositionGreaterThan(phase.path.id, phase.position)
        phasesToShift.forEach { it.position -= 1 }
        if (phasesToShift.isNotEmpty()) onboardingPhaseRepository.saveAll(phasesToShift)

        onboardingPhaseRepository.delete(phase)
    }

//  ========================== Helper Methods ==========================

    /**
     * Makes room for a new phase at the requested position by shifting all existing
     * phases at that position or after the position one position to the right.
     *
     * Valid positions are from `0` to `phaseCount`, where `phaseCount` means
     * appending the new phase at the end.
     *
     * @throws ResponseStatusException with `400 BAD_REQUEST` if the requested
     * position is outside the valid range.
     */
    private fun shiftPhasesRight(
        path: OnboardingPath,
        request: CreateOnboardingPhaseRequest,
    ) {
        val phaseCount = onboardingPhaseRepository.countByPathId(path.id)

        if (request.position !in 0..phaseCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $phaseCount",
            )
        }

        val phasesToShift = onboardingPhaseRepository
            .findByPathIdAndPositionGreaterThanEqualOrderByPositionDesc(
                path.id,
                request.position,
            )

        phasesToShift.forEach { it.position += 1 }
    }

    /**
     * Reorders phases after an existing phase is moved to a new position.
     *
     * When the phase moves down, all phases between the old and new position are
     * shifted one position to the left. When the phase moves up, all phases between
     * the new and old position are shifted one position to the right.
     *
     * Valid positions are from `0` to `phaseCount - 1`, because the phase already
     * exists and can only be moved within the current list.
     *
     * @throws ResponseStatusException with `400 BAD_REQUEST` if the requested
     * position is outside the valid range.
     */
    private fun shiftPhasesBetween(
        phase: OnboardingPhase,
        request: UpdateOnboardingPhaseRequest,
    ) {
        val phaseCount = onboardingPhaseRepository.countByPathId(phase.path.id)

        if (request.position !in 0 until phaseCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${phaseCount - 1}")
        }

        val oldPosition = phase.position
        val newPosition = request.position

        if (oldPosition < newPosition) {
            val phasesToShift = onboardingPhaseRepository
                .findByPathIdAndPositionBetween(
                    phase.path.id,
                    oldPosition + 1,
                    newPosition,
                )

            phasesToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            val phasesToShift = onboardingPhaseRepository
                .findByPathIdAndPositionBetween(
                    phase.path.id,
                    newPosition,
                    oldPosition - 1,
                )

            phasesToShift.forEach { it.position += 1 }
        }
    }
}
