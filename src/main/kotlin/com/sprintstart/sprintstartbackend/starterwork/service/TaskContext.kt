package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.OrientationPacketResponse
import java.util.UUID

/** What serving orientation knows about the hire's current task, read once per request. */
internal data class TaskContext(
    val proposalId: UUID,
    val projectId: UUID,
    val title: String,
    val body: String,
    val labels: List<String>,
    val taskUrl: String?,
    val cachedFingerprint: String?,
    val cachedPacket: OrientationPacketResponse?,
) {
    fun respond(packet: OrientationPacketResponse?, reason: String? = null) =
        MyOrientationResponse(
            taskId = proposalId,
            taskTitle = title,
            taskUrl = taskUrl,
            packet = packet,
            reason = reason,
        )

    /**
     * The cached packet when there is one, otherwise the honest empty state with [reason]. The
     * reason is dropped when a packet is served, so a served packet never carries an apology.
     */
    fun cachedOrEmpty(reason: String?) = respond(cachedPacket, reason.takeIf { cachedPacket == null })
}
