package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.buddy.external.TeamModeConversation
import com.sprintstart.sprintstartbackend.buddy.model.request.buddy.SendBuddyMessageRequest
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Component

/** Team mode behind the buddy's endpoints: [BuddyTeamService], in the shape [TeamModeConversation] asks for. */
@Component
class BuddyTeamConversation(
    private val buddyTeamService: BuddyTeamService,
) : TeamModeConversation {
    override fun messages(authId: AuthId, project: ProjectId): List<BuddyMessageResponse> =
        buddyTeamService.getMessagesForMe(authId.value, project.value)

    override fun open(authId: AuthId, project: ProjectId): Flow<BuddyStreamEvent> =
        buddyTeamService.streamOpenForMe(authId.value, project.value)

    override fun send(
        authId: AuthId,
        project: ProjectId,
        message: SendBuddyMessageRequest,
    ): Flow<BuddyStreamEvent> =
        buddyTeamService.sendMessageForMe(
            authId.value,
            project.value,
            message.content,
            message.capabilitiesEnabled,
            message.filters,
            message.currentPage,
        )
}
