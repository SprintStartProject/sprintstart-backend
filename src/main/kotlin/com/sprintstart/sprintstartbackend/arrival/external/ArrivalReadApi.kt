package com.sprintstart.sprintstartbackend.arrival.external

import com.sprintstart.sprintstartbackend.arrival.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId

/**
 * A hire's arrival steps and how far they got, as the board and the assistants show them: in the shape
 * the arrival pages serve, each with whether and how it was settled.
 */
interface ArrivalReadApi {
    /** The hire's steps across every project they are on, company-wide ones included. */
    fun forHire(userId: UserId): List<ArrivalStepResponse>

    /** The hire's steps on [projectId] alone, company-wide ones included. */
    fun forHireOn(userId: UserId, projectId: ProjectId): List<ArrivalStepResponse>
}
