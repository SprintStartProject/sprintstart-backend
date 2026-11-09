package com.sprintstart.sprintstartbackend.starterwork.external

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.starterwork.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.OrientationPacketResponse

/** A task's orientation packet as a manager writes it from team mode. */
interface OrientationAuthoringApi {
    /** The packet to author [taskId]'s orientation from: the cached one, or a shell. Never calls the AI. */
    fun packetForAuthoring(taskId: StarterWorkTaskId, projectId: ProjectId): MyOrientationResponse

    /** Pins a human-written packet for [taskId], replacing whatever was there. */
    fun authorPacket(
        taskId: StarterWorkTaskId,
        projectId: ProjectId,
        request: AuthorOrientationRequest,
    ): OrientationPacketResponse

    /** Drops [taskId]'s packet so the next hire read assembles it again; a no-op when there is none. */
    fun revertToAi(taskId: StarterWorkTaskId, projectId: ProjectId)
}
